package com.cleanweather.app.util;

import java.util.Locale;

/** 数值与风向格式化 */
public final class FormatUtil {

    private static final String[] WIND_DIRS = {
            "北", "东北偏北", "东北", "东北偏东", "东", "东南偏东", "东南", "东南偏南",
            "南", "西南偏南", "西南", "西南偏西", "西", "西北偏西", "西北", "西北偏北"
    };

    public static String windDirName(int degrees) {
        if (degrees < 0) return "";
        int idx = ((degrees + 11) / 23) % 16;
        return WIND_DIRS[idx];
    }

    /** 温度取整显示 */
    public static String temp(double t) {
        long r = Math.round(t);
        return String.valueOf(r);
    }

    public static String tempWithUnit(double t) {
        return Math.round(t) + "°";
    }

    public static String speed(double v) {
        long r = Math.round(v);
        return r + " 公里/时";
    }

    public static String percent(double v) {
        long r = Math.round(v);
        return r + "%";
    }

    public static String km(double v) {
        if (v >= 10) return String.format(Locale.getDefault(), "%.0f", v);
        return String.format(Locale.getDefault(), "%.1f", v);
    }

    public static String mm(double v) {
        return String.format(Locale.getDefault(), "%.1f", v);
    }

    public static String uvLevel(double uv) {
        if (uv < 3) return "低";
        if (uv < 6) return "中等";
        if (uv < 8) return "高";
        if (uv < 11) return "很高";
        return "极高";
    }

    /** AQI 分级颜色 (ARGB) */
    public static int aqiColor(double aqi) {
        if (aqi <= 50) return 0xFF4CAF50;
        if (aqi <= 100) return 0xFFFFC107;
        if (aqi <= 150) return 0xFFFF9800;
        if (aqi <= 200) return 0xFFF44336;
        if (aqi <= 300) return 0xFF9C27B0;
        return 0xFF7D26CD;
    }

    public static String aqiLevel(double aqi) {
        if (aqi <= 50) return "优";
        if (aqi <= 100) return "良";
        if (aqi <= 150) return "轻度污染";
        if (aqi <= 200) return "中度污染";
        if (aqi <= 300) return "重度污染";
        return "严重污染";
    }
}
