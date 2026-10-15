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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MSN 天气 API 客户端（直接调用网站同源接口）
 *
 * 数据接口均经 assets.msn.cn 代理：
 *  - /service/weather/overview            完整预报（当前/逐小时/10日/预警/生活指数）
 *  - /service/weather/current             多城市批量当前温度
 *  - /service/weather/locations/search    经纬度反查地名 / 关键词地名检索
 */
public final class WeatherApi {

    private static final String TAG = "WeatherApi";
    private static final int CONNECT_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(10);
    private static final int READ_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(15);

    private static final String BASE = "https://assets.msn.cn/service";
    private static final String API_KEY = "j5i4gDqHL6nGYwx5wi5kRhXjtf2c5qgFX9fzfk0TOo";
    private static final String LOC_API_KEY = "0QfOX3Vn51YCzitbLaRkTTBadtWpgTN8NZLW0C1SEM";
    private static final String APP_ID = "9e21380c-ff19-4c78-b4ea-19558e93a5d3";
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

    // ================= 地名检索（与 MSN 搜索同源） =================

    /** 地名实体：省 / 市 / 区县 / 街道 / 道路 / 门牌地址 / 地点 */
    public static class Place {
        public String name = "";
        public String displayName = "";
        public String locality = "";
        public String adminDistrict = "";   // 省级
        public String adminDistrict2 = "";  // 市级或区级
        public String entityType = "Place";
        public double lat;
        public double lon;

        /** 是否为细粒度实体（道路 / 门牌 / 地点），即"可直接添加的地址" */
        public boolean isFine() {
            String t = entityType == null ? "" : entityType.toLowerCase(Locale.US);
            return t.contains("address") || t.contains("road") || t.contains("street")
                    || t.contains("pointofinterest") || t.contains("business");
        }

        /** 人类可读的实体类型标签 */
        public String typeLabel() {
            String t = entityType == null ? "" : entityType;
            switch (t) {
                case "AdminDivision1": return "省份";
                case "AdminDivision2": return "区县";
                case "AdminDivision3": return "区县";
                case "PopulatedPlace": return "城市";
                case "RoadBlock": return "道路";
                case "Address": return "地址";
                case "PointOfInterest": return "地点";
                case "Neighborhood": return "街区";
                default: return t.isEmpty() ? "地点" : t;
            }
        }
    }

    private static List<Place> parsePlaces(JSONArray locations, double fallbackLat, double fallbackLon) {
        List<Place> out = new ArrayList<>();
        if (locations == null) return out;
        for (int i = 0; i < locations.length(); i++) {
            JSONObject loc = locations.optJSONObject(i);
            if (loc == null) continue;
            Place p = new Place();
            p.name = loc.optString("name", "");
            if (p.name.isEmpty()) continue;
            p.displayName = loc.optString("displayName", "");
            p.locality = loc.optString("locality", "");
            p.adminDistrict = loc.optString("adminDistrict", "");
            p.adminDistrict2 = loc.optString("adminDistrict2", "");
            p.entityType = loc.optString("entityType", "Place");
            JSONObject coord = loc.optJSONObject("coordinates");
            if (coord != null) {
                p.lat = coord.optDouble("lat", fallbackLat);
                p.lon = coord.optDouble("lon", fallbackLon);
            } else {
                p.lat = fallbackLat;
                p.lon = fallbackLon;
            }
            out.add(p);
        }
        return out;
    }

    /**
     * 调用 MSN 地名检索接口。
     * @param query 文本关键词（可为空）
     * @param count 返回条数
     * @param lat   反查纬度（可为 null）  @param lon 反查经度（可为 null）
     */
    public static List<Place> searchLocations(String query, int count, Double lat, Double lon) {
        try {
            StringBuilder url = new StringBuilder(BASE + "/weather/locations/search")
                    .append("?apikey=").append(LOC_API_KEY)
                    .append("&ocid=localizelocation-peregrine")
                    .append("&cm=zh-cn&it=web&user=").append(userId()).append("&scn=ANON")
                    .append("&locale=zh-cn&count=").append(count);
            if (query != null && !query.isEmpty()) {
                url.append("&query=").append(URLEncoder.encode(query, "UTF-8"));
            }
            if (lat != null && lon != null) {
                url.append("&lat=").append(String.format(Locale.US, "%.5f", lat))
                        .append("&lon=").append(String.format(Locale.US, "%.5f", lon));
            }
            String body = httpGet(url.toString());
            JSONObject root = new JSONObject(body);
            JSONArray value = root.getJSONArray("value");
            if (value.length() == 0) return new ArrayList<>();
            JSONArray responses = value.getJSONObject(0).optJSONArray("responses");
            if (responses == null || responses.length() == 0) return new ArrayList<>();
            JSONArray locations = responses.getJSONObject(0).optJSONArray("locations");
            double fl = lat == null ? 0 : lat;
            double fn = lon == null ? 0 : lon;
            return parsePlaces(locations, fl, fn);
        } catch (Exception e) {
            Log.e(TAG, "searchLocations failed: " + query, e);
        }
        return new ArrayList<>();
    }

    /**
     * 单点反向地理编码（经纬度 → 覆盖该点的地名实体）。
     * 优先返回"细粒度实体"（道路 / 门牌 / 地点），这样自动定位才能落到
     * 最近的可添加地址上（如「大安区太大路」），而不是只到区县。
     */
    public static Place reverseGeocodePlace(double lat, double lon) {
        List<Place> list = searchLocations(null, 8, lat, lon);
        if (list.isEmpty()) return null;
        Place best = list.get(0);
        double bestDist = distanceMeters(lat, lon, best.lat, best.lon);
        for (Place p : list) {
            double d = distanceMeters(lat, lon, p.lat, p.lon);
            if (p.isFine() && !best.isFine()) {
                best = p;
                bestDist = d;
            } else if (p.isFine() == best.isFine() && d < bestDist) {
                best = p;
                bestDist = d;
            }
        }
        return best;
    }

    /**
     * 依据定位坐标解析「最近的可添加地址」：
     * 1) 先反查该点，若已是道路 / 门牌等细粒度实体，直接采用；
     * 2) 否则在其周边做环形采样反查，命中细粒度实体后取距离最近的一个；
     * 3) 仍无则退回到行政级别结果。
     * 天气坐标始终使用用户真实经纬度，避免被"行政区中心点"带偏。
     */
    public static City resolveNearestPlace(double lat, double lon) {
        Place center = reverseGeocodePlace(lat, lon);
        if (center != null && center.isFine()) {
            return cityFromPlace(center, lat, lon);
        }

        Place best = null;
        double bestDist = Double.MAX_VALUE;
        // 约 120m / 250m / 500m / 900m：由近到远寻找最近的道路 / 门牌 / 地点实体
        double[] radii = {0.0011, 0.0022, 0.0045, 0.0081};
        for (double r : radii) {
            boolean hit = false;
            for (int k = 0; k < 8; k++) {
                double ang = Math.toRadians(k * 45.0);
                double dLat = r * Math.cos(ang);
                double dLon = r * Math.sin(ang) / Math.max(0.2, Math.cos(Math.toRadians(lat)));
                Place p = reverseGeocodePlace(lat + dLat, lon + dLon);
                if (p == null || !p.isFine()) continue;
                hit = true;
                double d = distanceMeters(lat, lon, p.lat, p.lon);
                if (d < bestDist) {
                    bestDist = d;
                    best = p;
                }
            }
            if (hit) break; // 命中即止，取该环内最近的一条
        }

        if (best != null) return cityFromPlace(best, lat, lon);
        if (center != null) return cityFromPlace(center, lat, lon);
        return null;
    }

    private static City cityFromPlace(Place p, double lat, double lon) {
        City c = new City();
        c.name = shortName(p);
        c.detail = adminChain(p);
        c.type = p.entityType;
        c.lat = lat;   // 使用用户真实坐标，确保天气贴合所在地
        c.lon = lon;
        c.isLocated = true;
        return c;
    }

    /** 去掉省 / 市前缀，得到较短的定位名（如「大安区太大路」） */
    private static String shortName(Place p) {
        String s = p.name;
        if (!p.adminDistrict.isEmpty() && s.startsWith(p.adminDistrict)) {
            s = s.substring(p.adminDistrict.length());
        }
        String cityTok = "";
        if (p.locality.endsWith("市")) cityTok = p.locality;
        else if (p.adminDistrict2.endsWith("市")) cityTok = p.adminDistrict2;
        if (!cityTok.isEmpty() && s.startsWith(cityTok)) {
            s = s.substring(cityTok.length());
        }
        return s.isEmpty() ? p.name : s;
    }

    /** 省 · 市 · 区 链条（去重并按行政级别排序） */
    private static String adminChain(Place p) {
        List<String> parts = new ArrayList<>();
        for (String t : new String[]{p.adminDistrict, p.adminDistrict2, p.locality}) {
            if (t == null || t.isEmpty() || parts.contains(t)) continue;
            parts.add(t);
        }
        java.util.Collections.sort(parts, new java.util.Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return adminRank(a) - adminRank(b);
            }
        });
        StringBuilder sb = new StringBuilder();
        for (String t : parts) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(t);
        }
        return sb.toString();
    }

    private static int adminRank(String s) {
        if (s.endsWith("省") || s.endsWith("自治区") || s.endsWith("特别行政区")) return 0;
        if (s.endsWith("市")) return 1;
        return 2;
    }

    private static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * R * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /** 去掉省前缀后的地名标题（与 MSN 搜索下拉一致，如「自贡市大安区太大路」） */
    private static String placeTitle(Place p) {
        String s = p.name == null ? "" : p.name;
        if (!p.adminDistrict.isEmpty() && s.startsWith(p.adminDistrict)) {
            s = s.substring(p.adminDistrict.length());
        }
        return s.isEmpty() ? p.name : s;
    }

    /** 位置搜索（与 MSN 天气搜索体验一致，返回可直接添加的地点） */
    public static List<City> searchCities(String query) {
        List<City> out = new ArrayList<>();
        if (query == null) return out;
        String q = query.trim();
        if (q.isEmpty()) return out;
        List<Place> places = searchLocations(q, 15, null, null);
        Set<String> seen = new HashSet<>();
        for (Place p : places) {
            String key = p.name + "@" + String.format(Locale.US, "%.3f,%.3f", p.lat, p.lon);
            if (!seen.add(key)) continue;
            City c = new City();
            c.name = placeTitle(p);
            String chain = adminChain(p);
            c.detail = chain.isEmpty() ? p.typeLabel() : p.typeLabel() + " · " + chain;
            c.type = p.entityType;
            c.lat = p.lat;
            c.lon = p.lon;
            c.isLocated = false;
            out.add(c);
        }
        return out;
    }
}
