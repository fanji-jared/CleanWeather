package com.cleanweather.app.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 时间解析/格式化工具。
 * API 时间格式:
 *  - ISO8601 带时区: 2026-10-15T04:00:00+08:00
 *  - .NET ticks (100ns since 0001-01-01): 639275904000000000
 */
public final class TimeUtil {

    /** 解析 ISO8601 时间串为 Unix 毫秒 */
    public static long parseIso(String iso) {
        if (iso == null || iso.isEmpty()) return 0;
        try {
            String s = iso;
            // 把 +08:00 转成 +0800，SimpleDateFormat 的 Z 只识别无冒号形式
            int plus = s.lastIndexOf('+');
            if (plus > 0 && s.length() >= plus + 6 && s.charAt(plus + 3) == ':') {
                s = s.substring(0, plus + 3) + s.substring(plus + 4);
            } else if (s.endsWith("Z")) {
                s = s.substring(0, s.length() - 1) + "+0000";
            }
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
            Date d = df.parse(s);
            return d == null ? 0 : d.getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    /** .NET ticks -> Unix 毫秒 */
    public static long ticksToMillis(long ticks) {
        return ticks / 10000L - 62135596800000L;
    }

    public static String formatTime(long millis) {
        if (millis <= 0) return "--";
        SimpleDateFormat df = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return df.format(new Date(millis));
    }

    public static String formatShortTime(long millis) {
        if (millis <= 0) return "--";
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        int h = cal.get(Calendar.HOUR_OF_DAY);
        int m = cal.get(Calendar.MINUTE);
        if (m == 0) return String.format(Locale.getDefault(), "%d点", h);
        return String.format(Locale.getDefault(), "%d:%02d", h, m);
    }

    public static String formatDate(long millis) {
        if (millis <= 0) return "";
        SimpleDateFormat df = new SimpleDateFormat("M月d日", Locale.getDefault());
        return df.format(new Date(millis));
    }

    public static String formatDateFull(long millis) {
        if (millis <= 0) return "";
        SimpleDateFormat df = new SimpleDateFormat("yyyy年M月d日", Locale.getDefault());
        return df.format(new Date(millis));
    }

    public static String formatWeekday(long millis) {
        if (millis <= 0) return "";
        String[] weeks = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        return weeks[cal.get(Calendar.DAY_OF_WEEK) - 1];
    }

    /** 今天 00:00 的毫秒值 */
    public static long todayStart() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    public static String formatUpdateAt(long millis) {
        if (millis <= 0) return "";
        SimpleDateFormat df = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return df.format(new Date(millis));
    }
}
