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

/**
 * 定位工具：使用系统 LocationManager（GPS/网络/被动），不依赖 GMS，
 * 兼容国内无 Google 服务的设备。
 */
public class Locator {

    public interface Callback {
        void onLocation(double lat, double lon);

        void onFailed(String reason);
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
     * 在后台线程调用。
     */
    @SuppressLint("MissingPermission")
    public static void requestOnce(final Context context, long timeoutMs, final Callback cb) {
        final LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            cb.onFailed("设备不支持定位");
            return;
        }
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_COARSE);
        String provider = lm.getBestProvider(criteria, true);
        final Object lock = new Object();
        final boolean[] done = {false};
        final LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                synchronized (lock) {
                    if (done[0]) return;
                    done[0] = true;
                    try {
                        lm.removeUpdates(this);
                    } catch (SecurityException ignore) {
                    }
                    cb.onLocation(location.getLatitude(), location.getLongitude());
                }
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

        try {
            if (provider != null) {
                lm.requestLocationUpdates(provider, 0, 0, listener, Looper.getMainLooper());
            } else {
                // 没有可用 provider 时直接尝试所有
                boolean any = false;
                for (String p : lm.getAllProviders()) {
                    try {
                        lm.requestLocationUpdates(p, 0, 0, listener, Looper.getMainLooper());
                        any = true;
                    } catch (Exception ignore) {
                    }
                }
                if (!any) {
                    cb.onFailed("没有可用的定位方式");
                    return;
                }
            }
        } catch (SecurityException e) {
            cb.onFailed("缺少定位权限");
            return;
        } catch (IllegalArgumentException e) {
            cb.onFailed("没有可用的定位方式");
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(timeoutMs);
                } catch (InterruptedException ignore) {
                }
                synchronized (lock) {
                    if (!done[0]) {
                        done[0] = true;
                        try {
                            lm.removeUpdates(listener);
                        } catch (SecurityException ignore) {
                        }
                        double[] last = lastKnown(context);
                        if (last != null) {
                            cb.onLocation(last[0], last[1]);
                        } else {
                            cb.onFailed("定位超时");
                        }
                    }
                }
            }
        }).start();
    }
}
