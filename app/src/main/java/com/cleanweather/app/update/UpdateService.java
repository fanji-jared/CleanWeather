package com.cleanweather.app.update;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 后台更新下载服务：
 *  - 前台服务 + 通知栏展示下载进度；
 *  - 下载完成后提示安装（点击通知即可调起系统安装程序，并尝试直接拉起）；
 *  - 下载失败则在通知栏提示并删除已下载的部分文件。
 */
public class UpdateService extends Service {

    private static final String TAG = "UpdateService";
    private static final String CHANNEL_ID = "update";
    private static final int NOTIF_ID = 2001;

    public static final String ACTION_DOWNLOAD = "com.cleanweather.app.action.DOWNLOAD_UPDATE";
    public static final String EXTRA_APK_URL = "apk_url";
    public static final String EXTRA_VERSION = "version";

    private volatile boolean running;

    /** 更新包存放目录（应用私有外部目录，无需存储权限） */
    public static File updateDir(Context ctx) {
        File dir = new File(ctx.getExternalFilesDir(null), "update");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** 清理历史更新包（安装成功后再次打开应用时调用） */
    public static void cleanup(Context ctx) {
        try {
            File[] files = updateDir(ctx).listFiles();
            if (files == null) return;
            for (File f : files) {
                if (f.isFile() && f.getName().toLowerCase().endsWith(".apk")) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        } catch (Exception ignore) {
        }
    }

    /** 调起系统安装程序 */
    public static Intent installIntent(Context ctx, File apk) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(UpdateFileProvider.uriFor(apk), "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_DOWNLOAD.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (running) return START_NOT_STICKY;
        running = true;

        final String apkUrl = intent.getStringExtra(EXTRA_APK_URL);
        final String version = intent.getStringExtra(EXTRA_VERSION);
        if (apkUrl == null || apkUrl.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        ensureChannel();
        startForegroundCompat(buildProgressNotification(0, null).build());

        new Thread(new Runnable() {
            @Override
            public void run() {
                download(apkUrl, version == null ? "" : version);
            }
        }, "update-download").start();
        return START_NOT_STICKY;
    }

    private void download(String apkUrl, String version) {
        File dir = updateDir(this);
        File target = new File(dir, "CleanWeather-" + (version.isEmpty() ? "latest" : version) + ".apk");
        HttpURLConnection conn = null;
        InputStream in = null;
        FileOutputStream out = null;
        try {
            URL url = new URL(apkUrl);
            for (int redirect = 0; redirect < 6; redirect++) {
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(20000);
                conn.setRequestProperty("User-Agent", "CleanWeather-Updater");
                conn.setInstanceFollowRedirects(false);
                int code = conn.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String loc = conn.getHeaderField("Location");
                    if (loc == null) throw new Exception("重定向地址缺失");
                    conn.disconnect();
                    url = new URL(url, loc);
                    conn = null;
                    continue;
                }
                if (code >= 400) throw new Exception("HTTP " + code);
                break;
            }
            if (conn == null) throw new Exception("重定向次数过多");

            long total = conn.getContentLengthLong();
            in = conn.getInputStream();
            out = new FileOutputStream(target);
            byte[] buf = new byte[8192];
            long downloaded = 0;
            int lastPct = -1;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                downloaded += n;
                if (total > 0) {
                    int pct = (int) (downloaded * 100 / total);
                    if (pct >= lastPct + 2 || pct == 100) {
                        lastPct = pct;
                        updateProgress(pct, downloaded, total);
                    }
                }
            }
            out.flush();

            if (total > 0 && downloaded < total) throw new Exception("下载不完整");

            Log.i(TAG, "下载完成: " + target.getAbsolutePath());
            onDownloaded(target, version);
        } catch (Exception e) {
            Log.e(TAG, "更新包下载失败", e);
            if (target.exists()) {
                //noinspection ResultOfMethodCallIgnored
                target.delete();
            }
            onFailed();
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignore) {
            }
            try {
                if (out != null) out.close();
            } catch (Exception ignore) {
            }
            if (conn != null) conn.disconnect();
        }
    }

    private void onDownloaded(File apk, String version) {
        // 点击通知 → 安装
        Notification.Builder b = baseBuilder()
                .setContentTitle("更新已下载完成")
                .setContentText("点击安装纯净天气 " + (version.isEmpty() ? "" : "v" + version))
                .setOngoing(false)
                .setAutoCancel(true)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(installPendingIntent(apk));
        notify(NOTIF_ID, b.build());

        // 尝试直接拉起安装程序（前台时可用；后台受限则依赖通知点击）
        try {
            startActivity(installIntent(this, apk));
        } catch (Exception ignore) {
        }
        stopForegroundCompat();
        running = false;
        stopSelf();
    }

    private void onFailed() {
        Notification.Builder b = baseBuilder()
                .setContentTitle("更新下载失败")
                .setContentText("请检查网络后重试")
                .setOngoing(false)
                .setAutoCancel(true)
                .setSmallIcon(android.R.drawable.stat_notify_error);
        notify(NOTIF_ID, b.build());
        stopForegroundCompat();
        running = false;
        stopSelf();
    }

    private PendingIntent installPendingIntent(File apk) {
        Intent intent = installIntent(this, apk);
        PendingIntent pi = PendingIntent.getActivity(this, 2002, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return pi;
    }

    private void updateProgress(int pct, long downloaded, long total) {
        String text = total > 0
                ? String.format(java.util.Locale.US, "%d%%  ·  %.1f/%.1f MB",
                pct, downloaded / 1048576.0, total / 1048576.0)
                : pct + "%";
        Notification.Builder b = baseBuilder()
                .setContentTitle("正在下载更新")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, pct, total <= 0)
                .setSmallIcon(android.R.drawable.stat_sys_download);
        notify(NOTIF_ID, b.build());
    }

    private Notification.Builder buildProgressNotification(int pct, String text) {
        return baseBuilder()
                .setContentTitle("正在下载更新")
                .setContentText(text == null ? "准备中…" : text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, pct, true)
                .setSmallIcon(android.R.drawable.stat_sys_download);
    }

    @SuppressWarnings("deprecation")
    private Notification.Builder baseBuilder() {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        return b;
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "应用更新",
                        NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("应用更新下载进度");
                nm.createNotificationChannel(ch);
            }
        }
    }

    private void notify(int id, Notification n) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(id, n);
    }

    private void startForegroundCompat(Notification n) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_DETACH);
        } else {
            stopForeground(false);
        }
    }
}
