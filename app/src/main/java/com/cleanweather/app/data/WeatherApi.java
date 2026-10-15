package com.cleanweather.app.data;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MSN 天气 API 客户端（直接调用网站同源接口）
 *
 * 数据接口均经 assets.msn.cn 代理：
 *  - /service/weather/overview          完整预报（当前/逐小时/10日/预警/生活指数）
 *  - /service/weather/current           多城市批量当前温度
 *  - /service/weather/locations/search   经纬度反查地名
 *  - /service/weather/api/v6/Places/AutoSuggest  城市搜索（307 重定向）
 */
public final class WeatherApi {

    private static final String TAG = "WeatherApi";
    private static final int CONNECT_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(10);
    private static final int READ_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(15);

    private static final String BASE = "https://assets.msn.cn/service";
    private static final String API_KEY = "j5i4gDqHL6nGYwx5wi5kRhXjtf2c5qgFX9fzfk0TOo";
    private static final String LOC_API_KEY = "0QfOX3Vn51YCzitbLaRkTTBadtWpgTN8NZLW0C1SEM";
    private static final String APP_ID = "9e21380c-ff19-4c78-b4ea-19558e93a5d3";
    private static final String AUTOSUGGEST_APPID = "EDEC3CB74CF190BBBE26DF7938F3D961E925F593";
    private static final String UA =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    private static volatile String userId;

    private static String userId() {
        if (userId == null) {
            synchronized (WeatherApi.class) {
                if (userId == null) {
                    userId = "m-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.US);
                }
            }
        }
        return userId;
    }

    /** 低层 GET，跟随最多 5 次重定向 */
    public static String httpGet(String urlStr) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            for (int redirect = 0; redirect < 5; redirect++) {
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);
                conn.setRequestProperty("User-Agent", UA);
                conn.setRequestProperty("Accept", "application/json");
                conn.setInstanceFollowRedirects(false);
                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_MOVED_PERM
                        || code == HttpURLConnection.HTTP_MOVED_TEMP
                        || code == 307 || code == 308) {
                    String loc = conn.getHeaderField("Location");
                    if (loc == null || loc.isEmpty()) throw new IOException("redirect without location");
                    conn.disconnect();
                    url = new URL(url, loc);
                    conn = null;
                    continue;
                }
                if (code >= 400) throw new IOException("HTTP " + code);
                InputStream in = conn.getInputStream();
                try {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                    return bos.toString("UTF-8");
                } finally {
                    in.close();
                }
            }
            throw new IOException("too many redirects");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String commonParams() {
        return "activityId=" + UUID.randomUUID()
                + "&ocid=msftweather&cm=zh-cn&it=web"
                + "&user=" + userId() + "&scn=ANON"
                + "&appId=" + APP_ID;
    }

    /** 获取完整天气数据原始 JSON（用于缓存） */
    public static String fetchOverviewBody(double lat, double lon) throws IOException {
        String url = BASE + "/weather/overview"
                + "?apikey=" + API_KEY
                + "&" + commonParams()
                + "&units=C&wrapodata=false&cuthour=true"
                + "&lifeDays=2&lifeModes=18&includeLifeActivity=true&lifeSubTypes=1,3,4,10,26"
                + "&insights=65536&startDate=-1&endDate=%2B9"
                + "&discardFutureInsightTimeseries=false"
                + "&latitude=" + String.format(Locale.US, "%.6f", lat)
                + "&longitude=" + String.format(Locale.US, "%.6f", lon);
        return httpGet(url);
    }

    /**
     * 获取完整天气数据。
     * @param lat 纬度  @param lon 经度
     */
    public static WeatherData fetchOverview(double lat, double lon) throws IOException, JSONException {
        return WeatherData.parse(fetchOverviewBody(lat, lon));
    }

    /** 城市当前温度（城市列表用） */
    public static List<CityTemp> fetchCurrentTemps(List<City> cities) {
        List<CityTemp> out = new ArrayList<>();
        if (cities == null || cities.isEmpty()) return out;
        StringBuilder listStr = new StringBuilder();
        for (int i = 0; i < cities.size(); i++) {
            City c = cities.get(i);
            if (i > 0) listStr.append('|');
            listStr.append(String.format(Locale.US, "%.5f,%.5f", c.lat, c.lon));
        }
        String url = BASE + "/weather/current"
                + "?apikey=" + API_KEY
                + "&" + commonParams()
                + "&units=C"
                + "&latLongList=" + URLEncoder.encode(listStr.toString());
        try {
            String body = httpGet(url);
            JSONObject root = new JSONObject(body);
            JSONArray values = root.getJSONArray("value");
            for (int i = 0; i < cities.size() && i < values.length(); i++) {
                CityTemp ct = new CityTemp();
                ct.cityId = cities.get(i).id;
                try {
                    JSONArray respArr = values.getJSONObject(i).getJSONArray("responses");
                    JSONObject curObj = respArr.getJSONObject(0).getJSONArray("weather").getJSONObject(0).getJSONObject("current");
                    ct.temp = curObj.optDouble("temp", Double.NaN);
                    ct.cap = curObj.optString("cap", "");
                    ct.icon = curObj.optInt("icon", -1);
                    ct.ok = true;
                } catch (Exception ignore) {
                    ct.ok = false;
                }
                out.add(ct);
            }
        } catch (Exception e) {
            Log.e(TAG, "fetchCurrentTemps failed", e);
        }
        return out;
    }

    /** 批量温度结果 */
    public static class CityTemp {
        public String cityId;
        public double temp = Double.NaN;
        public String cap = "";
        public int icon = -1;
        public boolean ok;
    }

    /** 经纬度反查地名 */
    public static City reverseGeocode(double lat, double lon) {
        try {
            String url = BASE + "/weather/locations/search"
                    + "?apikey=" + LOC_API_KEY
                    + "&ocid=localizelocation-peregrine"
                    + "&cm=zh-cn&it=web&user=" + userId() + "&scn=ANON"
                    + "&lat=" + String.format(Locale.US, "%.6f", lat)
                    + "&lon=" + String.format(Locale.US, "%.6f", lon)
                    + "&locale=zh-cn";
            String body = httpGet(url);
            JSONObject root = new JSONObject(body);
            JSONArray value = root.getJSONArray("value");
            if (value.length() == 0) return null;
            JSONArray responses = value.getJSONObject(0).getJSONArray("responses");
            if (responses.length() == 0) return null;
            JSONArray locations = responses.getJSONObject(0).getJSONArray("locations");
            if (locations.length() == 0) return null;
            JSONObject loc = locations.getJSONObject(0);

            City city = new City();
            String locality = loc.optString("locality", "");
            String adminDistrict = loc.optString("adminDistrict", "");
            String adminDistrict2 = loc.optString("adminDistrict2", "");
            String country = loc.optString("countryRegion", "");

            // 名称：市 + 区
            StringBuilder name = new StringBuilder();
            if (!adminDistrict2.isEmpty()) name.append(adminDistrict2);
            else if (!adminDistrict.isEmpty()) name.append(adminDistrict);
            if (!locality.isEmpty() && !adminDistrict2.equals(locality)) {
                name.append(" · ").append(locality);
            }
            if (name.length() == 0) name.append(loc.optString("name", "当前位置"));
            city.name = name.toString();

            StringBuilder detail = new StringBuilder();
            if (!adminDistrict.isEmpty()) detail.append(adminDistrict);
            if (!country.isEmpty()) {
                if (detail.length() > 0) detail.append(" · ");
                detail.append(country);
            }
            city.detail = detail.toString();

            // 坐标优先使用服务端回传的数据源坐标
            city.lat = lat;
            city.lon = lon;
            try {
                JSONArray ds = loc.getJSONArray("dataSources");
                if (ds.length() > 0) {
                    JSONObject coord = ds.getJSONObject(0).optJSONObject("coordinates");
                    if (coord != null) {
                        city.lat = coord.optDouble("lat", lat);
                        city.lon = coord.optDouble("lon", lon);
                    }
                }
            } catch (Exception ignore) {
            }
            city.isLocated = true;
            return city;
        } catch (Exception e) {
            Log.e(TAG, "reverseGeocode failed", e);
            return null;
        }
    }

    /** 城市搜索（Bing Places AutoSuggest） */
    public static List<City> searchCities(String query) {
        List<City> out = new ArrayList<>();
        try {
            String url = BASE + "/weather/api/v6/Places/AutoSuggest"
                    + "?appid=" + AUTOSUGGEST_APPID
                    + "&count=10&q=" + URLEncoder.encode(query, "UTF-8")
                    + "&setmkt=zh-cn&setlang=zh-cn&types=Place,Address,Business"
                    + "&abbrtext=1&setflight=&activityId=" + UUID.randomUUID();
            String body = httpGet(url);
            JSONObject root = new JSONObject(body);
            JSONArray value = root.optJSONArray("value");
            if (value == null) return out;
            for (int i = 0; i < value.length(); i++) {
                JSONObject item = value.optJSONObject(i);
                if (item == null) continue;
                if (!"Place".equals(item.optString("_type", "Place"))) continue;
                JSONObject geo = item.optJSONObject("geo");
                if (geo == null) continue;
                double lat = geo.optDouble("latitude", Double.NaN);
                double lon = geo.optDouble("longitude", Double.NaN);
                if (Double.isNaN(lat) || Double.isNaN(lon)) continue;
                City c = new City();
                c.name = item.optString("name", "");
                if (c.name.isEmpty()) continue;
                JSONObject addr = item.optJSONObject("address");
                if (addr != null) c.detail = addr.optString("text", "");
                c.lat = lat;
                c.lon = lon;
                c.isLocated = false;
                out.add(c);
            }
        } catch (Exception e) {
            Log.e(TAG, "searchCities failed", e);
        }
        return out;
    }
}
