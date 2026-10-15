package com.cleanweather.app;

import com.cleanweather.app.data.City;
import com.cleanweather.app.data.CityStore;
import com.cleanweather.app.data.WeatherApi;
import com.cleanweather.app.ui.IconMapper;
import com.cleanweather.app.util.FormatUtil;
import com.cleanweather.app.util.Locator;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 城市管理页：搜索添加城市（Bing Places 接口）、批量当前温度、删除、切换。
 */
public class CityListActivity extends Activity {

    private static final int REQ_LOCATION = 101;

    private CityStore store;
    private LinearLayout cityList, searchResults;
    private EditText searchInput;
    private ImageButton btnClear;
    private Map<String, WeatherApi.CityTemp> tempMap = new HashMap<>();
    private List<City> cities;
    private boolean locating;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_city_list);

        store = new CityStore(this);
        cityList = findViewById(R.id.cityList);
        searchResults = findViewById(R.id.searchResults);
        searchInput = findViewById(R.id.searchInput);
        btnClear = findViewById(R.id.btnClear);

        findViewById(R.id.header).setPadding(0, statusBarHeight() + dp(2), 0, 0);
        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.btnLocate).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestLocate();
            }
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if (s.length() == 0) {
                    searchResults.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        searchInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    doSearch();
                    return true;
                }
                return false;
            }
        });

        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchInput.setText("");
                searchResults.setVisibility(View.GONE);
            }
        });

        loadCities(true);
    }

    private void loadCities(boolean fetchTemps) {
        cities = store.loadCities();
        renderCities();
        if (fetchTemps) fetchTemps();
    }

    private void fetchTemps() {
        if (cities == null || cities.isEmpty()) return;
        final List<City> snapshot = cities;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<WeatherApi.CityTemp> temps = WeatherApi.fetchCurrentTemps(snapshot);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        for (WeatherApi.CityTemp t : temps) {
                            if (t.ok) tempMap.put(t.cityId, t);
                        }
                        if (cities != null && cities.equals(snapshot)) {
                            renderCities();
                        }
                    }
                });
            }
        }).start();
    }

    // ---------- 城市列表 ----------

    private void renderCities() {
        cityList.removeAllViews();
        String selectedId = store.getSelectedCityId();
        for (final City c : cities) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_city_row);
            row.setPadding(dp(14), dp(12), dp(10), dp(12));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = dp(8);

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);

            TextView name = new TextView(this);
            name.setText((c.isLocated ? "📍 " : "") + c.name);
            name.setTextColor(0xFF222222);
            name.setTextSize(15.5f);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            info.addView(name);

            if (c.detail != null && !c.detail.isEmpty()) {
                TextView detail = new TextView(this);
                detail.setText(c.detail);
                detail.setTextColor(0xFF9AA4B2);
                detail.setTextSize(11.5f);
                info.addView(detail);
            }
            row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            // 当前温度
            WeatherApi.CityTemp t = tempMap.get(c.id);
            if (t != null && !Double.isNaN(t.temp)) {
                LinearLayout tempCol = new LinearLayout(this);
                tempCol.setOrientation(LinearLayout.HORIZONTAL);
                tempCol.setGravity(Gravity.CENTER_VERTICAL);
                ImageView icon = new ImageView(this);
                tempCol.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
                icon.setImageResource(IconMapper.iconRes(t.icon));
                TextView temp = new TextView(this);
                temp.setText(FormatUtil.tempWithUnit(t.temp));
                temp.setTextColor(0xFF222222);
                temp.setTextSize(17);
                temp.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                tlp.leftMargin = dp(6);
                tempCol.addView(temp, tlp);
                row.addView(tempCol);
            } else {
                TextView loading = new TextView(this);
                loading.setText("···");
                loading.setTextColor(0xFFB5BCC7);
                loading.setTextSize(15);
                row.addView(loading);
            }

            // 选中标记
            if (c.id.equals(selectedId)) {
                row.setBackgroundColor(0xFFEAF3FE);
            }

            // 删除
            ImageButton del = new ImageButton(this);
            del.setImageResource(R.drawable.ic_delete);
            del.setColorFilter(0xFFB5BCC7);
            del.setBackground(null);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(34), dp(34));
            dlp.leftMargin = dp(8);
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmDelete(c);
                }
            });
            row.addView(del, dlp);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    store.setSelectedCityId(c.id);
                    setResult(RESULT_OK);
                    finish();
                }
            });

            cityList.addView(row, rlp);
        }

        // 空态提示
        if (cities.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无城市，请通过上方搜索添加\n或点击右上角定位添加当前城市");
            empty.setTextColor(0xFF9AA4B2);
            empty.setTextSize(13);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(40), 0, 0);
            cityList.addView(empty);
        }
    }

    private void confirmDelete(final City c) {
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle(getString(R.string.remove_city));
        b.setMessage(c.name);
        b.setPositiveButton(R.string.ok, new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                List<City> all = store.loadCities();
                for (City x : all) {
                    if (x.id.equals(c.id)) {
                        all.remove(x);
                        break;
                    }
                }
                store.saveCities(all);
                // 删除的正好是当前城市时，同步修正选中项，避免残留无效 id
                if (c.id.equals(store.getSelectedCityId())) {
                    store.setSelectedCityId(all.isEmpty() ? "" : all.get(0).id);
                }
                tempMap.remove(c.id);
                loadCities(false);
            }
        });
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    // ---------- 搜索 ----------

    private void doSearch() {
        final String q = searchInput.getText().toString().trim();
        if (q.isEmpty()) return;
        searchResults.setVisibility(View.VISIBLE);
        searchResults.removeAllViews();
        TextView tip = new TextView(this);
        tip.setText(getString(R.string.loading));
        tip.setTextColor(0xFF9AA4B2);
        tip.setTextSize(13);
        tip.setPadding(dp(10), dp(10), dp(10), dp(10));
        searchResults.addView(tip);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<City> results = WeatherApi.searchCities(q);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        renderSearchResults(results);
                    }
                });
            }
        }).start();
    }

    private void renderSearchResults(List<City> results) {
        searchResults.removeAllViews();
        if (results == null || results.isEmpty()) {
            TextView none = new TextView(this);
            none.setText(getString(R.string.no_results));
            none.setTextColor(0xFF9AA4B2);
            none.setTextSize(13);
            none.setPadding(dp(10), dp(10), dp(10), dp(10));
            searchResults.addView(none);
            return;
        }
        for (final City c : results) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(11), dp(12), dp(11));
            row.setBackgroundResource(R.drawable.bg_city_row);

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView name = new TextView(this);
            name.setText(c.name);
            name.setTextColor(0xFF222222);
            name.setTextSize(14.5f);
            col.addView(name);
            if (c.detail != null && !c.detail.isEmpty()) {
                TextView d = new TextView(this);
                d.setText(c.detail);
                d.setTextColor(0xFF9AA4B2);
                d.setTextSize(11);
                col.addView(d);
            }
            row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView add = new TextView(this);
            add.setText("+ 添加");
            add.setTextColor(0xFF3D7DC4);
            add.setTextSize(13.5f);
            row.addView(add);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    addCity(c);
                }
            });
            searchResults.addView(row);
            View line = new View(this);
            line.setBackgroundColor(0xFFEDF0F3);
            searchResults.addView(line, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
    }

    private void addCity(City c) {
        List<City> all = store.loadCities();
        // 去重（同坐标附近）
        for (City x : all) {
            if (Math.abs(x.lat - c.lat) < 0.01 && Math.abs(x.lon - c.lon) < 0.01) {
                Toast.makeText(this, "城市已存在", Toast.LENGTH_SHORT).show();
                store.setSelectedCityId(x.id);
                setResult(RESULT_OK);
                finish();
                return;
            }
        }
        all.add(c);
        store.saveCities(all);
        store.setSelectedCityId(c.id);
        Toast.makeText(this, "已添加 " + c.name, Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    // ---------- 定位 ----------

    private void requestLocate() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                }, REQ_LOCATION);
                return;
            }
        }
        doLocate();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            for (int g : grantResults) {
                if (g == PackageManager.PERMISSION_GRANTED) {
                    doLocate();
                    return;
                }
            }
            Toast.makeText(this, R.string.locate_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void doLocate() {
        if (locating) return; // 防止连点导致重复定位、监听器堆积
        locating = true;
        Toast.makeText(this, "正在定位…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                double[] last = Locator.lastKnown(CityListActivity.this);
                if (last != null) {
                    resolve(last[0], last[1]);
                    return;
                }
                // Locator 回调统一在后台线程执行，可安全做网络请求
                Locator.requestOnce(CityListActivity.this, 12000, new Locator.Callback() {
                    @Override
                    public void onLocation(final double lat, final double lon) {
                        resolve(lat, lon);
                    }

                    @Override
                    public void onFailed(final String reason) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                locating = false;
                                Toast.makeText(CityListActivity.this, "定位失败：" + reason, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            }

            private void resolve(double lat, double lon) {
                City resolved = WeatherApi.reverseGeocode(lat, lon);
                if (resolved == null) {
                    resolved = new City();
                    resolved.name = "当前位置";
                    resolved.lat = lat;
                    resolved.lon = lon;
                    resolved.isLocated = true;
                }
                final City fCity = resolved;
                // 替换已有定位城市
                List<City> all = store.loadCities();
                for (int i = 0; i < all.size(); i++) {
                    if (all.get(i).isLocated) {
                        fCity.id = all.get(i).id;
                        all.remove(i);
                        break;
                    }
                }
                fCity.isLocated = true;
                all.add(0, fCity);
                store.saveCities(all);
                store.setSelectedCityId(fCity.id);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        locating = false;
                        if (isFinishing() || isDestroyed()) return;
                        setResult(RESULT_OK);
                        finish();
                    }
                });
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 页面销毁时取消定位监听
        Locator.cancelActive();
        locating = false;
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
