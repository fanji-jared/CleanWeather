package com.cleanweather.app;

import com.cleanweather.app.data.City;
import com.cleanweather.app.data.CityStore;
import com.cleanweather.app.data.WeatherApi;
import com.cleanweather.app.data.WeatherData;
import com.cleanweather.app.ui.AqiBarView;
import com.cleanweather.app.ui.IconMapper;
import com.cleanweather.app.ui.PullScrollView;
import com.cleanweather.app.ui.RangeBarView;
import com.cleanweather.app.update.UpdateService;
import com.cleanweather.app.util.FormatUtil;
import com.cleanweather.app.util.Locator;
import com.cleanweather.app.util.SystemBar;
import com.cleanweather.app.util.TimeUtil;
import com.cleanweather.app.util.UpdateFlow;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 主页：当前天气 + 分钟级降水 + 每小时/每日预报 + 空气质量 + 详情 + 生活指数 + 日出日落。
 * 完整复刻 MSN 天气页面的功能卡片，但无广告、无资讯流。
 */
public class MainActivity extends Activity {

    private static final int REQ_LOCATION = 100;
    private static final int REQ_CITY_PICK = 200;

    private CityStore store;
    private City city;
    private WeatherData data;

    private FrameLayout root;
    private PullScrollView scroll;
    private LinearLayout contentList;
    private TextView cityName, currentTemp, currentCap, tempHiLo, footer;
    private ImageView currentIcon;
    private View loadingOverlay, errorOverlay;
    private TextView errorText;
    private ImageButton btnRefresh, btnLocate;

    private View refreshHeader;
    private ImageView refreshIcon;
    private TextView refreshText;

    private boolean refreshing;
    private boolean locating;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        store = new CityStore(this);
        store.initDefault();

        bindViews();
        setupStatusBar();
        setupPullToRefresh();

        findViewById(R.id.cityButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(MainActivity.this, CityListActivity.class), REQ_CITY_PICK);
            }
        });
        findViewById(R.id.errorRetry).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh(true);
            }
        });
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh(false);
            }
        });
        btnLocate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestLocationFlow();
            }
        });
        findViewById(R.id.btnSettings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });

        loadSelectedCity();

        // 首次启动就申请定位权限（此前缺失，导致权限默认关闭、无法定位）
        // 用 post 延迟到界面布局完成后再申请，确保系统权限对话框能正常弹出
        root.post(new Runnable() {
            @Override
            public void run() {
                ensureLocationPermissionOnStart();
                // 安装成功后：清理上一次下载的更新包（避免占用空间）
                UpdateService.cleanup(MainActivity.this);
                // 启动时自动检查升级（可在设置中关闭）
                if (store.isAutoCheckUpdate()) {
                    UpdateFlow.checkAndPrompt(MainActivity.this, false);
                }
            }
        });
    }

    private void bindViews() {
        root = findViewById(R.id.root);
        scroll = findViewById(R.id.scroll);
        contentList = findViewById(R.id.contentList);
        cityName = findViewById(R.id.cityName);
        currentTemp = findViewById(R.id.currentTemp);
        currentCap = findViewById(R.id.currentCap);
        tempHiLo = findViewById(R.id.tempHiLo);
        currentIcon = findViewById(R.id.currentIcon);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        errorOverlay = findViewById(R.id.errorOverlay);
        errorText = findViewById(R.id.errorText);
        footer = findViewById(R.id.footer);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnLocate = findViewById(R.id.btnLocate);
        refreshHeader = findViewById(R.id.refreshHeader);
        refreshIcon = findViewById(R.id.refreshIcon);
        refreshText = findViewById(R.id.refreshText);
    }

    private void setupStatusBar() {
        // 状态栏 / 导航栏配色跟随顶部、底部背景色（在渲染时动态更新）
        SystemBar.applyGradient(this, getResources().getColor(R.color.bg_day_top),
                getResources().getColor(R.color.bg_day_bottom));
    }

    /** 顶部下拉刷新：显示刷新图标与"上次更新"提示，松手后重新获取数据 */
    private void setupPullToRefresh() {
        if (refreshHeader != null) {
            scroll.setRefreshHeader(refreshHeader, refreshIcon);
        }
        scroll.setPullCallback(new PullScrollView.PullCallback() {
            @Override
            public void onPullProgress(float progress) {
                if (refreshText == null || refreshing) return;
                if (progress >= 1f) {
                    refreshText.setText(getString(R.string.pull_release_refresh, lastUpdateText()));
                } else {
                    refreshText.setText(getString(R.string.pull_last_update, lastUpdateText()));
                }
            }

            @Override
            public void onPullRefresh() {
                if (refreshText != null) {
                    refreshText.setText(getString(R.string.pull_last_update, lastUpdateText()));
                }
                refresh(false);
            }
        });
    }

    /** 最近一次天气数据的更新时间文案（打开应用/下拉刷新后自动更新） */
    private String lastUpdateText() {
        long t = store.getLastUpdateTime();
        return t > 0 ? TimeUtil.formatUpdateAt(t) : getString(R.string.last_update_unknown);
    }

    private void loadSelectedCity() {
        String id = store.getSelectedCityId();
        for (City c : store.loadCities()) {
            if (c.id.equals(id)) {
                city = c;
                break;
            }
        }
        if (city == null) {
            List<City> all = store.loadCities();
            city = all.isEmpty() ? defaultBeijing() : all.get(0);
        }
        renderCity();
        refresh(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent dataIntent) {
        super.onActivityResult(requestCode, resultCode, dataIntent);
        if (requestCode == REQ_CITY_PICK) {
            City old = city;
            loadSelectedCity();
            if (old == null || !old.id.equals(city.id)) {
                renderCity();
                refresh(true);
            }
        }
    }

    private City defaultBeijing() {
        City c = new City();
        c.name = "北京市";
        c.detail = "中国";
        c.lat = 39.9042;
        c.lon = 116.4074;
        return c;
    }

    // ================= 数据加载 =================

    private void refresh(boolean showLoading) {
        if (refreshing || city == null) return;
        refreshing = true;
        if (showLoading && data == null) loadingOverlay.setVisibility(View.VISIBLE);
        errorOverlay.setVisibility(View.GONE);
        btnRefresh.setAlpha(0.4f);

        final double lat = city.lat;
        final double lon = city.lon;

        // 先展示缓存
        String cached = store.loadWeatherCache(city.id);
        if (data == null && cached != null) {
            try {
                WeatherData cd = WeatherData.parse(cached);
                if (cd != null) {
                    data = cd;
                    render();
                }
            } catch (Exception ignore) {
            }
            loadingOverlay.setVisibility(View.GONE);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String body = WeatherApi.fetchOverviewBody(lat, lon);
                    final WeatherData wd = WeatherData.parse(body);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            store.saveWeatherCache(city.id, body);
                            store.setLastUpdateTime(System.currentTimeMillis());
                            data = wd;
                            render();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (data == null) {
                                loadingOverlay.setVisibility(View.GONE);
                                errorText.setText(getString(R.string.network_error));
                                errorOverlay.setVisibility(View.VISIBLE);
                            } else {
                                Toast.makeText(MainActivity.this, R.string.network_error, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } finally {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            refreshing = false;
                            btnRefresh.setAlpha(1f);
                            loadingOverlay.setVisibility(View.GONE);
                            // 刷新结束收起下拉头部
                            if (scroll != null) scroll.collapseHeader();
                        }
                    });
                }
            }
        }).start();
    }

    // ================= 渲染 =================

    private void renderCity() {
        if (city == null || cityName == null) return;
        cityName.setText(city.name);
        setTitle(city.name + " - " + getString(R.string.app_name));
    }

    private void render() {
        if (data == null) return;
        renderCity();

        WeatherData.Current cur = data.current;
        if (cur != null) {
            currentTemp.setText(FormatUtil.tempWithUnit(cur.temp));
            currentCap.setText(cur.cap);
            boolean night = IconMapper.isNight(cur.icon, cur.daytime);
            currentIcon.setImageResource(IconMapper.iconRes(cur.icon));

            // 背景渐变
            int[] grad = IconMapper.gradientFor(cur.icon, !night);
            animateBackground(grad[0], grad[1]);

            // 高低温（取今天）
            String hiLo = "";
            if (!data.days.isEmpty()) {
                WeatherData.Daily d0 = data.days.get(0).daily;
                if (d0 != null) {
                    hiLo = String.format(Locale.getDefault(), "最高 %s°   最低 %s°",
                            FormatUtil.temp(d0.tempHi), FormatUtil.temp(d0.tempLo));
                }
            }
            tempHiLo.setText(hiLo);
        }

        contentList.removeAllViews();
        renderAlerts();
        renderNowcast();
        renderHourlyCard();
        renderDailyCard();
        renderAqiCard();
        renderDetailsCard();
        renderLifeCard();
        renderSunCard();
        renderInsights();

        String updateTime = data.fetchedAt > 0 ? TimeUtil.formatUpdateAt(data.fetchedAt) : "";
        String provider = data.provider == null || data.provider.isEmpty() ? "" : " · " + data.provider;
        // footer 可能因布局差异未绑定，做一次判空，避免渲染阶段空指针闪退
        if (footer != null) {
            footer.setText(String.format("更新于 %s%s", updateTime, provider));
        }
    }

    private void animateBackground(int top, int bottom) {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        root.setBackground(gd);
        // 状态栏取顶部色、导航栏取底部色，让顶部/底部与系统栏浑然一体
        SystemBar.applyGradient(this, top, bottom);
    }

    /** 通用卡片容器 */
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

    /** 卡片标题行，可带右侧"更多"入口 */
    private View cardTitle(LinearLayout parent, String title, final View.OnClickListener more) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(16);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (more != null) {
            TextView moreBtn = new TextView(this);
            moreBtn.setText("查看24小时 >");
            moreBtn.setTextColor(0xCCFFFFFF);
            moreBtn.setTextSize(12.5f);
            moreBtn.setPadding(dp(8), dp(4), dp(4), dp(4));
            row.addView(moreBtn);
            moreBtn.setOnClickListener(more);
        }
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    // ---------- 预警 ----------

    private void renderAlerts() {
        for (final WeatherData.Alert a : data.alerts) {
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setBackgroundResource(R.drawable.bg_card_alert);
            c.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(10);
            contentList.addView(c, lp);

            TextView title = new TextView(this);
            title.setText("⚠ " + (a.headline.isEmpty() ? "天气预警" : a.headline));
            title.setTextColor(0xFFFFEBEE);
            title.setTextSize(14.5f);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            c.addView(title);

            if (!a.description.isEmpty()) {
                final TextView desc = new TextView(this);
                desc.setText(a.description);
                desc.setTextColor(0xE6FFEBEE);
                desc.setTextSize(12.5f);
                desc.setMaxLines(3);
                desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
                c.addView(desc);
                c.setOnClickListener(new View.OnClickListener() {
                    boolean expanded = false;

                    @Override
                    public void onClick(View v) {
                        expanded = !expanded;
                        desc.setMaxLines(expanded ? Integer.MAX_VALUE : 3);
                    }
                });
            }
        }
    }

    // ---------- 分钟级降水 ----------

    private void renderNowcast() {
        WeatherData.Nowcast nc = data.nowcast;
        if (nc == null || nc.precip.isEmpty()) return;

        LinearLayout c = card();
        cardTitle(c, "降水预告", null);

        TextView tv = new TextView(this);
        tv.setText(nc.summary.isEmpty() ? getString(R.string.no_precip_2h) : nc.summary);
        tv.setTextColor(0xE6FFFFFF);
        tv.setTextSize(14);
        c.addView(tv);

        // 降水强度条（未来 ~2 小时，每 4 分钟一格）
        LinearLayout bars = new LinearLayout(this);
        bars.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(26));
        blp.topMargin = dp(8);
        for (double v : nc.precip) {
            View bar = new View(this);
            LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            b.leftMargin = dp(1);
            b.rightMargin = dp(1);
            bar.setLayoutParams(b);
            double ratio = Math.max(0, Math.min(1, v / 20.0));
            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius(dp(2));
            int color = v <= 0 ? 0x26FFFFFF : blend(0xFF7EC8FF, 0xFF3F72C4, ratio);
            gd.setColor(color);
            bar.setBackground(gd);
            bar.setScaleY((float) (v <= 0 ? 0.15 : 0.15 + 0.85 * ratio));
            bar.setPivotY(dp(26));
            bars.addView(bar);
        }
        c.addView(bars, blp);

        TextView end = new TextView(this);
        end.setText("未来 2 小时");
        end.setTextColor(0x80FFFFFF);
        end.setTextSize(10.5f);
        c.addView(end);
    }

    // ---------- 每小时预报卡片 ----------

    private void renderHourlyCard() {
        List<WeatherData.Hour> all = data.allHours();
        if (all.isEmpty()) return;

        // 取接下来 24 个小时（从当前时间起）
        long now = System.currentTimeMillis();
        List<WeatherData.Hour> next24 = new ArrayList<>();
        for (WeatherData.Hour h : all) {
            if (h.valid >= now - 3600_000L) next24.add(h);
            if (next24.size() >= 24) break;
        }
        if (next24.isEmpty()) next24 = all.subList(0, Math.min(24, all.size()));

        LinearLayout c = card();
        cardTitle(c, getString(R.string.hourly_forecast), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openHourlyDetail();
            }
        });

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        hsv.addView(strip);

        for (int i = 0; i < next24.size(); i++) {
            WeatherData.Hour h = next24.get(i);
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setPadding(dp(2), dp(6), dp(2), dp(6));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT);
            ilp.rightMargin = dp(2);

            TextView time = new TextView(this);
            time.setText(i == 0 ? "现在" : TimeUtil.formatShortTime(h.valid));
            time.setTextColor(0xCCFFFFFF);
            time.setTextSize(12);
            time.setGravity(Gravity.CENTER);
            item.addView(time);

            ImageView icon = new ImageView(this);
            LinearLayout.LayoutParams iclp = new LinearLayout.LayoutParams(dp(30), dp(30));
            iclp.topMargin = dp(4);
            iclp.bottomMargin = dp(2);
            icon.setImageResource(IconMapper.iconRes(h.icon));
            item.addView(icon, iclp);

            TextView temp = new TextView(this);
            temp.setText(FormatUtil.tempWithUnit(h.temp));
            temp.setTextColor(0xFFFFFFFF);
            temp.setTextSize(14.5f);
            temp.setGravity(Gravity.CENTER);
            temp.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            item.addView(temp);

            TextView precip = new TextView(this);
            precip.setText(h.precip > 0 ? FormatUtil.percent(h.precip) : " ");
            precip.setTextColor(0xFF9CD2F0);
            precip.setTextSize(11);
            precip.setGravity(Gravity.CENTER);
            item.addView(precip);

            strip.addView(item, ilp);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        c.addView(hsv, lp);
    }

    private void openHourlyDetail() {
        Intent it = new Intent(this, HourlyDetailActivity.class);
        it.putExtra("city_name", city.name);
        it.putExtra("cache_id", city.id);
        it.putExtra("lat", city.lat);
        it.putExtra("lon", city.lon);
        startActivity(it);
    }

    // ---------- 每日预报卡片 ----------

    private void renderDailyCard() {
        if (data.days.isEmpty()) return;

        // 全周温度范围
        double gMin = Double.MAX_VALUE, gMax = -Double.MAX_VALUE;
        for (WeatherData.DayEntry de : data.days) {
            if (de.daily != null) {
                if (!Double.isNaN(de.daily.tempLo)) gMin = Math.min(gMin, de.daily.tempLo);
                if (!Double.isNaN(de.daily.tempHi)) gMax = Math.max(gMax, de.daily.tempHi);
            }
        }
        if (gMin > gMax) {
            gMin = 0;
            gMax = 30;
        }

        LinearLayout c = card();
        cardTitle(c, getString(R.string.daily_forecast), null);

        long todayStart = TimeUtil.todayStart();
        for (int i = 0; i < data.days.size(); i++) {
            final WeatherData.DayEntry de = data.days.get(i);
            if (de.daily == null) continue;
            final int dayIndex = i;
            final boolean isToday = de.daily.valid >= todayStart
                    && de.daily.valid < todayStart + 24 * 3600_000L;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));
            row.setClickable(true);

            // 左侧日期
            LinearLayout dateCol = new LinearLayout(this);
            dateCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT);

            TextView wd = new TextView(this);
            wd.setText(isToday ? getString(R.string.today) : TimeUtil.formatWeekday(de.daily.valid));
            wd.setTextColor(0xFFFFFFFF);
            wd.setTextSize(14.5f);
            wd.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            dateCol.addView(wd);

            TextView date = new TextView(this);
            date.setText(TimeUtil.formatDate(de.daily.valid));
            date.setTextColor(0x80FFFFFF);
            date.setTextSize(11);
            dateCol.addView(date);
            row.addView(dateCol, dlp);

            // 图标
            ImageView icon = new ImageView(this);
            LinearLayout.LayoutParams iclp = new LinearLayout.LayoutParams(dp(32), dp(32));
            icon.setImageResource(IconMapper.iconRes(de.daily.icon));
            row.addView(icon, iclp);

            // 降水概率
            TextView pr = new TextView(this);
            pr.setText(de.daily.precip > 0 ? FormatUtil.percent(de.daily.precip) : " ");
            pr.setTextColor(0xFF9CD2F0);
            pr.setTextSize(12);
            pr.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(42), ViewGroup.LayoutParams.WRAP_CONTENT);
            row.addView(pr, plp);

            // 温度区间
            TextView hi = new TextView(this);
            hi.setText(FormatUtil.temp(de.daily.tempHi) + "°");
            hi.setTextColor(0xFFFFFFFF);
            hi.setTextSize(15);
            hi.setGravity(Gravity.CENTER);
            row.addView(hi, new LinearLayout.LayoutParams(dp(36), ViewGroup.LayoutParams.WRAP_CONTENT));

            RangeBarView bar = new RangeBarView(this);
            bar.setRange(gMin, gMax, de.daily.tempLo, de.daily.tempHi);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, dp(6), 1f);
            blp.leftMargin = dp(6);
            blp.rightMargin = dp(6);
            row.addView(bar, blp);

            TextView lo = new TextView(this);
            lo.setText(FormatUtil.temp(de.daily.tempLo) + "°");
            lo.setTextColor(0xB3FFFFFF);
            lo.setTextSize(15);
            lo.setGravity(Gravity.CENTER);
            row.addView(lo, new LinearLayout.LayoutParams(dp(30), ViewGroup.LayoutParams.WRAP_CONTENT));

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent it = new Intent(MainActivity.this, DailyDetailActivity.class);
                    it.putExtra("day_index", dayIndex);
                    it.putExtra("city_name", city.name);
                    it.putExtra("cache_id", city.id);
                    it.putExtra("lat", city.lat);
                    it.putExtra("lon", city.lon);
                    startActivity(it);
                }
            });

            // 行分隔线
            View line = new View(this);
            line.setBackgroundColor(0x1AFFFFFF);
            c.addView(row);
            if (i < data.days.size() - 1) {
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1);
                llp.topMargin = dp(2);
                c.addView(line, llp);
            }
        }
    }

    // ---------- 空气质量 ----------

    private void renderAqiCard() {
        WeatherData.Current cur = data.current;
        if (cur == null || Double.isNaN(cur.aqi)) return;

        LinearLayout c = card();
        cardTitle(c, getString(R.string.air_quality), null);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView num = new TextView(this);
        num.setText(FormatUtil.temp(cur.aqi));
        num.setTextColor(0xFFFFFFFF);
        num.setTextSize(34);
        num.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        row.addView(num);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ilp.leftMargin = dp(12);

        TextView sev = new TextView(this);
        String sevText = cur.aqiSeverity == null || cur.aqiSeverity.isEmpty()
                ? FormatUtil.aqiLevel(cur.aqi) : cur.aqiSeverity;
        sev.setText(sevText);
        sev.setTextColor(0xE6FFFFFF);
        sev.setTextSize(15);
        sev.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        info.addView(sev);

        if (cur.primaryPollutant != null && !cur.primaryPollutant.isEmpty()) {
            TextView pp = new TextView(this);
            pp.setText("主要污染物 " + cur.primaryPollutant);
            pp.setTextColor(0x99FFFFFF);
            pp.setTextSize(12);
            pp.setMaxLines(1);
            pp.setEllipsize(android.text.TextUtils.TruncateAt.END);
            info.addView(pp);
        }
        row.addView(info, ilp);
        c.addView(row);

        AqiBarView bar = new AqiBarView(this);
        bar.setAqi(cur.aqi);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(14));
        blp.topMargin = dp(10);
        c.addView(bar, blp);

        TextView scale = new TextView(this);
        scale.setText("0            50           100          150         200        300+");
        scale.setTextColor(0x66FFFFFF);
        scale.setTextSize(9.5f);
        c.addView(scale);
    }

    // ---------- 天气详情网格 ----------

    private void renderDetailsCard() {
        WeatherData.Current cur = data.current;
        if (cur == null) return;

        LinearLayout c = card();
        cardTitle(c, getString(R.string.weather_details), null);

        List<String[]> items = new ArrayList<>();
        items.add(new String[]{"体感温度", FormatUtil.tempWithUnit(cur.feels)});
        items.add(new String[]{"风", FormatUtil.windDirName(cur.windDir) + " " + FormatUtil.speed(cur.windSpd)});
        if (!Double.isNaN(cur.windGust) && cur.windGust > 0) {
            items.add(new String[]{"阵风", FormatUtil.speed(cur.windGust)});
        }
        items.add(new String[]{"湿度", FormatUtil.percent(cur.rh)});
        items.add(new String[]{"能见度", FormatUtil.km(cur.vis) + " 公里"});
        items.add(new String[]{"气压", FormatUtil.temp(cur.baro) + " 百帕"});
        items.add(new String[]{"露点", FormatUtil.tempWithUnit(cur.dewPt)});
        String uvText = Double.isNaN(cur.uv) ? "--" : (FormatUtil.temp(cur.uv) + " " +
                (cur.uvDesc == null || cur.uvDesc.isEmpty() ? FormatUtil.uvLevel(cur.uv) : cur.uvDesc));
        items.add(new String[]{"紫外线", uvText});
        items.add(new String[]{"云量", FormatUtil.percent(cur.cloudCover)});

        int cols = 4;
        LinearLayout row = null;
        int perRow = items.size() > 8 ? 4 : 4;
        for (int i = 0; i < items.size(); i++) {
            if (i % perRow == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = dp(10);
                c.addView(row, rlp);
            }
            String[] it = items.get(i);
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

            TextView label = new TextView(this);
            label.setText(it[0]);
            label.setTextColor(0x99FFFFFF);
            label.setTextSize(11.5f);
            label.setGravity(Gravity.CENTER);
            cell.addView(label);

            TextView val = new TextView(this);
            val.setText(it[1]);
            val.setTextColor(0xFFFFFFFF);
            val.setTextSize(14.5f);
            val.setGravity(Gravity.CENTER);
            val.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            cell.addView(val);

            row.addView(cell, clp);
        }
    }

    // ---------- 生活指数 ----------

    private void renderLifeCard() {
        if (data.life.isEmpty()) return;

        LinearLayout c = card();
        cardTitle(c, getString(R.string.life_index), null);

        LinearLayout row = null;
        for (int i = 0; i < data.life.size(); i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = dp(10);
                c.addView(row, rlp);
            }
            WeatherData.LifeIndex li = data.life.get(i);
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

            TextView name = new TextView(this);
            name.setText(li.displayName());
            name.setTextColor(0x99FFFFFF);
            name.setTextSize(12);
            name.setGravity(Gravity.CENTER);
            cell.addView(name);

            TextView val = new TextView(this);
            String v = li.shortSummary.isEmpty() ? li.summary : li.shortSummary;
            if (v.isEmpty()) v = "--";
            val.setText(v.length() > 6 ? v.substring(0, 6) : v);
            val.setTextColor(0xFFFFE082);
            val.setTextSize(15);
            val.setGravity(Gravity.CENTER);
            val.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            cell.addView(val);

            TextView desc = new TextView(this);
            if (!li.summary.isEmpty() && !li.summary.equals(v)) {
                desc.setText(li.summary);
                desc.setTextColor(0x80FFFFFF);
                desc.setTextSize(10);
                desc.setGravity(Gravity.CENTER);
                desc.setMaxLines(2);
                desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
            } else {
                desc.setText(" ");
                desc.setTextSize(10);
            }
            cell.addView(desc);
            row.addView(cell, clp);
        }
    }

    // ---------- 日出日落 ----------

    private void renderSunCard() {
        if (data.days.isEmpty()) return;
        WeatherData.DayEntry d0 = data.days.get(0);
        if (d0 == null || d0.almanac == null) return;

        LinearLayout c = card();
        cardTitle(c, getString(R.string.sun_moon), null);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        String sunrise = d0.almanac.sunrise > 0 ? TimeUtil.formatTime(d0.almanac.sunrise) : "--";
        String sunset = d0.almanac.sunset > 0 ? TimeUtil.formatTime(d0.almanac.sunset) : "--";

        row.addView(sunCell(R.drawable.ic_sunrise, getString(R.string.sunrise), sunrise));
        row.addView(sunCell(R.drawable.ic_sunset, getString(R.string.sunset), sunset));
        String moon = d0.almanac.moonPhase == null || d0.almanac.moonPhase.isEmpty()
                ? "--" : d0.almanac.moonPhase;
        row.addView(sunCell(0, getString(R.string.moon_phase), moon));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        c.addView(row, lp);
    }

    private LinearLayout sunCell(int iconRes, String label, String value) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

        if (iconRes != 0) {
            ImageView iv = new ImageView(this);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(26), dp(26));
            ilp.bottomMargin = dp(2);
            iv.setImageResource(iconRes);
            cell.addView(iv, ilp);
        } else {
            TextView ph = new TextView(this);
            ph.setText("🌙");
            ph.setTextSize(15);
            cell.addView(ph);
        }

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(0x99FFFFFF);
        l.setTextSize(11.5f);
        cell.addView(l);

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(0xFFFFFFFF);
        v.setTextSize(14.5f);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        cell.addView(v);

        cell.setLayoutParams(clp);
        return cell;
    }

    // ---------- 温度洞察 ----------

    private void renderInsights() {
        if (data.insightTexts.isEmpty()) return;
        LinearLayout c = card();
        for (int i = 0; i < Math.min(3, data.insightTexts.size()); i++) {
            TextView tv = new TextView(this);
            tv.setText("•  " + data.insightTexts.get(i));
            tv.setTextColor(0xE6FFFFFF);
            tv.setTextSize(13.5f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = i == 0 ? 0 : dp(6);
            c.addView(tv, lp);
        }
    }

    // ================= 定位 =================

    private boolean hasLocationPermission() {
        if (Build.VERSION.SDK_INT < 23) return true;
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasLocatedCity() {
        for (City c : store.loadCities()) {
            if (c.isLocated) return true;
        }
        return false;
    }

    /**
     * 启动时确保定位权限：
     *  - 已授权且还没有"当前位置"城市时，自动定位一次；
     *  - 未授权且从未申请过时，弹出系统权限对话框（修复"初次无提示、权限默认关闭"的问题）。
     */
    private void ensureLocationPermissionOnStart() {
        if (hasLocationPermission()) {
            if (!hasLocatedCity()) doLocate();
            return;
        }
        if (Build.VERSION.SDK_INT < 23) return;
        if (store.isLocationAsked()) return; // 已经问过，不再反复打扰
        store.setLocationAsked(true);
        requestPermissions(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        }, REQ_LOCATION);
    }

    private void requestLocationFlow() {
        if (!hasLocationPermission()) {
            if (Build.VERSION.SDK_INT >= 23) {
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                }, REQ_LOCATION);
            }
            return;
        }
        doLocate();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            boolean granted = false;
            for (int g : grantResults) {
                if (g == PackageManager.PERMISSION_GRANTED) granted = true;
            }
            if (granted) {
                doLocate();
            } else {
                Toast.makeText(this, R.string.grant_location, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 页面销毁时取消定位监听，避免回调打到已销毁的 Activity 或造成监听器泄漏
        Locator.cancelActive();
        locating = false;
    }

    private void doLocate() {
        if (locating) return; // 防止连点导致重复定位、监听器堆积
        locating = true;
        Toast.makeText(this, "正在定位…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                // 先尝试最后已知位置
                double[] last = Locator.lastKnown(MainActivity.this);
                if (last != null) {
                    resolveCity(last[0], last[1]);
                    return;
                }
                // 注意：Locator 的回调统一在后台线程执行，可在其中安全地做网络请求
                Locator.requestOnce(MainActivity.this, 12000, new Locator.Callback() {
                    @Override
                    public void onLocation(final double lat, final double lon) {
                        resolveCity(lat, lon);
                    }

                    @Override
                    public void onFailed(final String reason) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                locating = false;
                                Toast.makeText(MainActivity.this, "定位失败：" + reason, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            }
        }).start();
    }

    private void resolveCity(double lat, double lon) {
        City resolved = WeatherApi.resolveNearestPlace(lat, lon);
        if (resolved == null) {
            resolved = new City();
            resolved.name = "当前位置";
            resolved.lat = lat;
            resolved.lon = lon;
            resolved.isLocated = true;
        }
        // 移除旧的定位城市，加入新的并选中
        List<City> all = store.loadCities();
        City existing = null;
        for (City c : all) {
            if (c.isLocated) {
                existing = c;
                break;
            }
        }
        if (existing != null) {
            all.remove(existing);
            resolved.id = existing.id; // 保留 id 复用缓存
        }
        resolved.isLocated = true;
        all.add(0, resolved);
        store.saveCities(all);
        final String id = resolved.id;
        store.setSelectedCityId(id);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                locating = false;
                if (isFinishing() || isDestroyed()) return;
                loadSelectedCity();
                refresh(true);
            }
        });
    }

    // ================= 工具 =================

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static int statusBarHeight(Context ctx) {
        int res = ctx.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (res > 0) return ctx.getResources().getDimensionPixelSize(res);
        return (int) (24 * ctx.getResources().getDisplayMetrics().density);
    }

    private static int blend(int c1, int c2, double ratio) {
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
