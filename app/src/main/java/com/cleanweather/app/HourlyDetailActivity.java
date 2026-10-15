package com.cleanweather.app;

import com.cleanweather.app.data.CityStore;
import com.cleanweather.app.data.WeatherApi;
import com.cleanweather.app.data.WeatherData;
import com.cleanweather.app.ui.IconMapper;
import com.cleanweather.app.ui.MetricChartView;
import com.cleanweather.app.util.FormatUtil;
import com.cleanweather.app.util.SystemBar;
import com.cleanweather.app.util.TimeUtil;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 每小时预报二级页：48 小时指标曲线 + 逐时列表。
 * 指标：温度/体感/降水概率/降水量/风速/湿度/云量/气压/紫外线/露点/能见度。
 */
public class HourlyDetailActivity extends Activity {

    /** 指标定义 */
    private static final int M_TEMP = 0, M_FEELS = 1, M_PRECIP = 2, M_RAIN = 3,
            M_WIND = 4, M_RH = 5, M_CLOUD = 6, M_BARO = 7, M_UV = 8, M_DEW = 9, M_VIS = 10;

    private static final int[] METRIC_NAMES = {
            R.string.metric_temp, R.string.metric_feels, R.string.metric_precip_prob,
            R.string.metric_rain, R.string.metric_wind, R.string.metric_humidity,
            R.string.metric_cloud, R.string.metric_pressure, R.string.metric_uv,
            R.string.metric_dew, R.string.metric_vis
    };
    private static final int[] METRIC_COLORS = {
            0xFFFFC94D, 0xFFFFB74D, 0xFF64B5F6, 0xFF4FC3F7, 0xFF81C784,
            0xFF4DD0E1, 0xFFB0BEC5, 0xFFAED581, 0xFFFFF176, 0xFFE0E0E0, 0xFF90A4AE
    };

    private CityStore store;
    private WeatherData data;
    private List<WeatherData.Hour> hours = new ArrayList<>();
    /** 与图表数据点对齐的小时列表（过滤 NaN 后） */
    private List<WeatherData.Hour> displayHours = new ArrayList<>();

    private LinearLayout tabs, selectedCard, hourList;
    private MetricChartView chart;
    private TextView subTitle;
    private String cityName, cacheId;
    private double lat, lon;
    private int currentMetric = M_TEMP;
    private List<TextView> tabViews = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hourly_detail);

        store = new CityStore(this);
        cityName = getIntent().getStringExtra("city_name");
        cacheId = getIntent().getStringExtra("cache_id");
        lat = getIntent().getDoubleExtra("lat", Double.NaN);
        lon = getIntent().getDoubleExtra("lon", Double.NaN);

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        ((TextView) findViewById(R.id.titleText)).setText(R.string.hourly_forecast);
        subTitle = findViewById(R.id.subTitle);
        subTitle.setText(cityName == null ? "" : cityName);
        tabs = findViewById(R.id.tabs);
        chart = findViewById(R.id.chart);
        selectedCard = findViewById(R.id.selectedCard);
        hourList = findViewById(R.id.hourList);

        findViewById(R.id.header).setPadding(0, dp(2), 0, 0);
        SystemBar.applyHeader(this, 0xFF1B2A4A, 0xFF1B2A4A);

        chart.setSelectListener(new MetricChartView.SelectListener() {
            @Override
            public void onSelected(int index) {
                renderSelected(index);
            }
        });

        loadData();
    }

    private void loadData() {
        // 先读缓存（主页面刚拉取过）
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
            onData();
            return;
        }
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            Toast.makeText(this, R.string.network_error, Toast.LENGTH_SHORT).show();
            return;
        }
        // 无缓存时重新拉取
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
                            onData();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(HourlyDetailActivity.this, R.string.network_error, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void onData() {
        if (data == null) return;
        hours = data.allHours();
        // 限制 48 小时
        if (hours.size() > 48) hours = hours.subList(0, 48);
        if (hours.isEmpty()) return;

        buildTabs();
        applyMetric(M_TEMP);
        buildHourList();
    }

    // ---------- 指标 Tab ----------

    private void buildTabs() {
        tabs.removeAllViews();
        tabViews.clear();
        for (int i = 0; i < METRIC_NAMES.length; i++) {
            final int metric = i;
            TextView chip = new TextView(this);
            chip.setText(getString(METRIC_NAMES[i]));
            chip.setTextSize(13);
            chip.setPadding(dp(14), dp(7), dp(14), dp(7));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyMetric(metric);
                }
            });
            tabs.addView(chip);
            tabViews.add(chip);
        }
    }

    private void applyMetric(int metric) {
        currentMetric = metric;
        for (int i = 0; i < tabViews.size(); i++) {
            boolean sel = i == metric;
            tabViews.get(i).setBackgroundResource(sel ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            tabViews.get(i).setTextColor(sel ? 0xFF231F20 : 0xCCFFFFFF);
        }

        List<MetricChartView.Point> pts = new ArrayList<>();
        List<String> xLabels = new ArrayList<>();
        List<String> vLabels = new ArrayList<>();
        displayHours.clear();
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;

        for (WeatherData.Hour h : hours) {
            double v = metricValue(h, metric);
            if (Double.isNaN(v)) continue;
            min = Math.min(min, v);
            max = Math.max(max, v);
            displayHours.add(h);
            pts.add(new MetricChartView.Point(h.valid, v));
            xLabels.add(TimeUtil.formatShortTime(h.valid));
            vLabels.add(metricLabel(h, metric));
        }
        if (pts.isEmpty()) return;

        // 给 y 轴留呼吸空间
        double pad = (max - min) * 0.15;
        if (pad < 1) pad = 1;
        if (min == max) {
            min -= 1;
            max += 1;
        } else {
            min -= pad;
            max += pad;
        }
        chart.setData(pts, xLabels, vLabels, min, max, METRIC_COLORS[metric]);
        chart.setSelected(0);
        renderSelected(0);
    }

    private double metricValue(WeatherData.Hour h, int metric) {
        switch (metric) {
            case M_TEMP: return h.temp;
            case M_FEELS: return h.feels;
            case M_PRECIP: return h.precip;
            case M_RAIN: return h.rainAmount;
            case M_WIND: return h.windSpd;
            case M_RH: return h.rh;
            case M_CLOUD: return h.cloudCover;
            case M_BARO: return h.baro;
            case M_UV: return h.uv;
            case M_DEW: return h.dewPt;
            case M_VIS: return h.vis;
        }
        return Double.NaN;
    }

    private String metricLabel(WeatherData.Hour h, int metric) {
        double v = metricValue(h, metric);
        switch (metric) {
            case M_TEMP: case M_FEELS: case M_DEW:
                return FormatUtil.temp(v) + "°";
            case M_PRECIP: return FormatUtil.percent(v);
            case M_RAIN: return FormatUtil.mm(v);
            case M_WIND: return FormatUtil.temp(v) + "";
            case M_RH: case M_CLOUD: return FormatUtil.percent(v);
            case M_BARO: return String.valueOf(Math.round(v));
            case M_UV: return String.valueOf(Math.round(v));
            case M_VIS: return FormatUtil.km(v);
        }
        return "";
    }

    // ---------- 选中小时详情 ----------

    private void renderSelected(int index) {
        if (index < 0 || index >= displayHours.size()) return;
        WeatherData.Hour h = displayHours.get(index);
        selectedCard.removeAllViews();

        // 第一行：时间 + 图标 + 温度
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout timeCol = new LinearLayout(this);
        timeCol.setOrientation(LinearLayout.VERTICAL);
        TextView day = new TextView(this);
        day.setText(TimeUtil.formatDate(h.valid) + " " + TimeUtil.formatWeekday(h.valid));
        day.setTextColor(0x99FFFFFF);
        day.setTextSize(12);
        timeCol.addView(day);
        TextView time = new TextView(this);
        time.setText(TimeUtil.formatShortTime(h.valid));
        time.setTextColor(0xFFFFFFFF);
        time.setTextSize(24);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        timeCol.addView(time);
        top.addView(timeCol);

        ImageView icon = new ImageView(this);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(42), dp(42));
        ilp.leftMargin = dp(16);
        ilp.rightMargin = dp(10);
        icon.setImageResource(IconMapper.iconRes(h.icon));
        top.addView(icon, ilp);

        LinearLayout capCol = new LinearLayout(this);
        capCol.setOrientation(LinearLayout.VERTICAL);
        TextView temp = new TextView(this);
        temp.setText(FormatUtil.tempWithUnit(h.temp) + "  体感 " + FormatUtil.tempWithUnit(h.feels));
        temp.setTextColor(0xFFFFFFFF);
        temp.setTextSize(15);
        temp.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        capCol.addView(temp);
        TextView cap = new TextView(this);
        cap.setText(h.cap);
        cap.setTextColor(0xCCFFFFFF);
        cap.setTextSize(13);
        capCol.addView(cap);
        top.addView(capCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        selectedCard.addView(top);

        // 指标格
        double v = metricValue(h, currentMetric);
        String metricName = getString(METRIC_NAMES[currentMetric]);
        String metricVal = metricLabel(h, currentMetric);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        grid.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.topMargin = dp(12);
        grid.setPadding(0, dp(10), 0, dp(2));
        selectedCard.addView(grid, glp);

        // 当前指标高亮 + 其他常规指标
        String[][] cells = {
                {metricName, metricVal},
                {"降水", FormatUtil.percent(h.precip)},
                {"风速", FormatUtil.windDirName(h.windDir) + " " + FormatUtil.speed(h.windSpd)},
                {"湿度", FormatUtil.percent(h.rh)}
        };
        for (String[] cell : cells) {
            LinearLayout cv = new LinearLayout(this);
            cv.setOrientation(LinearLayout.VERTICAL);
            cv.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            TextView l = new TextView(this);
            l.setText(cell[0]);
            l.setTextColor(0x99FFFFFF);
            l.setTextSize(11.5f);
            cv.addView(l);
            TextView val = new TextView(this);
            val.setText(cell[1]);
            val.setTextColor(0xFFFFFFFF);
            val.setTextSize(14);
            val.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            cv.addView(val);
            grid.addView(cv, lp);
        }
    }

    // ---------- 逐时列表 ----------

    private void buildHourList() {
        hourList.removeAllViews();
        // 标题
        TextView title = new TextView(this);
        title.setText("逐小时明细（48 小时）");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(15);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hourList.addView(title);

        for (int i = 0; i < hours.size(); i++) {
            final WeatherData.Hour h = hours.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(9), 0, dp(9));

            TextView time = new TextView(this);
            time.setText(TimeUtil.formatShortTime(h.valid));
            time.setTextColor(0xCCFFFFFF);
            time.setTextSize(13);
            row.addView(time, new LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT));

            ImageView icon = new ImageView(this);
            icon.setImageResource(IconMapper.iconRes(h.icon));
            row.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));

            TextView cap = new TextView(this);
            cap.setText(h.cap);
            cap.setTextColor(0xCCFFFFFF);
            cap.setTextSize(13);
            cap.setSingleLine(true);
            cap.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(cap, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView precip = new TextView(this);
            precip.setText(h.precip > 0 ? FormatUtil.percent(h.precip) : " ");
            precip.setTextColor(0xFF9CD2F0);
            precip.setTextSize(12.5f);
            row.addView(precip, new LinearLayout.LayoutParams(dp(42), ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView temp = new TextView(this);
            temp.setText(FormatUtil.tempWithUnit(h.temp));
            temp.setTextColor(0xFFFFFFFF);
            temp.setTextSize(15);
            temp.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            row.addView(temp, new LinearLayout.LayoutParams(dp(46), ViewGroup.LayoutParams.WRAP_CONTENT));

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int idx = displayHours.indexOf(h);
                    if (idx >= 0) {
                        chart.setSelected(idx);
                        renderSelected(idx);
                    }
                }
            });
            hourList.addView(row);

            if (i < hours.size() - 1) {
                View line = new View(this);
                line.setBackgroundColor(0x14FFFFFF);
                hourList.addView(line, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1));
            }
        }
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
