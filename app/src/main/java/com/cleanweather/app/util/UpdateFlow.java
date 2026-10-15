package com.cleanweather.app.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import com.cleanweather.app.R;
import com.cleanweather.app.update.UpdateService;

/** 统一的版本检查 / 更新提示 / 后台下载入口 */
public final class UpdateFlow {

    public static final int REQ_INSTALL_UNKNOWN = 301;

    private UpdateFlow() {
    }

    /** 检查更新并（如有）弹出更新提示框。manual=true 时会给出"已是最新/检查失败"的反馈。 */
    public static void checkAndPrompt(final Activity activity, final boolean manual) {
        if (manual) Toast.makeText(activity, R.string.update_checking, Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String local = UpdateChecker.localVersion(activity);
                final UpdateChecker.UpdateInfo info = UpdateChecker.check();
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        if (info == null) {
                            if (manual) {
                                Toast.makeText(activity, R.string.update_check_failed, Toast.LENGTH_LONG).show();
                            }
                            return;
                        }
                        if (!UpdateChecker.isNewer(info.version, local)) {
                            if (manual) {
                                Toast.makeText(activity,
                                        activity.getString(R.string.update_is_latest, local),
                                        Toast.LENGTH_LONG).show();
                            }
                            return;
                        }
                        showUpdateDialog(activity, info);
                    }
                });
            }
        }, "update-check").start();
    }

    private static void showUpdateDialog(final Activity activity, final UpdateChecker.UpdateInfo info) {
        String notes = info.notes == null ? "" : info.notes.trim();
        if (notes.isEmpty()) notes = activity.getString(R.string.update_no_notes);
        // 合理控制字数：过长则截断，避免弹窗过高
        if (notes.length() > 600) notes = notes.substring(0, 600) + "…";

        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.update_found_title, info.version))
                .setMessage(notes)
                .setPositiveButton(R.string.update_download, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startDownload(activity, info);
                    }
                })
                .setNegativeButton(R.string.update_later, null)
                .show();
    }

    /** 开始后台下载（通知栏显示进度） */
    public static void startDownload(Context ctx, UpdateChecker.UpdateInfo info) {
        if (info == null || info.apkUrl == null || info.apkUrl.isEmpty()) return;
        ensureInstallPermission(ctx);
        try {
            Intent it = new Intent(ctx, UpdateService.class)
                    .setAction(UpdateService.ACTION_DOWNLOAD)
                    .putExtra(UpdateService.EXTRA_APK_URL, info.apkUrl)
                    .putExtra(UpdateService.EXTRA_VERSION, info.version);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(it);
            else ctx.startService(it);
            Toast.makeText(ctx, R.string.update_downloading, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(ctx, R.string.update_download_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /** 是否已允许安装未知来源应用 */
    public static boolean canInstall(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                return ctx.getPackageManager().canRequestPackageInstalls();
            } catch (Exception e) {
                return true;
            }
        }
        return true;
    }

    /** 引导用户去打开"允许安装未知应用"开关（安装时系统也会再校验） */
    public static void ensureInstallPermission(Context ctx) {
        if (canInstall(ctx)) return;
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + ctx.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception ignore) {
        }
    }
}
