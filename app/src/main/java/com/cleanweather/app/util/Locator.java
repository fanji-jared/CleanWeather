package com.cleanweather.app.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * 定位工具：使用系统 LocationManager（GPS/网络/被动），不依赖 GMS，
 * 兼容国内无 Google 服务的设备。
 *
 * 注意（重要）：定位监听器绑定在主线程 Looper 上，因此系统回调发生在主线程。
 * 为避免调用方在主线程里做网络请求（会触发 NetworkOnMainThreadException 闪退），
 * 本类会把所有回调统一投递到后台线程执行。调用方可以放心在回调里做网络 IO。
 */
public final class Locator {

    public interface Callback {
        void onLocation(double lat, double lon);

        void onFailed(String reason);
    }

    /** 回调统一在后台线程执行，保证调用方不会在主线程做网络请求 */
    private static final ExecutorService CALLBACK_EXEC =
            Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "locator-callback");
                    t.setDaemon(true);
                    return t;
                }
            });

    private static final Object LOCK = new Object();
    private static LocationManager activeLm;
    private static LocationListener activeListener;

    private Locator() {
    }

    /**
     * 取消当前正在进行的定位请求。
     * 在页面销毁、切换城市或发起新的定位前调用，避免监听器堆积导致主线程被回调刷爆（卡死）。
     */
    public static void cancelActive() {
        synchronized (LOCK) {
            if (activeLm != null && activeListener != null) {
                try {
                    activeLm.removeUpdates(activeListener);
                } catch (Exception ignore) {
                }
            }
            activeLm = null;
            activeListener = null;
        }
    }

    /** 最后已知位置（快速） */
    public static double[] lastKnown(Context context) {
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return null;
            List<String> providers = lm.getProviders(true);
            Location best = null;
            for (String p : providers) {
                try {
                    Location l = lm.getLastKnownLocation(p);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                } catch (SecurityException ignore) {
                } catch (Exception ignore) {
                }
            }
            if (best != null && isFresh(best)) {
                return new double[]{best.getLatitude(), best.getLongitude()};
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isFresh(Location l) {
        long age = SystemClock.elapsedRealtimeNanos() - l.getElapsedRealtimeNanos();
        return age < 10L * 60L * 1000L * 1000L * 1000L; // 10 分钟内
    }

    /**
     * 请求一次新鲜定位，最长等待 timeoutMs。
     * 回调 {@link Callback} 一定在后台线程执行。
     */
    @SuppressLint("MissingPermission")
    public static void requestOnce(final Context context, long timeoutMs, final Callback cb) {
        // 先取消上一次请求，防止监听器重复注册
        cancelActive();

        final LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            dispatchFailed(cb, "设备不支持定位");
            return;
        }

        final Object doneLock = new Object();
        final boolean[] done = {false};

        final LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                synchronized (doneLock) {
                    if (done[0]) return;
                    done[0] = true;
                }
                cancelActive();
                final double lat = location.getLatitude();
                final double lon = location.getLongitude();
                CALLBACK_EXEC.execute(new Runnable() {
                    @Override
                    public void run() {
                        cb.onLocation(lat, lon);
                    }
                });
            }

            @Override
            public void onProviderDisabled(String provider) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }
        };

        synchronized (LOCK) {
            activeLm = lm;
            activeListener = listener;
        }

        try {
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_COARSE);
            String provider = null;
            try {
                provider = lm.getBestProvider(criteria, true);
            } catch (Exception ignore) {
            }

            boolean registered = false;
            if (provider != null) {
                try {
                    // minTime=1000ms 降低回调频率，避免主线程被高频位置更新刷爆
                    lm.requestLocationUpdates(provider, 1000L, 0f, listener, Looper.getMainLooper());
                    registered = true;
                } catch (Exception ignore) {
                }
            }
            if (!registered) {
                for (String p : lm.getAllProviders()) {
                    try {
                        lm.requestLocationUpdates(p, 1000L, 0f, listener, Looper.getMainLooper());
                        registered = true;
                    } catch (Exception ignore) {
                    }
                }
            }
            if (!registered) {
                cancelActive();
                dispatchFailed(cb, "没有可用的定位方式");
                return;
            }
        } catch (SecurityException e) {
            cancelActive();
            dispatchFailed(cb, "缺少定位权限");
            return;
        } catch (Exception e) {
            cancelActive();
            dispatchFailed(cb, "定位不可用");
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(timeoutMs);
                } catch (InterruptedException ignore) {
                }
                synchronized (doneLock) {
                    if (done[0]) return;
                    done[0] = true;
                }
                cancelActive();
                double[] last = lastKnown(context);
                if (last != null) {
                    final double lat = last[0];
                    final double lon = last[1];
                    CALLBACK_EXEC.execute(new Runnable() {
                        @Override
                        public void run() {
                            cb.onLocation(lat, lon);
                        }
                    });
                } else {
                    dispatchFailed(cb, "定位超时");
                }
            }
        }, "locator-timeout").start();
    }

    private static void dispatchFailed(final Callback cb, final String reason) {
        CALLBACK_EXEC.execute(new Runnable() {
            @Override
            public void run() {
                cb.onFailed(reason);
            }
        });
    }
}
