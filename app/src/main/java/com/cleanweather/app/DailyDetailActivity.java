package com.cleanweather.app;

import com.cleanweather.app.data.CityStore;
import com.cleanweather.app.data.WeatherApi;
import com.cleanweather.app.data.WeatherData;
import com.cleanweather.app.ui.IconMapper;
import com.cleanweather.app.util.FormatUtil;
import com.cleanweather.app.util.TimeUtil;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * 每日预报详情页：白天/夜间详情、日出日落月相、该日逐小时明细。
 */
public class DailyDetailActivity extends Activity {

    private CityStore store;
    private WeatherData data;
    private int dayIndex;
    private LinearLayout contentList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_detail);

        store = new CityStore(this);
        dayIndex = getIntent().getIntExtra("day_index", 0);
        String cacheId = getIntent().getStringExtra("cache_id");
        final double lat = getIntent().getDoubleExtra("lat", Double.NaN);
        final double lon = getIntent().getDoubleExtra("lon", Double.NaN);

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.header).setPadding(0, statusBarHeight() + dp(2), 0, 0);
        contentList = findViewById(R.id.contentList);

        // 读缓存
        if (cacheId != null) {
            String body = store.loadWeatherCache(cacheId);
            if (body != null) {
                try {
                    data = WeatherData.parse(body);
                } catch (Exception ignore) {
                }
            }
        }
        if (data != null) {
            render();
        } else if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String body = WeatherApi.fetchOverviewBody(lat, lon);
                        final WeatherData wd = WeatherData.parse(body);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                data = wd;
                                render();
                            }
                        });
                    } catch (final Exception e) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(DailyDetailActivity.this, R.string.network_error, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            }).start();
        }
    }

    private void render() {
        if (data == null || dayIndex < 0 || dayIndex >= data.days.size()) return;
        WeatherData.DayEntry de = data.days.get(dayIndex);
        WeatherData.Daily d = de.daily;
        if (d == null) return;

        // 头部
        long todayStart = TimeUtil.todayStart();
        boolean isToday = d.valid >= todayStart && d.valid < todayStart + 24 * 3600_000L;
        String title = (isToday ? getString(R.string.today) : TimeUtil.formatWeekday(d.valid))
                + " · " + TimeUtil.formatDate(d.valid);
        ((TextView) findViewById(R.id.dayTitle)).setText(title);
        ((ImageView) findViewById(R.id.dayIcon)).setImageResource(IconMapper.iconRes(d.icon));
        ((TextView) findViewById(R.id.dayCap)).setText(d.dayCap.isEmpty() ? d.nightCap : d.dayCap);
        ((TextView) findViewById(R.id.dayTemp)).setText(String.format("%s° / %s°",
                FormatUtil.temp(d.tempHi), FormatUtil.temp(d.tempLo)));

        contentList.removeAllViews();

        // 摘要
        if (!d.daySummary.isEmpty()) {
            LinearLayout c = card();
            TextView t = new TextView(this);
            t.setText(d.daySummary);
            t.setTextColor(0xE6FFFFFF);
            t.setTextSize(14);
            c.addView(t);
            if (!d.nightSummary.isEmpty()) {
                TextView n = new TextView(this);
                n.setText(d.nightSummary);
                n.setTextColor(0xCCFFFFFF);
                n.setTextSize(13);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(6);
                c.addView(n, lp);
            }
        }

        // 白天 / 夜间对比卡
        LinearLayout dnCard = card();
        addDayNightRow(dnCard, R.string.daytime, d.dayCap, d.dayIcon,
                d.precip, d.windMax, d.windMaxDir, d.uv, d.uvDesc, d.vis, d.rh, d.feelsHi);
        addDayNightRow(dnCard, R.string.nighttime, d.nightCap, d.nightIcon,
                Double.NaN, Double.NaN, -1, Double.NaN, "", Double.NaN, Double.NaN, d.feelsLo);

        // 日出日落月相
        if (de.almanac != null) {
            LinearLayout c = card();
            cardTitle(c, getString(R.string.sun_moon));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, 0);

            row.addView(infoCell("日出", de.almanac.sunrise > 0 ? TimeUtil.formatTime(de.almanac.sunrise) : "--"));
            row.addView(infoCell("日落", de.almanac.sunset > 0 ? TimeUtil.formatTime(de.almanac.sunset) : "--"));
            row.addView(infoCell("月相", de.almanac.moonPhase.isEmpty() ? "--" : de.almanac.moonPhase));
            c.addView(row);
        }

        // 逐时明细
        if (!de.hourly.isEmpty()) {
            LinearLayout c = card();
            cardTitle(c, "逐小时明细");
            for (int i = 0; i < de.hourly.size(); i++) {
                WeatherData.Hour h = de.hourly.get(i);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(8), 0, dp(8));

                TextView time = new TextView(this);
                time.setText(TimeUtil.formatShortTime(h.valid));
                time.setTextColor(0xCCFFFFFF);
                time.setTextSize(13);
                row.addView(time, new LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT));

                ImageView icon = new ImageView(this);
                icon.setImageResource(IconMapper.iconRes(h.icon));
                row.addView(icon, new LinearLayout.LayoutParams(dp(26), dp(26)));

                TextView cap = new TextView(this);
                cap.setText(h.cap);
                cap.setTextColor(0xCCFFFFFF);
                cap.setTextSize(12.5f);
                cap.setSingleLine(true);
                cap.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.addView(cap, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView precip = new TextView(this);
                precip.setText(h.precip > 0 ? FormatUtil.percent(h.precip) : " ");
                precip.setTextColor(0xFF9CD2F0);
                precip.setTextSize(12);
                row.addView(precip, new LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView temp = new TextView(this);
                temp.setText(FormatUtil.tempWithUnit(h.temp));
                temp.setTextColor(0xFFFFFFFF);
                temp.setTextSize(14.5f);
                temp.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                row.addView(temp, new LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT));

                c.addView(row);
                if (i < de.hourly.size() - 1) {
                    View line = new View(this);
                    line.setBackgroundColor(0x14FFFFFF);
                    c.addView(line, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, 1));
                }
            }
        }
    }

    /** 白天/夜间一行对比 */
    private void addDayNightRow(LinearLayout parent, int labelRes, String cap, int icon,
                                double precip, double wind, int windDir, double uv, String uvDesc,
                                double vis, double rh, double feels) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = dp(8);

        TextView label = new TextView(this);
        label.setText(getString(labelRes));
        label.setTextColor(0x99FFFFFF);
        label.setTextSize(13);
        wrap.addView(label);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, 0);

        ImageView iv = new ImageView(this);
        iv.setImageResource(IconMapper.iconRes(icon));
        row.addView(iv, new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ilp.leftMargin = dp(10);

        TextView capTv = new TextView(this);
        capTv.setText(cap == null || cap.isEmpty() ? "—" : cap);
        capTv.setTextColor(0xFFFFFFFF);
        capTv.setTextSize(15);
        capTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        info.addView(capTv);

        StringBuilder sb = new StringBuilder();
        if (!Double.isNaN(precip)) sb.append("降水 ").append(FormatUtil.percent(precip)).append("  ");
        if (!Double.isNaN(wind)) sb.append(FormatUtil.windDirName(windDir)).append(" ").append(FormatUtil.speed(wind));
        if (!Double.isNaN(uv)) {
            if (sb.length() > 0) sb.append("  ");
            String ud = uvDesc == null || uvDesc.isEmpty() ? "" : "(" + uvDesc + ")";
            sb.append("紫外线 ").append(FormatUtil.temp(uv)).append(ud);
        }
        if (!Double.isNaN(vis)) sb.append("  能见度 ").append(FormatUtil.km(vis)).append("km");
        if (!Double.isNaN(rh)) sb.append("  湿度 ").append(FormatUtil.percent(rh));
        if (!Double.isNaN(feels)) sb.append("  体感 ").append(FormatUtil.temp(feels)).append("°");
        TextView detail = new TextView(this);
        detail.setText(sb.toString());
        detail.setTextColor(0x99FFFFFF);
        detail.setTextSize(12);
        detail.setMaxLines(2);
        info.addView(detail);
        row.addView(info, ilp);

        wrap.addView(row);
        parent.addView(wrap, wlp);
        if (labelRes == R.string.daytime) {
            View line = new View(this);
            line.setBackgroundColor(0x1AFFFFFF);
            parent.addView(line, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
    }

    private View infoCell(String label, String value) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(0x99FFFFFF);
        l.setTextSize(12);
        cell.addView(l);
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(0xFFFFFFFF);
        v.setTextSize(15);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        cell.addView(v);
        cell.setLayoutParams(lp);
        return cell;
    }

    private LinearLayout card() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setBackgroundResource(R.drawable.bg_card);
        ll.setPadding(dp(14), dp(13), dp(14), dp(13));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        contentList.addView(ll, lp);
        return ll;
    }

    private void cardTitle(LinearLayout parent, String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(15.5f);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        parent.addView(tv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int statusBarHeight() {
        int res = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (res > 0) return getResources().getDimensionPixelSize(res);
        return dp(24);
    }
}
