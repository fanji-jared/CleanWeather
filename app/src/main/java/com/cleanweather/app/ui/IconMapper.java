package com.cleanweather.app.ui;

import com.cleanweather.app.R;

/** MSN 图标编码 -> 本地矢量图标资源 */
public final class IconMapper {

    /**
     * 图标编码为 AccuWeather 风格 (MSN 使用同一套):
     * 1 晴 / 2-4 多云间晴 / 5 朦胧 / 6-7 多云 / 8 阴 / 11 雾
     * 12-14 阵雨 / 15-17 雷雨 / 18 雨 / 19-21 小雪 / 22-24 雪 / 25-26 冻雨
     * 29 晴夜 / 30 多云夜 / 31-32 阴夜
     */
    public static int iconRes(int icon) {
        switch (icon) {
            case 1: return R.drawable.ic_sunny;
            case 2: case 3: case 4: return R.drawable.ic_partly_sunny;
            case 5: return R.drawable.ic_hazy;
            case 6: case 7: return R.drawable.ic_mostly_cloudy;
            case 8: return R.drawable.ic_cloudy;
            case 11: return R.drawable.ic_fog;
            case 12: case 13: case 14: return R.drawable.ic_showers;
            case 15: case 16: case 17: return R.drawable.ic_tstorm;
            case 18: return R.drawable.ic_rain;
            case 19: case 20: case 21: return R.drawable.ic_flurries;
            case 22: case 23: case 24: return R.drawable.ic_snow;
            case 25: case 26: return R.drawable.ic_sleet;
            case 29: return R.drawable.ic_night_clear;
            case 30: return R.drawable.ic_night_partly;
            case 31: case 32: return R.drawable.ic_night_cloudy;
            default: return R.drawable.ic_cloudy;
        }
    }

    /**
     * 依据图标编码 + 昼夜返回渐变背景色数组 [top, bottom]
     */
    public static int[] gradientFor(int icon, boolean daytime) {
        if (!daytime) {
            switch (icon) {
                case 15: case 16: case 17: case 12: case 18: case 13: case 14:
                    return new int[]{0xFF232B3B, 0xFF4A5568};
                case 22: case 23: case 24: case 25: case 26: case 19: case 20: case 21:
                    return new int[]{0xFF2A3550, 0xFF556580};
                default:
                    return new int[]{0xFF1B2A4A, 0xFF3D5A80};
            }
        }
        switch (icon) {
            case 15: case 16: case 17:
                return new int[]{0xFF3E4E60, 0xFF647A92}; // 雷雨
            case 12: case 13: case 14: case 18: case 25: case 26:
                return new int[]{0xFF4A5D75, 0xFF6B8CAE}; // 雨
            case 22: case 23: case 24: case 19: case 20: case 21:
                return new int[]{0xFF6E8CAE, 0xFFA8BFD4}; // 雪
            case 11:
                return new int[]{0xFF6B7A8C, 0xFF9BA9B8}; // 雾
            case 6: case 7: case 8: case 31: case 32:
                return new int[]{0xFF5E718A, 0xFF8FA5BC}; // 多云/阴
            case 5:
                return new int[]{0xFF77838F, 0xFFA5ACB2}; // 朦胧
            default:
                return new int[]{0xFF3D7DC4, 0xFF7AB6E8}; // 晴
        }
    }

    /** 是否夜间（icon 29-32 为夜间码，或 daytime 字段） */
    public static boolean isNight(int icon, String daytime) {
        if (icon >= 29 && icon <= 32) return true;
        return "n".equals(daytime);
    }
}
