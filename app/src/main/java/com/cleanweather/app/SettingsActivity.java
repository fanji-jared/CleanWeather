package com.cleanweather.app;

import com.cleanweather.app.data.CityStore;
import com.cleanweather.app.util.SystemBar;
import com.cleanweather.app.util.UpdateChecker;
import com.cleanweather.app.util.UpdateFlow;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 设置页：自动检查升级、通知/精准定位权限、手动检查升级、版本、分享、作者、GitHub。
 */
public class SettingsActivity extends Activity {

    private static final int REQ_NOTIF = 301;
    private static final int REQ_LOCATION = 302;

    private static final int COLOR_TITLE = 0xFF222222;
    private static final int COLOR_SUB = 0xFF9AA4B2;
    private static final int COLOR_ICON = 0xFF5B6B7C;
    private static final int HEADER_COLOR = 0xFF1B2A4A;

    private CityStore store;
    private LinearLayout container;
    private TextView notifSubtitle, locSubtitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        store = new CityStore(this);
        container = findViewById(R.id.settingsContainer);

        SystemBar.applyHeader(this, HEADER_COLOR, 0xFFF5F6F8);
        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        buildGeneral();
        buildPermissions();
        buildUpdateSection();
        buildAbout();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从系统设置返回后，刷新权限状态文案
        refreshPermissionState();
    }

    // ================= 分区构建 =================

    private void buildGeneral() {
        addSectionLabel(getString(R.string.settings_general));
        LinearLayout section = addSection();

        addSwitchRow(section, R.drawable.ic_update,
                getString(R.string.auto_check_update),
                getString(R.string.auto_check_update_desc),
                store.isAutoCheckUpdate(),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        store.setAutoCheckUpdate(isChecked);
                    }
                });
    }

    private void buildPermissions() {
        addSectionLabel(getString(R.string.settings_permissions));
        LinearLayout section = addSection();

        notifSubtitle = addActionRow(section, R.drawable.ic_notifications,
                getString(R.string.permission_notification), "", false,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        requestNotificationPermission();
                    }
                });
        addDivider(section);

        locSubtitle = addActionRow(section, R.drawable.ic_locate,
                getString(R.string.permission_location), "", false,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        requestPreciseLocation();
                    }
                });

        refreshPermissionState();
    }

    private void buildUpdateSection() {
        addSectionLabel(getString(R.string.settings_update));
        LinearLayout section = addSection();

        addActionRow(section, R.drawable.ic_refresh,
                getString(R.string.check_update_manual),
                getString(R.string.check_update_manual_desc), true,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        UpdateFlow.checkAndPrompt(SettingsActivity.this, true);
                    }
                });
    }

    private void buildAbout() {
        addSectionLabel(getString(R.string.settings_about));
        LinearLayout section = addSection();

        addActionRow(section, R.drawable.ic_share,
                getString(R.string.share_app),
                getString(R.string.share_app_desc), true,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        shareApp();
                    }
                });
        addDivider(section);

        // 软件名 + 版本号
        addActionRow(section, R.drawable.ic_info,
                getString(R.string.app_version),
                getString(R.string.app_name) + " v" + UpdateChecker.localVersion(this), false, null);
        addDivider(section);

        addActionRow(section, R.drawable.ic_person,
                getString(R.string.about_author),
                getString(R.string.author_name), false, null);
        addDivider(section);

        addActionRow(section, R.drawable.ic_github,
                getString(R.string.github_title),
                UpdateChecker.REPO_PAGE, true,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openGithub();
                    }
                });
    }

    // ================= 行控件 =================

    /** 分区小标题 */
    private void addSectionLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(COLOR_SUB);
        label.setTextSize(12.5f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(6);
        lp.topMargin = dp(16);
        lp.bottomMargin = dp(7);
        container.addView(label, lp);
    }

    /** 白色圆角分区容器 */
    private LinearLayout addSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setBackgroundResource(R.drawable.bg_settings_section);
        container.addView(section, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return section;
    }

    /**
     * 普通行：图标 + 标题 + 说明 + 可选右箭头。返回说明 TextView，便于后续更新文案。
     */
    private TextView addActionRow(LinearLayout section, int iconRes, String title,
                                  String subtitle, boolean chevron, View.OnClickListener click) {
        LinearLayout row = buildRowBase(section, iconRes, title);
        LinearLayout col = (LinearLayout) row.getChildAt(1);

        TextView sub = new TextView(this);
        sub.setText(subtitle);
        sub.setTextColor(COLOR_SUB);
        sub.setTextSize(12);
        sub.setMaxLines(2);
        col.addView(sub);

        if (chevron) {
            ImageView arrow = new ImageView(this);
            arrow.setImageResource(R.drawable.ic_chevron_right);
            arrow.setColorFilter(0xFFC3CAD3);
            row.addView(arrow, new LinearLayout.LayoutParams(dp(16), dp(16)));
        }
        if (click != null) {
            applySelectable(row);
            row.setOnClickListener(click);
        }
        return sub;
    }

    /** 开关行：图标 + 标题 + 说明 + Switch */
    private void addSwitchRow(LinearLayout section, int iconRes, String title, String subtitle,
                              boolean checked, CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = buildRowBase(section, iconRes, title);
        LinearLayout col = (LinearLayout) row.getChildAt(1);

        TextView sub = new TextView(this);
        sub.setText(subtitle);
        sub.setTextColor(COLOR_SUB);
        sub.setTextSize(12);
        sub.setMaxLines(2);
        col.addView(sub);

        Switch sw = new Switch(this);
        sw.setChecked(checked);
        sw.setShowText(false);
        sw.setPadding(0, 0, 0, 0);
        sw.setOnCheckedChangeListener(listener);
        row.addView(sw, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** 行骨架：图标 +（标题列），返回整行（标题列固定是第 2 个 child） */
    private LinearLayout buildRowBase(LinearLayout section, int iconRes, String title) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(58));
        row.setPadding(dp(16), dp(11), dp(14), dp(11));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(COLOR_ICON);
        row.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clp.leftMargin = dp(14);
        clp.rightMargin = dp(8);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(COLOR_TITLE);
        t.setTextSize(15);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        col.addView(t);
        row.addView(col, clp);

        section.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void addDivider(LinearLayout section) {
        View line = new View(this);
        line.setBackgroundColor(0xFFEDF0F3);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin = dp(52);
        section.addView(line, lp);
    }

    private void applySelectable(View v) {
        TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
            v.setBackgroundResource(tv.resourceId);
        }
    }

    // ================= 权限 =================

    private void refreshPermissionState() {
        if (notifSubtitle != null) {
            boolean on = notificationGranted();
            notifSubtitle.setText(on ? R.string.permission_notification_on
                    : R.string.permission_notification_off);
        }
        if (locSubtitle != null) {
            boolean fine = fineLocationGranted();
            locSubtitle.setText(fine ? R.string.permission_location_on
                    : R.string.permission_location_off);
        }
    }

    @SuppressWarnings("deprecation")
    private boolean notificationGranted() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        if (Build.VERSION.SDK_INT >= 24) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            return nm == null || nm.areNotificationsEnabled();
        }
        return true;
    }

    private boolean fineLocationGranted() {
        if (Build.VERSION.SDK_INT < 23) return true;
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (notificationGranted()) {
                openNotificationSettings();
            } else {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            }
        } else if (Build.VERSION.SDK_INT >= 24) {
            if (notificationGranted()) {
                Toast.makeText(this, R.string.permission_notification_on, Toast.LENGTH_SHORT).show();
            } else {
                openNotificationSettings();
            }
        } else {
            Toast.makeText(this, R.string.permission_notification_on, Toast.LENGTH_SHORT).show();
        }
    }

    private void openNotificationSettings() {
        try {
            Intent i;
            if (Build.VERSION.SDK_INT >= 26) {
                i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            } else {
                i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:" + getPackageName()));
            }
            startActivity(i);
        } catch (Exception ignore) {
        }
    }

    private void requestPreciseLocation() {
        if (Build.VERSION.SDK_INT < 23) {
            Toast.makeText(this, R.string.permission_location_on, Toast.LENGTH_SHORT).show();
            return;
        }
        if (fineLocationGranted()) {
            Toast.makeText(this, R.string.permission_location_on, Toast.LENGTH_SHORT).show();
            return;
        }
        requestPermissions(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        }, REQ_LOCATION);
    }

    private void openAppSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception ignore) {
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIF) {
            boolean granted = false;
            for (int g : grantResults) if (g == PackageManager.PERMISSION_GRANTED) granted = true;
            refreshPermissionState();
            if (!granted) openNotificationSettings();
        } else if (requestCode == REQ_LOCATION) {
            boolean granted = false;
            for (int g : grantResults) if (g == PackageManager.PERMISSION_GRANTED) granted = true;
            refreshPermissionState();
            if (!granted) {
                // 被拒绝则引导去系统设置手动开启
                openAppSettings();
            }
        }
    }

    // ================= 分享 / GitHub =================

    private void shareApp() {
        String text = getString(R.string.share_text, UpdateChecker.RELEASES_PAGE);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
        send.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(send, getString(R.string.share_app)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.share_no_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void openGithub() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.REPO_PAGE)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
