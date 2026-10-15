package com.cleanweather.app.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.util.Log;

import com.cleanweather.app.data.WeatherApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 版本检查：从 GitHub Releases 获取最新版本号与更新说明，并与本地版本比较。
 *
 * 优先走 GitHub API（信息最全，含安装包直链）；被限流/不可用时回退解析
 * releases.atom 订阅源，并按约定拼出安装包下载地址。
 */
public final class UpdateChecker {

    private static final String TAG = "UpdateChecker";
    public static final String REPO_OWNER = "fanji-jared";
    public static final String REPO_NAME = "CleanWeather";
    /** 项目主页 */
    public static final String REPO_PAGE =
            "https://github.com/" + REPO_OWNER + "/" + REPO_NAME;
    /**  Releases 页（最新版本下载地址） */
    public static final String RELEASES_PAGE =
            "https://github.com/" + REPO_OWNER + "/" + REPO_NAME + "/releases";
    private static final String API_LATEST =
            "https://api.github.com/repos/" + REPO_OWNER + "/" + REPO_NAME + "/releases/latest";
    private static final String ATOM_LATEST =
            "https://github.com/" + REPO_OWNER + "/" + REPO_NAME + "/releases.atom";

    /** 更新信息 */
    public static class UpdateInfo {
        public String version = "";   // 1.0.3（不含 v）
        public String tag = "";       // v1.0.3
        public String notes = "";     // 更新说明（纯文本）
        public String apkUrl = "";    // 安装包直链
    }

    private UpdateChecker() {
    }

    /** 本地版本名，如 1.0.3 */
    public static String localVersion(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionName == null ? "1.0.0" : pi.versionName;
        } catch (Exception e) {
            return "1.0.0";
        }
    }

    /** 检查是否有新版本；返回 null 表示已是最新或检查失败 */
    public static UpdateInfo check() {
        UpdateInfo info = null;
        try {
            info = fromApi();
        } catch (Exception e) {
            Log.w(TAG, "GitHub API 检查失败，改用 atom 订阅源", e);
        }
        if (info == null || info.apkUrl.isEmpty()) {
            try {
                UpdateInfo atom = fromAtom();
                if (atom != null) {
                    if (info == null) info = atom;
                    else if (info.apkUrl.isEmpty()) info.apkUrl = atom.apkUrl;
                }
            } catch (Exception e) {
                Log.w(TAG, "atom 订阅源检查失败", e);
            }
        }
        if (info == null) return null;
        if (info.version.isEmpty() || info.apkUrl.isEmpty()) return null;
        return info;
    }

    /** 是否比给定本地版本更新 */
    public static boolean isNewer(String remote, String local) {
        int[] r = parseVersion(remote);
        int[] l = parseVersion(local);
        int n = Math.max(r.length, l.length);
        for (int i = 0; i < n; i++) {
            int a = i < r.length ? r[i] : 0;
            int b = i < l.length ? l[i] : 0;
            if (a != b) return a > b;
        }
        return false;
    }

    private static int[] parseVersion(String v) {
        if (v == null) return new int[]{0};
        String s = v.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        // 只取数字与点
        Matcher m = Pattern.compile("(\\d+)(?:\\.(\\d+))*").matcher(s);
        java.util.List<Integer> nums = new java.util.ArrayList<>();
        Matcher dm = Pattern.compile("\\d+").matcher(s);
        while (dm.find() && nums.size() < 4) {
            try {
                nums.add(Integer.parseInt(dm.group()));
            } catch (Exception ignore) {
            }
        }
        if (nums.isEmpty()) return new int[]{0};
        int[] out = new int[nums.size()];
        for (int i = 0; i < out.length; i++) out[i] = nums.get(i);
        return out;
    }

    private static UpdateInfo fromApi() throws Exception {
        String body = WeatherApi.httpGet(API_LATEST);
        JSONObject root = new JSONObject(body);
        UpdateInfo info = new UpdateInfo();
        info.tag = root.optString("tag_name", "");
        info.version = stripV(info.tag);
        if (info.version.isEmpty()) info.version = stripV(root.optString("name", ""));
        info.notes = root.optString("body", "");
        JSONArray assets = root.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.optJSONObject(i);
                if (a == null) continue;
                String name = a.optString("name", "");
                if (name.toLowerCase().endsWith(".apk")) {
                    info.apkUrl = a.optString("browser_download_url", "");
                    break;
                }
            }
        }
        return info;
    }

    private static UpdateInfo fromAtom() throws Exception {
        String xml = WeatherApi.httpGet(ATOM_LATEST);
        // 取第一个 <entry>
        int entryStart = xml.indexOf("<entry>");
        if (entryStart < 0) return null;
        int entryEnd = xml.indexOf("</entry>", entryStart);
        String entry = xml.substring(entryStart, entryEnd > 0 ? entryEnd : xml.length());

        UpdateInfo info = new UpdateInfo();
        Matcher title = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL).matcher(entry);
        if (title.find()) {
            String t = title.group(1).trim();
            Matcher vm = Pattern.compile("v?(\\d+(?:\\.\\d+)+)").matcher(t);
            if (vm.find()) info.version = vm.group(1);
        }
        Matcher link = Pattern.compile("<link[^>]*href=\"([^\"]*?/releases/tag/([^\"]+))\"")
                .matcher(entry);
        if (link.find()) {
            info.tag = link.group(2);
            if (info.version.isEmpty()) info.version = stripV(info.tag);
        }
        Matcher content = Pattern.compile("<content[^>]*>(.*?)</content>", Pattern.DOTALL).matcher(entry);
        if (content.find()) info.notes = stripHtml(content.group(1));
        if (!info.version.isEmpty()) {
            info.apkUrl = "https://github.com/" + REPO_OWNER + "/" + REPO_NAME
                    + "/releases/download/" + (info.tag.isEmpty() ? "v" + info.version : info.tag)
                    + "/CleanWeather-" + info.version + "-release.apk";
        }
        return info;
    }

    private static String stripV(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.startsWith("v") || t.startsWith("V")) t = t.substring(1);
        Matcher m = Pattern.compile("\\d+(?:\\.\\d+)+").matcher(t);
        return m.find() ? m.group() : "";
    }

    /** 去掉 HTML 标签与实体，得到纯文本更新说明 */
    public static String stripHtml(String html) {
        if (html == null) return "";
        String s = html.replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
        s = s.replaceAll("(?s)<[^>]+>", "");
        s = s.replaceAll("\\r", "");
        s = s.replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }
}
