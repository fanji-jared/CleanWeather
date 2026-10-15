package com.cleanweather.app.data;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/** 城市模型 */
public class City {
    public String id = UUID.randomUUID().toString();
    public String name = "";
    public String detail = "";
    public double lat;
    public double lon;
    public boolean isLocated; // 是否为“当前定位”城市

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("detail", detail);
        o.put("lat", lat);
        o.put("lon", lon);
        o.put("located", isLocated);
        return o;
    }

    public static City fromJson(JSONObject o) throws JSONException {
        City c = new City();
        c.id = o.optString("id", UUID.randomUUID().toString());
        c.name = o.optString("name", "");
        c.detail = o.optString("detail", "");
        c.lat = o.optDouble("lat", 0);
        c.lon = o.optDouble("lon", 0);
        c.isLocated = o.optBoolean("located", false);
        return c;
    }
}
