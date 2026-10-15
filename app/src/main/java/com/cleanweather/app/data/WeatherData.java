package com.cleanweather.app.data;

import com.cleanweather.app.util.TimeUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * MSN 天气 overview 数据模型（完整镜像网站数据结构）
 * 数据源为中国天气网等，经由 assets.msn.cn/service/weather/overview 返回。
 */
public class WeatherData {

    /** 当前天气 */
    public static class Current {
        public double temp, feels, dewPt, uv, vis, windSpd, windGust, baro, rh, cloudCover, aqi;
        public int windDir, icon;
        public String cap = "", uvDesc = "", aqiSeverity = "", primaryPollutant = "", daytime = "";
        public long created;
    }

    /** 逐小时条目 */
    public static class Hour {
        public long valid;
        public double temp, feels, precip, windSpd, windGust, rh, uv, vis, dewPt, baro, cloudCover, rainAmount, aqi;
        public int windDir, icon;
        public String cap = "", summary = "";
    }

    /** 单日预报汇总 */
    public static class Daily {
        public long valid;
        public double tempHi, tempLo, feelsHi, feelsLo, precip, windMax, uv, rh, vis, rainAmount, snowAmount;
        public int windMaxDir, icon, dayIcon, nightIcon;
        public String dayCap = "", nightCap = "", daySummary = "", nightSummary = "", uvDesc = "";
    }

    /** 日出日落月相 */
    public static class Almanac {
        public long valid, sunrise, sunset;
        public String moonPhase = "";
    }

    /** 一天：daily + hourly + almanac */
    public static class DayEntry {
        public Daily daily;
        public List<Hour> hourly = new ArrayList<>();
        public Almanac almanac;
    }

    /** 分钟级降水预告 (nowcasting) */
    public static class Nowcast {
        public String summary = "";
        public String shortSummary = "";
        public List<Double> precip = new ArrayList<>();
        public int minutesBetween = 4;
    }

    /** 天气预警 */
    public static class Alert {
        public String headline = "", description = "", severity = "", area = "", link = "";
        public long start, end;
    }

    /** 生活指数 */
    public static class LifeIndex {
        public String name = "", summary = "", shortSummary = "";
        public double value;
        public int type, subType;

        public String displayName() {
            String n = lifeName(type, subType);
            return n != null ? n : name;
        }
    }

    /** 温度趋势洞察文本 */
    public List<String> insightTexts = new ArrayList<>();

    public Current current;
    public List<DayEntry> days = new ArrayList<>();
    public Nowcast nowcast;
    public List<Alert> alerts = new ArrayList<>();
    public List<LifeIndex> life = new ArrayList<>();
    public String provider = "";
    public long fetchedAt;

    private static double optD(JSONObject o, String k) {
        try { return o.optDouble(k, Double.NaN); } catch (Exception e) { return Double.NaN; }
    }

    private static int optI(JSONObject o, String k) {
        try { return o.optInt(k, -1); } catch (Exception e) { return -1; }
    }

    private static String optS(JSONObject o, String k) {
        try { return o.optString(k, ""); } catch (Exception e) { return ""; }
    }

    /** 从 overview 响应解析 */
    public static WeatherData parse(String body) throws JSONException {
        JSONObject root = new JSONObject(body);
        JSONArray responses = root.getJSONArray("responses");
        if (responses.length() == 0) return null;
        JSONArray weatherArr = responses.getJSONObject(0).getJSONArray("weather");
        if (weatherArr.length() == 0) return null;
        return parseWeather(weatherArr.getJSONObject(0));
    }

    private static WeatherData parseWeather(JSONObject w) throws JSONException {
        WeatherData d = new WeatherData();
        d.fetchedAt = System.currentTimeMillis();

        // ----- current -----
        JSONObject c = w.optJSONObject("current");
        if (c != null) {
            Current cur = new Current();
            cur.temp = optD(c, "temp");
            cur.feels = optD(c, "feels");
            cur.dewPt = optD(c, "dewPt");
            cur.uv = optD(c, "uv");
            cur.vis = optD(c, "vis");
            cur.windSpd = optD(c, "windSpd");
            cur.windGust = optD(c, "windGust");
            cur.baro = optD(c, "baro");
            cur.rh = optD(c, "rh");
            cur.cloudCover = optD(c, "cloudCover");
            cur.aqi = optD(c, "aqi");
            cur.windDir = optI(c, "windDir");
            cur.icon = optI(c, "icon");
            cur.cap = optS(c, "cap");
            cur.uvDesc = optS(c, "uvDesc");
            cur.aqiSeverity = optS(c, "aqiSeverity");
            cur.primaryPollutant = optS(c, "primaryPollutant");
            cur.daytime = optS(c, "daytime");
            cur.created = TimeUtil.parseIso(optS(c, "created"));
            d.current = cur;
        }

        // ----- forecast days -----
        JSONObject forecast = w.optJSONObject("forecast");
        if (forecast != null) {
            JSONArray daysArr = forecast.optJSONArray("days");
            if (daysArr != null) {
                for (int i = 0; i < daysArr.length(); i++) {
                    JSONObject dayObj = daysArr.getJSONObject(i);
                    DayEntry entry = new DayEntry();

                    JSONObject dailyObj = dayObj.optJSONObject("daily");
                    if (dailyObj != null) {
                        Daily dd = new Daily();
                        dd.valid = TimeUtil.parseIso(optS(dailyObj, "valid"));
                        dd.tempHi = optD(dailyObj, "tempHi");
                        dd.tempLo = optD(dailyObj, "tempLo");
                        dd.feelsHi = optD(dailyObj, "feelsHi");
                        dd.feelsLo = optD(dailyObj, "feelsLo");
                        dd.precip = optD(dailyObj, "precip");
                        dd.windMax = optD(dailyObj, "windMax");
                        dd.windMaxDir = optI(dailyObj, "windMaxDir");
                        dd.uv = optD(dailyObj, "uv");
                        dd.uvDesc = optS(dailyObj, "uvDesc");
                        dd.rh = optD(dailyObj, "rh");
                        dd.vis = optD(dailyObj, "vis");
                        dd.rainAmount = optD(dailyObj, "rainAmount");
                        dd.snowAmount = optD(dailyObj, "snowAmount");
                        dd.icon = optI(dailyObj, "icon");

                        JSONObject dayPart = dailyObj.optJSONObject("day");
                        if (dayPart != null) {
                            dd.dayCap = optS(dayPart, "cap");
                            dd.dayIcon = optI(dayPart, "icon");
                            JSONArray sums = dayPart.optJSONArray("summaries");
                            dd.daySummary = sums != null && sums.length() > 0 ? joinSummaries(sums) : optS(dayPart, "summary");
                        }
                        JSONObject nightPart = dailyObj.optJSONObject("night");
                        if (nightPart != null) {
                            dd.nightCap = optS(nightPart, "cap");
                            dd.nightIcon = optI(nightPart, "icon");
                            JSONArray sums = nightPart.optJSONArray("summaries");
                            dd.nightSummary = sums != null && sums.length() > 0 ? joinSummaries(sums) : optS(nightPart, "summary");
                        }
                        entry.daily = dd;
                    }

                    JSONObject almObj = dayObj.optJSONObject("almanac");
                    if (almObj != null) {
                        Almanac alm = new Almanac();
                        alm.valid = TimeUtil.parseIso(optS(almObj, "valid"));
                        alm.sunrise = TimeUtil.parseIso(optS(almObj, "sunrise"));
                        alm.sunset = TimeUtil.parseIso(optS(almObj, "sunset"));
                        alm.moonPhase = optS(almObj, "moonPhase");
                        entry.almanac = alm;
                    }

                    JSONArray hArr = dayObj.optJSONArray("hourly");
                    if (hArr != null) {
                        for (int h = 0; h < hArr.length(); h++) {
                            JSONObject hObj = hArr.getJSONObject(h);
                            Hour hour = new Hour();
                            hour.valid = TimeUtil.parseIso(optS(hObj, "valid"));
                            hour.temp = optD(hObj, "temp");
                            hour.feels = optD(hObj, "feels");
                            hour.precip = optD(hObj, "precip");
                            hour.windSpd = optD(hObj, "windSpd");
                            hour.windGust = optD(hObj, "windGust");
                            hour.rh = optD(hObj, "rh");
                            hour.uv = optD(hObj, "uv");
                            hour.vis = optD(hObj, "vis");
                            hour.dewPt = optD(hObj, "dewPt");
                            hour.baro = optD(hObj, "baro");
                            hour.cloudCover = optD(hObj, "cloudCover");
                            hour.rainAmount = optD(hObj, "rainAmount");
                            hour.aqi = optD(hObj, "aqi");
                            hour.windDir = optI(hObj, "windDir");
                            hour.icon = optI(hObj, "icon");
                            hour.cap = optS(hObj, "cap");
                            hour.summary = optS(hObj, "summary");
                            entry.hourly.add(hour);
                        }
                    }
                    d.days.add(entry);
                }
            }
        }

        // ----- nowcasting 分钟级降水 -----
        JSONObject ncObj = w.optJSONObject("nowcasting");
        if (ncObj != null && ncObj.has("precipitation")) {
            Nowcast nc = new Nowcast();
            nc.summary = optS(ncObj, "summary");
            nc.shortSummary = optS(ncObj, "shortSummary");
            if (nc.summary.isEmpty()) nc.summary = nc.shortSummary;
            nc.minutesBetween = ncObj.optInt("minutesBetweenHorrizons", 4);
            JSONArray pr = ncObj.optJSONArray("precipitation");
            if (pr != null) {
                for (int i = 0; i < pr.length(); i++) nc.precip.add(pr.optDouble(i, 0));
            }
            d.nowcast = nc;
        }

        // ----- alerts -----
        JSONArray alArr = w.optJSONArray("alerts");
        if (alArr != null) {
            for (int i = 0; i < alArr.length(); i++) {
                JSONObject aObj = alArr.optJSONObject(i);
                if (aObj == null) continue;
                Alert a = new Alert();
                a.headline = firstNonEmpty(optS(aObj, "headline"), optS(aObj, "headlineText"), optS(aObj, "title"));
                a.description = firstNonEmpty(optS(aObj, "description"), optS(aObj, "desc"), optS(aObj, "detail"));
                a.severity = firstNonEmpty(optS(aObj, "severity"), optS(aObj, "level"));
                a.area = firstNonEmpty(optS(aObj, "area"), optS(aObj, "region"), optS(aObj, "areas"));
                a.link = optS(aObj, "link");
                a.start = TimeUtil.parseIso(firstNonEmpty(optS(aObj, "start"), optS(aObj, "startTime"), optS(aObj, "effective")));
                a.end = TimeUtil.parseIso(firstNonEmpty(optS(aObj, "end"), optS(aObj, "endTime"), optS(aObj, "expires")));
                if (!a.headline.isEmpty() || !a.description.isEmpty()) d.alerts.add(a);
            }
        }

        // ----- 生活指数 -----
        JSONObject laObj = w.optJSONObject("LifeActivityResp");
        if (laObj != null) {
            JSONArray laDays = laObj.optJSONArray("days");
            if (laDays != null && laDays.length() > 0) {
                JSONArray idxArr = laDays.optJSONObject(0).optJSONArray("lifeDailyIndices");
                if (idxArr != null) {
                    for (int i = 0; i < idxArr.length(); i++) {
                        JSONObject idx = idxArr.optJSONObject(i);
                        if (idx == null) continue;
                        LifeIndex li = new LifeIndex();
                        li.type = optI(idx, "type");
                        li.subType = optI(idx, "subType");
                        li.value = optD(idx, "value");
                        li.summary = optS(idx, "summary");
                        li.shortSummary = optS(idx, "shortSummary");
                        if (li.shortSummary.isEmpty()) li.shortSummary = optS(idx, "taskbarSummary");
                        d.life.add(li);
                    }
                }
            }
        }

        // ----- insights 文本 -----
        JSONObject insObj = w.optJSONObject("insights");
        if (insObj != null) {
            JSONArray insArr = insObj.optJSONArray("insights");
            if (insArr != null) {
                for (int i = 0; i < insArr.length(); i++) {
                    JSONObject ins = insArr.optJSONObject(i);
                    if (ins == null) continue;
                    String t = firstNonEmpty(optS(ins, "fullText"), optS(ins, "teaserText"), optS(ins, "headlineText"));
                    if (!t.isEmpty()) d.insightTexts.add(t);
                }
            }
        }

        // ----- provider -----
        JSONObject prov = w.optJSONObject("provider");
        if (prov != null) d.provider = optS(prov, "name");

        return d;
    }

    private static String joinSummaries(JSONArray sums) throws JSONException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sums.length(); i++) {
            String s = sums.optString(i, "").trim();
            if (!s.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(s);
            }
        }
        return sb.toString();
    }

    private static String firstNonEmpty(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isEmpty()) return v;
        }
        return "";
    }

    /** 生活指数 (type, subType) -> 中文名 */
    public static String lifeName(int type, int subType) {
        if (type == 1) {
            switch (subType) {
                case 1: return "穿衣";
                case 2: return "洗车";
                case 3: return "风寒";
                case 4: return "紫外线";
                case 5: return "晾晒";
                case 6: return "钓鱼";
                case 10: return "雨伞";
            }
        }
        if (type == 2) {
            if (subType == 10) return "雨伞";
        }
        if (type == 3) {
            switch (subType) {
                case 1: return "运动";
                case 26: return "运动";
                case 2: return "旅行";
            }
        }
        return "生活指数";
    }

    /** 合并所有天数的逐小时数据（用于24小时页面） */
    public List<Hour> allHours() {
        List<Hour> out = new ArrayList<>();
        for (DayEntry de : days) out.addAll(de.hourly);
        return out;
    }
}
