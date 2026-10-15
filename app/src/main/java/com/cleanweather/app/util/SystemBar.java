package com.cleanweather.app.util;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.Window;

/**
 * 系统栏配色：让状态栏与页面顶部颜色一致、导航栏与页面底部颜色一致，
 * 并根据背景明暗自动切换图标深浅（状态栏 / 导航栏分别判断），保证可读性。
 */
public final class SystemBar {

    private SystemBar() {
    }

    /** 主页：渐变背景——状态栏取顶部色，导航栏取底部色 */
    public static void applyGradient(Activity a, int topColor, int bottomColor) {
        apply(a, topColor, bottomColor);
    }

    /**
     * 子页面：顶部为固定色（顶栏/深色背景），底部为另一色。
     * @param headerColor 页面顶部色（状态栏跟随）
     * @param bottomColor 页面底部色（导航栏跟随）
     */
    public static void applyHeader(Activity a, int headerColor, int bottomColor) {
        apply(a, headerColor, bottomColor);
    }

    private static void apply(Activity a, int topColor, int bottomColor) {
        Window w = a.getWindow();
        if (Build.VERSION.SDK_INT < 21) return;

        w.setStatusBarColor(topColor);
        w.setNavigationBarColor(bottomColor);

        View decor = w.getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (Build.VERSION.SDK_INT >= 23) {
            if (isLight(topColor)) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            if (isLight(bottomColor)) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);
    }

    /** 背景是否为浅色（决定系统栏图标用深色还是白色） */
    private static boolean isLight(int color) {
        double r = (color >> 16) & 0xFF;
        double g = (color >> 8) & 0xFF;
        double b = color & 0xFF;
        return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0 > 0.6;
    }
}
