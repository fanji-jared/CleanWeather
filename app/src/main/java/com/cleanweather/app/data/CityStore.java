package com.cleanweather.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 城市列表 + 天气缓存持久化（SharedPreferences） */
public class CityStore {

    private static final String PREFS = "clean_weather";
    private static final String KEY_CITIES = "cities";
    private static final String KEY_SELECTED = "selected_city_id";
    private static final String KEY_LOCATION_ASKED = "location_asked";
    private static final String KEY_AUTO_UPDATE = "auto_check_update";
    private static final String KEY_LAST_UPDATE = "last_update_time";
    private static final String KEY_CACHE_PREFIX = "cache_";

    private final SharedPreferences prefs;

    public CityStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<City> loadCities() {
        List<City> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_CITIES, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                out.add(City.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException ignore) {
        }
        return out;
    }

    public void saveCities(List<City> cities) {
        JSONArray arr = new JSONArray();
        for (City c : cities) {
            try {
                arr.put(c.toJson());
            } catch (JSONException ignore) {
            }
        }
        prefs.edit().putString(KEY_CITIES, arr.toString()).apply();
    }

    public String getSelectedCityId() {
        return prefs.getString(KEY_SELECTED, "");
    }

    public void setSelectedCityId(String id) {
        prefs.edit().putString(KEY_SELECTED, id).apply();
    }

    /** 天气数据缓存（离线可看） */
    public void saveWeatherCache(String cityId, String body) {
        prefs.edit().putString(KEY_CACHE_PREFIX + cityId, body).apply();
    }

    public String loadWeatherCache(String cityId) {
        return prefs.getString(KEY_CACHE_PREFIX + cityId, null);
    }

    /** 是否已经弹过一次定位权限申请（避免每次启动都打扰用户） */
    public boolean isLocationAsked() {
        return prefs.getBoolean(KEY_LOCATION_ASKED, false);
    }

    public void setLocationAsked(boolean asked) {
        prefs.edit().putBoolean(KEY_LOCATION_ASKED, asked).apply();
    }

    /** 是否自动检查升级（默认开启） */
    public boolean isAutoCheckUpdate() {
        return prefs.getBoolean(KEY_AUTO_UPDATE, true);
    }

    public void setAutoCheckUpdate(boolean on) {
        prefs.edit().putBoolean(KEY_AUTO_UPDATE, on).apply();
    }

    /** 最近一次天气数据更新时间（用于下拉刷新的"最后更新"提示） */
    public long getLastUpdateTime() {
        return prefs.getLong(KEY_LAST_UPDATE, 0L);
    }

    public void setLastUpdateTime(long time) {
        prefs.edit().putLong(KEY_LAST_UPDATE, time).apply();
    }

    /** 首次安装：默认加入北京 */
    public void initDefault() {
        if (loadCities().isEmpty()) {
            City beijing = new City();
            beijing.name = "北京市";
            beijing.detail = "中国";
            beijing.lat = 39.9042;
            beijing.lon = 116.4074;
            List<City> list = new ArrayList<>();
            list.add(beijing);
            saveCities(list);
            setSelectedCityId(beijing.id);
        }
    }
}
