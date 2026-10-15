# 纯净天气 CleanWeather · 从需求到 APK 的完整实战

> 一个用 AI（TRAE）从零构建的安卓天气 App，完整复刻 MSN 天气的核心功能，
> 但去掉了所有广告、资讯流和冗余导航。
>
> **本项目同时是一份「面向 AI 构建初学者」的教学样本** —— 从一句模糊的需求，
> 到需求拆解、方案论证、接口逆向、工程实现、签名打包，全流程记录在案。

---

## 目录

- [0. 先看结果](#0-先看结果)
- [1. 需求分析](#1-需求分析)
- [2. 方案分析：WebView vs 原生](#2-方案分析webview-vs-原生)
- [3. 目标网站功能与接口逆向分析](#3-目标网站功能与接口逆向分析)
- [4. 技术选型与架构设计](#4-技术选型与架构设计)
- [5. 具体实现](#5-具体实现)
- [6. 构建、签名与发布](#6-构建签名与发布)
- [7. 踩坑记录与解决方案](#7-踩坑记录与解决方案)
- [8. 安装与使用](#8-安装与使用)
- [9. 面向 AI 构建初学者的经验总结](#9-面向-ai-构建初学者的经验总结)
- [10. 修改历史（Changelog）](#10-修改历史changelog)
- [11. 免责声明](#11-免责声明)

---

## 0. 先看结果

| 项目 | 说明 |
| --- | --- |
| 应用名 | 纯净天气（CleanWeather） |
| 包名 | `com.cleanweather.app` |
| 版本 | 1.0.3（versionCode 4） |
| 产物 | `dist/CleanWeather-1.0.3-release.apk`（已签名） |
| 最低版本 | **Android 5.0（API 21）** |
| 目标版本 | Android 14（API 34） |
| 第三方依赖 | **零依赖**（纯 Java + Android 原生 API） |
| 联网协议 | HTTPS，无鉴权、无密钥硬编码以外的埋点 |
| 定位 | 系统 `LocationManager`，**不依赖 Google 服务**，国内设备可用 |

**一句话总结**：不把网页塞进 WebView，而是直接调用 MSN 天气后端的公开接口，
把 JSON 解析成数据模型，再用纯原生控件 + 自绘 View 重新渲染。
因此天然没有广告、没有资讯流、没有弹窗，体积小、启动快、可控性强。

---

## 1. 需求分析

### 1.1 用户原始需求（原话拆解）

用户给了一个 MSN 天气的链接，并提出：

> "分析它跟天气相关的所有功能，还有 UI 设计……开发一个安卓 APP，
> 有精确的定位功能……使用这个网站的 API 去伪造 API 得到数据，
> 解析后渲染出来……功能要全面，每小时预报这种二级页面也要实现……
> 打开地图这种复杂功能可以暂时不写……
> **或者**，直接把网页嵌入 WebView 也行，但要清理广告/资讯/导航栏……
> **最后我只需要一个 APK 文件**，注意签名和 API 兼容性，尽量兼容老设备。"

### 1.2 需求拆解

把这段话翻译成可执行的任务清单：

| 编号 | 需求 | 归类 | 交付物 |
| --- | --- | --- | --- |
| R1 | 分析目标网站的所有天气功能 | 调研 | 功能清单 |
| R2 | 分析目标网站的 UI 设计 | 调研 | 视觉规范 |
| R3 | 精确/可用的定位能力 | 功能 | `Locator` 模块 |
| R4 | 对接网站 API，解析并渲染 | 功能 | 数据层 + UI 层 |
| R5 | 二级页面（每小时预报等） | 功能 | 多 Activity |
| R6 | 剔除广告、资讯、弹窗、导航栏 | 体验 | 清爽 UI |
| R7 | 最终交付已签名的 APK | 交付 | `app-release.apk` |
| R8 | 兼容老设备 | 兼容 | `minSdk 21` |

> **初学者提示**：AI 时代最重要的能力之一，就是**把一句人话拆成结构化清单**。
> 上面这张表就是"翻译"过程。拆得越细，后面 AI 越不容易跑偏。

### 1.3 关键约束与判断

- **"只给一个 APK"** → 意味着不需要上架商店，自签名即可；也意味着代码要能一键构建。
- **"兼容老设备"** → `minSdk` 尽量低。选 21（Android 5.0）是性价比最高的分界线：
  再低会踩到大量 API 差异，再高会抛弃 2015 年前后的老机器。
- **"打开地图可以不做"** → 明确划出范围边界，这非常重要：**AI 需要知道"不做什么"**。
- **"有不确定的点随时提问"** → 这是一个强烈信号：允许在关键决策点暂停、与用户对齐。

---

## 2. 方案分析：WebView vs 原生

用户给了两条路。这是整个项目**最关键的一次技术决策**，也是初学者最值得学习的思维过程。

### 2.1 方案 A —— WebView 嵌入网页

**思路**：App 里放一个全屏 `WebView` 加载 MSN 天气页面，
用注入 JS 的方式删掉广告、资讯流、顶部导航；再在 WebView 外层套一个自定义工具栏。

**优点**
- 开发快，几乎"零接口分析"。
- 网页改版后，稍微改选择器就能跟上。

**缺点（致命）**
- **DOM 结构随时可能变**，注入脚本依赖 class/id，网站一改版就打回原形。
- **性能差**：加载完整网页 + JS 框架，低端机卡顿、耗电、启动慢。
- **难以彻底清爽**：广告/资讯往往是异步加载的，删了还会重新插入，需要反复"守株待兔"。
- **无法离线**：没有网络就是一整页报错。
- 体积大：塞进完整网页运行时后，包体和内存占用都明显上升。

### 2.2 方案 B —— 原生调用 API + 自绘 UI

**思路**：抓包找到网站背后的天气接口（JSON），直接 `HttpURLConnection` 调用，
解析成数据模型，用原生控件重新堆出一模一样的卡片式 UI。

**优点**
- **天然无广告**：我们只拿天气数据，页面里那些"资讯/广告"根本不会被请求。
- **性能极佳**：一个请求拿到全部数据，纯原生渲染，115 KB 的体积。
- **完全可控**：UI 想怎么改就怎么改，二级页面随意做。
- **可离线**：把 JSON 存本地，无网也能看上次的数据。
- 兼容性最好：不依赖 WebView 内核版本差异。

**缺点**
- 前期要花时间做**接口逆向**（找到 URL、参数、数据结构）。
- 网站接口如果加鉴权/改字段，需要跟着改。

### 2.3 决策对比表

| 维度 | 方案 A（WebView） | 方案 B（原生 API） |
| --- | --- | --- |
| 开发速度 | 快 | 中（需抓包） |
| 广告清理彻底度 | 一般（异步广告难根除） | **彻底（根本不下发）** |
| 性能 / 耗电 | 差 | **优** |
| 包体积 | 大 | **极小（115 KB）** |
| 离线可用 | 否 | **是** |
| 抗网站改版 | 弱（依赖 DOM） | 中（依赖接口字段） |
| 老设备兼容 | 受 WebView 内核限制 | **好** |
| UI 自由度 | 低 | **高** |

### 2.4 最终选择与理由

**选择方案 B。**

决定性依据来自一次验证：抓包后发现 MSN 天气的接口
（`assets.msn.cn/service/weather/overview`）**完全公开、无需登录、无需鉴权头**，
返回的 JSON 覆盖了当前天气、逐小时、逐日、分钟级降水、生活指数、预警、日月出没等**全部数据**。
既然数据源如此干净，就没有理由再去啃一个充满广告的网页 DOM。

> **初学者提示**：好的技术决策不是"哪个更酷"，而是**先花 10 分钟验证关键假设**
> （这里就是"接口是否公开"），再决策。假设一旦成立，答案几乎自动浮现。

---

## 3. 目标网站功能与接口逆向分析

### 3.1 分析方法

用浏览器的开发者工具（F12）→ Network 面板，筛选 `Fetch/XHR`，
刷新天气页，观察有哪些 XHR 请求；再逐个查看响应体，比对页面上显示的数据，
定位哪条响应提供了哪块内容。

### 3.2 抓到的接口清单

所有天气接口都通过 MSN 的边缘服务 `assets.msn.cn/service` 代理：

| 接口 | 用途 | 关键参数 |
| --- | --- | --- |
| `/weather/overview` | **完整预报**（当前/逐小时/10日/预警/生活指数/日月） | `latitude` `longitude` `units=C` |
| `/weather/current` | **多城市批量当前温度** | `latLongList=lat,lon\|lat,lon\|...` |
| `/weather/locations/search` | **经纬度反查地名** | `lat` `lon` `locale=zh-cn` |
| `/weather/api/v6/Places/AutoSuggest` | **城市搜索（含 307 重定向）** | `q=关键词` `setmkt=zh-cn` |

### 3.3 接口参数详解（以 overview 为例）

```
GET https://assets.msn.cn/service/weather/overview
    ?apikey=<公开 key>
    &ocid=msftweather&cm=zh-cn&it=web&scn=ANON
    &appId=<公开 appId>
    &units=C                 # 摄氏度
    &wrapodata=false         # 不包裹响应
    &cuthour=true            # 只取当前时刻之后的小时数据
    &lifeDays=2&lifeModes=18&includeLifeActivity=true&lifeSubTypes=1,3,4,10,26  # 生活指数
    &insights=65536          # 温度趋势洞察
    &startDate=-1&endDate=%2B9   # 昨天 ~ 未来 9 天
    &latitude=29.388081&longitude=104.925354   # 坐标（6 位小数）
```

> 抓包时看到的 `apikey` / `appId` 是网页端写死在 JS 里的**公开标识**，
> 并非用户密钥。我们在客户端复用了它们，保证与网页返回完全一致的数据。

### 3.4 响应数据结构（缩略）

```
{
  "responses": [ {
    "weather": [ {
      "current":  { temp, cap, icon, feels, dewPt, rh, windSpd, windDir, baro, uv, vis, cloudCover, ... },
      "alerts":   [ { headline, description, severity, ... } ],
      "lifeActivities": [ { type, subType, name, summary, ... } ],
      "daily":    [ { valid, tempHi, tempLo, dayCap, nightCap, day.icon, night.icon, ... } ],
      "hourly":   [ { valid, temp, precip, windSpd, rh, uv, vis, dewPt, baro, cloudCover, icon, ... } ],
      "almanac":  [ { valid, sunrise, sunset, moonPhase } ],
      "nowcasting": { summary, shortSummary, precip: [...] },
      "insights": [ { ... } ]
    } ]
  } ]
}
```

### 3.5 关键发现

1. **无鉴权**：请求头不需要 `Authorization`，不需要 Cookie，只需要 `User-Agent`。
2. **一次请求拿全量**：`overview` 一个接口就覆盖了主页所有卡片，**只需一个网络往返**。
3. **支持批量**：`current` 接口用 `latLongList` 一次查询多个城市温度，城市列表页零额外请求。
4. **有 307 重定向**：`AutoSuggest` 会先返回 307 跳转，客户端必须手动跟随重定向（见踩坑记录）。

---

## 4. 技术选型与架构设计

### 4.1 技术栈

| 层 | 选型 | 理由 |
| --- | --- | --- |
| 语言 | 纯 Java（Java 17 编译） | 无需 Kotlin 依赖，构建链最简单 |
| 构建 | Gradle + AGP 8.7.3 | Android 官方标准 |
| 网络 | `HttpURLConnection` | 系统自带，**零依赖**，可控重定向 |
| JSON | `org.json` | 系统内置，无需 Gson/Moshi |
| UI | 原生 View + 自绘 `Canvas` | 不引入任何 UI 库，体积最小 |
| 存储 | `SharedPreferences` | 城市列表 + 天气 JSON 缓存 |
| 定位 | `android.location.LocationManager` | **不依赖 GMS**，兼容国内设备 |

> **初学者提示**：很多人一上来就 `implementation` 一堆库。但对这种"小工具"类 App，
> 每引入一个库都意味着方法数、体积和兼容性风险。**能用系统自带的，就不要引入第三方**。

### 4.2 工程结构

```
CleanWeather/
├── app/
│   ├── build.gradle                  # 模块构建配置（签名 / minSdk / lint 开关）
│   └── src/main/
│       ├── AndroidManifest.xml       # 权限 + Activity 注册
│       ├── java/com/cleanweather/app/
│       │   ├── MainActivity.java             # 主页（1020 行，卡片渲染核心）
│       │   ├── HourlyDetailActivity.java     # 二级页：每/逐小时曲线（429 行）
│       │   ├── DailyDetailActivity.java      # 二级页：每日详情（325 行）
│       │   ├── CityListActivity.java         # 城市管理（474 行）
│       │   ├── data/
│       │   │   ├── WeatherApi.java           # ★ 接口客户端（含重定向处理）
│       │   │   ├── WeatherData.java          # ★ JSON → 数据模型（358 行）
│       │   │   ├── City.java                 # 城市模型
│       │   │   └── CityStore.java            # 持久化（城市 + 缓存）
│       │   ├── ui/
│       │   │   ├── MetricChartView.java      # 自绘指标曲线图（262 行）
│       │   │   ├── AqiBarView.java           # 空气质量色带
│       │   │   ├── RangeBarView.java         # 每日温度区间条
│       │   │   ├── PullScrollView.java       # 下拉刷新容器
│       │   │   └── IconMapper.java           # 天气码 → 图标 / 渐变色
│       │   └── util/
│       │       ├── Locator.java              # 定位工具
│       │       ├── FormatUtil.java           # 数值格式化
│       │       └── TimeUtil.java             # 时间格式化
│       └── res/
│           ├── layout/  (4 个页面布局)
│           ├── drawable/ (14 套天气矢量图标 + 卡片/按钮背景)
│           └── values/  (colors / strings / styles)
├── build.gradle                      # 顶层：声明 AGP 版本
├── settings.gradle                   # 仓库与模块声明
├── gradle.properties                 # JVM 参数、代理、AndroidX 开关
└── keystore/weather-release.keystore # 签名密钥库
```

### 4.3 分层与数据流

```
 [系统定位 LocationManager]
            │  lat / lon
            ▼
 [WeatherApi.fetchOverviewBody]  ──HTTPS──▶  assets.msn.cn/service/weather/overview
            │  原始 JSON（字符串）
            ├──────────────▶ [CityStore] 存 SharedPreferences（离线缓存）
            ▼
 [WeatherData.parse]  ──▶  WeatherData 数据模型
            ▼
 [MainActivity.render]  ──▶  原生 View + 自绘 View 渲染
            │
            └─ 点击卡片 ──▶ [HourlyDetailActivity / DailyDetailActivity]
```

**设计要点**：把"原始 JSON 字符串"和"解析后的模型"分开保存。
- 原始 JSON 存本地 → 离线可重放；
- 模型用于渲染 → 上层不关心 JSON 结构。
这样即使接口加字段，也只需改 `WeatherData.parse`，UI 层零改动。

---

## 5. 具体实现

### 5.1 数据层

#### `WeatherApi` —— 接口客户端

核心是一个**低层 GET + 手动跟随重定向**方法，因为 `HttpURLConnection` 的
自动重定向对 307/308 支持不完整，且跨域 `Location` 解析需要自己处理：

关键代码 [`WeatherApi.java`](app/src/main/java/com/cleanweather/app/data/WeatherApi.java)：

- `httpGet(url)`：最高跟随 5 次重定向，遇到 `301/302/307/308` 时读取 `Location` 头并重新请求；
  统一设置移动端 `User-Agent` 与 `Accept: application/json`；超时 10s 连接 / 15s 读取。
- `fetchOverviewBody(lat, lon)`：拼装上文 3.3 的全部参数，返回**原始 JSON**（供缓存）。
- `fetchOverview(lat, lon)`：`parse(fetchOverviewBody(...))` 的便捷封装。
- `fetchCurrentTemps(cities)`：用 `latLongList` 批量取多城市当前温度。
- `reverseGeocode(lat, lon)`：经纬度 → 地名（用于"定位到当前位置"）。
- `searchCities(query)`：Bing Places 自动补全，用于添加城市。

每次请求都会带上一个随机生成的 `userId`（`m-<UUID>`）与 `activityId`，
用于埋点字段完整性 —— 这是复刻网页行为的必要细节。

#### `WeatherData` —— JSON 解析模型

这是全项目**最考验耐心**的部分：把 337 KB 的嵌套 JSON 映射成强类型对象。

模型划分（内部类）：

| 模型 | 含义 | 关键字段 |
| --- | --- | --- |
| `Current` | 当前天气 | temp / feels / dewPt / uv / vis / windSpd / baro / rh / cloudCover / aqi / icon / cap |
| `Hour` | 逐小时 | valid / temp / precip / windSpd / rh / uv / dewPt / baro / cloudCover / icon |
| `Daily` | 逐日 | tempHi / tempLo / dayCap / nightCap / dayIcon / nightIcon / uvDesc |
| `Almanac` | 日月 | sunrise / sunset / moonPhase |
| `DayEntry` | 一天聚合 | daily + hourly[] + almanac |
| `Nowcast` | 分钟级降水 | summary / shortSummary / precip[] / minutesBetween |
| `Alert` | 预警 | headline / description / severity / area |
| `LifeIndex` | 生活指数 | type / subType / name / summary / displayName() |

解析入口 `parse(body)` → `parseWeather(obj)`，全程使用 `optXxx(key, default)`
的**防御式读取**，任何字段缺失都不会抛异常，只会拿到默认值 `NaN` / `""` / `-1`。
这是接口数据解析的最佳实践 —— **永远假设字段可能不存在**。

> 验证方式：在 JVM 侧用一份真实的 API 响应（337 KB）直接跑 `WeatherData.parse`，
> 逐项断言 current / 10 天 / 235 小时 / 分钟降水 / 生活指数 / 109 条洞察 / 日出日落，
> 结果 **ALL CHECKS PASSED**。这一步保证了「解析层」在写 UI 之前就是对的。

### 5.2 定位层 `Locator`

不依赖 GMS（Google Play 服务），只用系统 `LocationManager`，因此**在国内华为/小米等
无 Google 服务的老设备上同样可用**。

- `lastKnown(context)`：快速取"最后已知位置"，**10 分钟内的**才算新鲜（`isFresh`）。
- `requestOnce(context, timeoutMs, cb)`：请求一次实时定位，超时则回退到 `lastKnown`，
  再不行才报失败。回调 `onLocation` / `onFailed`。
- provider 选择：`Criteria` 用 `ACCURACY_COARSE`（省电、室内可用），
  取不到则遍历全部 provider 兜底。

主流程：**先渲染缓存 → 后台定位 → 用坐标拉数据 → 刷新 UI**，保证秒开。

### 5.3 持久化 `CityStore`

基于 `SharedPreferences`（`clean_weather`）：

- `cities`：城市列表 JSON（`City.toJson` / `City.fromJson`）
- `selected_city_id`：当前城市
- `cache_<cityId>`：该城市的**原始天气 JSON**，用于离线展示
- `initDefault()`：首次安装默认加入"北京市"，避免空白首页

### 5.4 自绘 UI 组件

为了零依赖又要有"网页级的视觉"，用 `Canvas` 自绘了 4 个关键控件：

#### `MetricChartView` —— 指标曲线图（每小时页核心）

- 平滑贝塞尔曲线（控制点取相邻点中点）+ 曲线下渐变填充
- 网格线、X 轴时间标签、Y 轴数值标签
- **支持触摸选择**：手指滑动时高亮最近的数据点并回调索引，与下方小时列表联动
- 参数化：曲线颜色、数值单位、数据点列表都可换，因此**一个控件复用了 11 种指标**（见 5.6）

关键实现 [`MetricChartView.java`](app/src/main/java/com/cleanweather/app/ui/MetricChartView.java)：

```java
// 贝塞尔平滑：以相邻点中点为锚，画二次曲线
for (int i = 0; i < pts.size() - 1; i++) {
    float midX = (pts[i].x + pts[i+1].x) / 2f;
    curvePath.quadTo(pts[i].x, pts[i].y, midX, (pts[i].y + pts[i+1].y) / 2f);
}
```

#### `AqiBarView` —— 空气质量色带

把 AQI 数值映射到"优→良→轻度→中度→重度→严重"六段色带，并标记当前值位置。

#### `RangeBarView` —— 每日温度区间条

10 天预报里每天一个"温度区间条"：按全局最低/最高温做归一化，
用渐变条表示当天温度跨度，视觉上直接对齐全网页效果。

#### `PullScrollView` —— 下拉刷新

系统 `ScrollView` 在手指下拉时触摸事件会被消费，导致"下拉回调"不可靠。
这里自定义 `PullScrollView`，重写 `onTouchEvent`，
当内容已在顶部且继续下拉超过阈值时触发 `onPullRefresh()`，并做了阻尼与回弹。

### 5.5 主页 `MainActivity`（1020 行）

渲染顺序与卡片清单（完全对齐网页）：

1. **顶部工具栏**：城市名（点击进城市管理）+ 定位按钮 + 刷新按钮
2. **当前天气大卡片**：温度、天气图标、天气描述、今日最高/最低
3. **动态背景**：依据天气码 + 昼夜，用 `ValueAnimator` 平滑过渡渐变背景
4. **天气预警卡片**（有预警才显示，醒目的橙色描边）
5. **分钟级降水**：`nowcasting` 摘要 + 未来 2 小时迷你曲线
6. **每小时预报横滑条**：未来 24 小时的图标 + 温度，点击进入二级页
7. **10 天预报**：每天图标 + 温度区间条 + 天气描述，点击进入每日详情
8. **空气质量卡片**：AQI 数值 + 色带 + 首要污染物
9. **天气详情卡片**：体感/湿度/风速/气压/紫外线/能见度/露点 网格
10. **生活指数卡片**：洗车/运动/感冒/穿衣/洗晒 等
11. **日出日落 + 月相卡片**
12. **温度趋势洞察**：来自 `insights` 的对比文本
13. **页脚**："已更新 HH:mm"

刷新流程（`refresh()`）：
- 先用缓存立即渲染 → 后台线程请求 → `runOnUiThread` 回主线程渲染
- 成功后 `saveWeatherCache` 落盘
- 失败时：若已有数据则 `Toast` 提示，否则显示错误遮罩 + 重试按钮
- `refreshing` 标志防止重复请求

### 5.6 二级页：每小时预报 `HourlyDetailActivity`（429 行）

- 顶部一排**指标切换 chip**：温度 / 体感 / 降水 / 风速 / 湿度 / 云量 / 气压 / 紫外线 / 露点 / 能见度
- 中部 `MetricChartView` 绘制 **48 小时**该指标曲线
- 底部小时列表，与图表**双向联动**（图表选中 → 列表高亮；反之亦然）
- 实现细节：构建 `displayHours` 列表，**过滤掉指标为 `NaN` 的小时**，
  保证"图表数据点"与"列表条目"一一对应，避免索引错位（见踩坑记录）

### 5.7 二级页：每日详情 `DailyDetailActivity`（325 行）

展示某一天的完整数据：白天/夜间天气、图标、最高/最低温、
体感、降水、风、湿度、能见度、紫外线描述等。

### 5.8 城市管理 `CityListActivity`（474 行）

- 顶部搜索框 → `searchCities` 实时搜索（Bing Places）
- 搜索结果点击即添加
- 城市列表：每行显示城市名 + **当前温度**（一次 `fetchCurrentTemps` 批量拿）
- 支持删除（长按/按钮）、点击切换为当前城市
- "定位当前位置"：`Locator` + `reverseGeocode`，把 GPS 坐标变成"市 · 区"的可读地名
- 返回结果通过 `onActivityResult` 通知主页刷新

### 5.9 图标与视觉系统

- **14 套矢量天气图标**（纯 XML `vector`）：晴 / 多云间晴 / 朦胧 / 多云 / 阴 / 雾 /
  阵雨 / 雷雨 / 雨 / 小雪 / 雪 / 冻雨 / 晴夜 / 多云夜 / 阴夜。
- `IconMapper` 负责两件事：
  1. **天气码 → 图标资源**：兼容 AccuWeather 码段（1 晴，2-4 多云间晴，…，29-32 夜间码）
  2. **天气码 + 昼夜 → 背景渐变**：晴天蓝、雨灰蓝、雪冷灰、夜间深蓝、雷雨墨绿等
- 主题：`Theme.Material.NoActionBar` + 透明状态栏，沉浸式全屏渐变背景。

---

## 6. 构建、签名与发布

### 6.1 环境要求

| 组件 | 版本 |
| --- | --- |
| JDK | 17（构建用） |
| Android Gradle Plugin | 8.7.3 |
| Gradle | 8.x |
| Android SDK | compileSdk 34 / buildTools 34 |

### 6.2 关键构建配置 [`app/build.gradle`](app/build.gradle)

```gradle
android {
    namespace 'com.cleanweather.app'
    compileSdk 34
    defaultConfig {
        applicationId "com.cleanweather.app"
        minSdk 21           // ★ 兼容 Android 5.0+
        targetSdk 34
        versionCode 4
        versionName "1.0.3"
    }
    signingConfigs {
        release {
            storeFile file("../keystore/weather-release.keystore")
            storePassword "cleanweather2026"
            keyAlias "weather"
            keyPassword "cleanweather2026"
        }
    }
    buildTypes {
        release { minifyEnabled false; shrinkResources false; signingConfig signingConfigs.release }
        debug   { signingConfig signingConfigs.release }
    }
    lint { checkReleaseBuilds false; abortOnError false }  // ★ 规避 JDK 新版下 lint 崩溃
}
```

`gradle.properties` 里关闭 AndroidX（本工程未用到）：
`android.useAndroidX=false`、`android.nonTransitiveRClass=false`。

### 6.3 签名

使用自签名密钥库 [`keystore/weather-release.keystore`](keystore/weather-release.keystore)：

| 项 | 值 |
| --- | --- |
| 类型 | PKCS12 |
| 别名 | `weather` |
| 口令 | `cleanweather2026` |
| 算法 | SHA384withRSA / 2048-bit |
| 有效期 | 至 2056-10-06（约 30 年） |
| 主体 | `CN=CleanWeather, OU=Weather, O=CleanWeather, L=Beijing, ST=Beijing, C=CN` |

同时启用 **v1 + v2 签名**，保证 Android 5.0 ~ Android 14 全段可安装。

### 6.4 构建命令

```bash
cd CleanWeather
./gradlew assembleRelease
```

（若无 wrapper，可用本机 `gradle assembleRelease`。）

### 6.5 产物

```
app/build/outputs/apk/release/app-release.apk     # 约 115 KB，已签名
```

校验签名：

```bash
jarsigner -verify app-release.apk     # → "jar verified."
```

---

## 7. 踩坑记录与解决方案

这一节对初学者尤其有价值 —— **AI 写代码不会一次成功，关键是会定位、会修复**。

| # | 问题 | 现象 | 解决方案 |
| --- | --- | --- | --- |
| 1 | 解析层类型错误 | `WeatherData.parse` 中把 `JSONArray` 变量声明成了 `JSONObject`，编译失败 | 修正变量类型；养成"逐字段核对类型"的习惯 |
| 2 | Gradle 下载失败 | 依赖拉取超时 | 在 `gradle.properties` 配置 HTTP/HTTPS 代理指向本地端口 |
| 3 | lint 任务崩溃 | Gradle 8.14 + 新版 JDK 下 `lint` 任务直接崩 | `lint { checkReleaseBuilds false; abortOnError false }` 跳过 release lint |
| 4 | 下拉刷新失效 | 包装的 `ScrollView` 触摸事件返回 `false`，回调收不到 | 自研 `PullScrollView`，重写 `onTouchEvent` 实现可靠触发 |
| 5 | 图表与列表错位 | `NaN` 数据点导致图表索引与小时列表错位 | 引入 `displayHours` 列表，过滤 `NaN` 后保持一一对应 |
| 6 | 307 重定向 | `AutoSuggest` 返回 307，默认不跟随 | `httpGet` 手动读取 `Location` 头并重发请求（最多 5 跳） |
| 7 | 老机定位无响应 | 无 GMS 设备上定位为空 | 改用 `LocationManager`，并用 `lastKnown` 兜底 |
| 8 | 定位回调在主线程做网络请求 | **点击定位后立即闪退**（`NetworkOnMainThreadException`） | `Locator` 把所有回调统一投递到后台单线程执行器，调用方拿到的回调一定不在主线程 |
| 9 | 定位监听器堆积 | **连续点击定位几次后界面卡死**（ANR） | 新增 `Locator.cancelActive()` 全局取消 + `minTime=1000ms` + 页面 `locating` 防重 + `onDestroy()` 移除监听 |
| 10 | 启动不申请定位权限 | **首次安装无权限弹窗、权限默认关闭**，无法定位 | `ensureLocationPermissionOnStart()` 延迟到界面布局完成后申请；用 `location_asked` 持久化标记避免反复打扰 |
| 11 | 缺失数据被四舍五入成 0 | 指标缺失（`NaN`）时显示 `0` / `0°` / `0%`，像真实数据 | `FormatUtil.missing()` 判定，`NaN` / `Infinity` 统一返回 `--` 占位 |
| 12 | 删除当前城市后选中残留 | 删除正在查看的城市后，主页面落到一个不存在的城市 id | 删除时同步修正 `selected_city_id`（切到剩余首个城市）并清理温度缓存 |
| 13 | **渲染阶段空指针闪退（关键）** | **天气数据加载成功后 App 立刻闪退**，日志为 `NullPointerException` | `MainActivity.bindViews()` 漏绑定了布局中的 `R.id.footer`，而 `render()` 末尾调用了 `footer.setText(...)`；补上 `footer = findViewById(R.id.footer)` 并加判空防御 |

### 7.1 关键闪退的定位过程（第 13 条）

这是本项目最典型的一次"**静态排查**"实战。当无法连接设备抓日志时，可以按下面的顺序自行排查：

1. **看崩溃发生的时机**：现象是"数据加载成功后闪退"，说明崩溃点一定在渲染链路（`render()`）而不是网络链路（网络异常会走 `catch`，不会崩）。
2. **锁定可疑的 View 字段**：逐一核对 `bindViews()` 里绑定了哪些 `R.id.*`，再和布局 `activity_main.xml` 里的 `@+id` 对照。
3. **发现缺口**：布局里有 `@+id/footer`，代码里声明了 `private TextView footer;`，但 `bindViews()` **从未**执行 `footer = findViewById(R.id.footer)`。
4. **确认崩点**：`render()` 最后一行 `footer.setText(...)` 作用在一个 `null` 上 → `NullPointerException` → 主线程崩溃。

**修复**：补上 `footer = findViewById(R.id.footer);`，并加 `if (footer != null)` 判空，防止未来布局改动再次引入同类问题。

### 一条通用经验

> **先让"数据"正确，再让"界面"好看。**
> 本项目在写任何 UI 之前，先在 JVM 侧把真实 JSON 解析跑通并逐项断言。
> 这样后面 UI 出问题时，可以确信"不是数据的锅"，排查范围直接缩小一半。

---

## 8. 安装与使用

1. 把 `app-release.apk` 传到手机，点击安装；
2. 首次安装如提示"未知来源"，在系统设置中允许该来源安装；
3. 首次启动默认显示北京市，点击右上角**定位图标**获取当前城市；
4. 点击**城市名**进入城市管理，可搜索添加 / 删除 / 切换城市；
5. 下拉页面刷新；点击"每小时预报"或某一天进入二级详情页。

权限说明（仅 4 项，且都是必要的）：

| 权限 | 用途 |
| --- | --- |
| `INTERNET` | 请求天气接口 |
| `ACCESS_FINE_LOCATION` | 精确定位 |
| `ACCESS_COARSE_LOCATION` | 粗略定位（省电/室内） |
| `ACCESS_NETWORK_STATE` | 判断网络状态 |

---

## 9. 面向 AI 构建初学者的经验总结

这是本项目最想传递的东西 —— **AI 不是"许愿机"，而是一个需要你引导的强力执行者**。

### 9.1 需求阶段

- **把"人话"翻译成结构化清单**（见 1.2 表格）。清单越具体，AI 越不容易跑偏。
- **明确"不做什么"**。用户说"地图可以先不做"，这一句帮项目省下了大量无效工作。
- **允许 AI 提问**。用户主动说"有不确定的点随时问我"，使关键决策点能及时对齐。

### 9.2 方案阶段

- **先验证关键假设，再决策**。本项目 10 分钟抓包，就决定了技术路线。
- **用对比表代替"感觉"**。把 WebView 与原生按多个维度列表，优劣一目了然。
- **别被"简单方案"迷惑**。WebView 看似简单，实则后期维护成本更高。

### 9.3 实现阶段

- **自底向上**：先数据层（能正确解析）→ 再 UI 层（能正确渲染）。
- **每写一层就验证一层**：解析层用真实响应跑断言；UI 层逐个卡片核对。
- **零依赖优先**：能系统自带就自带，体积和兼容性都会更好。
- **防御式解析**：所有字段都用 `opt` 带默认值，接口改字段也不会崩。
- **保留原始数据**：原始 JSON 落盘 → 离线可用 + 便于复现问题。

### 9.4 交付阶段

- **兼容性是设计出来的**：`minSdk 21` + 不用 GMS + 纯原生控件 = 真正的老机友好。
- **签名是必需品**：哪怕只是自己装，也要配好 keystore（本项目配置了 30 年有效期）。
- **给出可复现的构建命令**，让任何人都能一键重建 APK。

### 9.5 推荐的工作节奏（可复用的提示词模板）

```
1. 调研：请分析 <目标网站> 中与 <主题> 相关的功能、UI 设计和背后的接口。
2. 决策：请给出两种以上实现方案的对比，并推荐一种，说明理由。
3. 拆解：请把推荐方案拆成任务清单，标注依赖关系。
4. 实现：请实现 <某模块>，要求 <具体约束>，并在完成后自我验证。
5. 验证：请用真实数据验证 <某模块> 的正确性。
6. 交付：请构建并签名，产出最终 APK。
```

---

## 10. 修改历史（Changelog）

> 本节记录每一次版本迭代中的 Bug 修复与改进，方便回溯与学习。
> 每一个条目都写清了「现象 / 根因 / 修复」，这正是初学者最该积累的部分。

### v1.0.3（versionCode 4）— 定位精度、搜索体验与设置中心

**发布日期**：2026-10-15

本次为功能增强版本，围绕「定位更准、搜索更顺、可配置、可自更新」四件事展开。

#### ✨ 新增 1：自动定位到「最近的可添加地址」（道路 / 乡镇级）

- **现象**：自动定位最多只能到「市 / 区」（如仅能定位到「大安区」），而用户在 MSN 上
  可以搜索到「大安区何市镇」「太大路」「何市客运站」等道路级地点，却必须手动输入。
- **根因**：反向地理编码默认只取 1 条粗粒度结果（`Postcode1` / 行政区），未请求细粒度实体。
- **修复**：改用 `/weather/locations/search` 接口并传入 `count=8`，反查时**优先取非行政区类型**
  （`Address` / `RoadBlock` / `PointOfInterest`）；若单点反查未命中细粒度实体，则在
  120m / 250m / 500m / 900m 四个半径、8 个方向做**环形采样反查**，取距离最近的道路 / 门牌 / 地点。
  天气坐标仍使用用户真实经纬度，避免被「行政区中心点」带偏。
- **涉及文件**：[WeatherApi.java](app/src/main/java/com/cleanweather/app/data/WeatherApi.java)

#### ✨ 新增 2：搜索体验对齐 MSN（输入即联想）

- **现象**：原搜索只能搜索到城市 / 区县，且需要回车确认，体验与 MSN 不一致。
- **修复**：搜索实现由 AutoSuggest 切换到 `/weather/locations/search?query=`，可返回道路 / 地点级实体；
  新增 **300ms 防抖输入联想**（边打字边搜）与**过期响应丢弃**（只渲染与当前输入一致的请求结果）。
- **涉及文件**：[CityListActivity.java](app/src/main/java/com/cleanweather/app/CityListActivity.java)、
  [WeatherApi.java](app/src/main/java/com/cleanweather/app/data/WeatherApi.java)

#### ✨ 新增 3：设置中心（[SettingsActivity.java](app/src/main/java/com/cleanweather/app/SettingsActivity.java)）

- **自动检查升级**：开关默认开启，每次启动自动检测新版本；
- **权限管理**：通知权限、精准定位权限的获取与状态展示，被拒绝时引导至系统设置；
- **手动检查升级**：一键立即检查；
- **版本信息**：软件名 + 版本号；
- **分享此应用**：调用系统分享（微信 / QQ 等）发送软件介绍与最新版本下载地址；
- **关于作者**：作者「繁寂」与 GitHub 项目地址（图标 + 地址，点击调用浏览器打开）。

#### ✨ 新增 4：自动更新（检查 / 提示 / 后台下载 / 安装）

- 启动时若开启自动检查升级，则拉取 `github.com/fanji-jared/CleanWeather/releases` 的最新版本号与说明，
  与本地版本比较；有新版本则弹框展示更新说明（超长自动截断），可**后台更新**或**取消**。
- 后台更新由前台服务 [UpdateService.java](app/src/main/java/com/cleanweather/app/update/UpdateService.java)
  下载最新 APK，**通知栏实时显示进度**；下载完成调起系统安装器，安装成功后再次打开应用会**清理已下载的更新包**；
  下载失败同样在通知栏提示并删除不完整的分包。
- 版本检查优先走 GitHub API，被限流时回退解析 `releases.atom` 订阅源（[UpdateChecker.java](app/src/main/java/com/cleanweather/app/util/UpdateChecker.java)）。

#### 🐞 修复 1：主页顶部存在多余高度

- **现象**：最顶部组件与屏幕实际顶部之间有一块多余空隙。
- **根因**：页面在已由系统处理状态栏内边距的情况下，又手动叠加了 `statusBarHeight` 内边距，形成双重留白。
- **修复**：统一去掉各页面手动叠加的状态栏内边距，改由 [SystemBar.java](app/src/main/java/com/cleanweather/app/util/SystemBar.java)
  负责状态栏配色，使内容紧贴状态栏下方。

#### 🎨 体验优化：下拉刷新与系统栏配色

- 主页新增下拉刷新头部：下拉过程显示刷新图标与「上次更新时间」提示，达到阈值提示「松手刷新」，
  松手后重新拉取数据并渲染，结束后动画收起头部。
- 状态栏取页面顶部色、导航栏取页面底部色，并根据背景明暗自动切换图标深浅，使顶部 / 底部与系统栏浑然一体
  （主页为动态渐变，子页面为固定色）。

#### ✅ 本次验证

- `gradle assembleDebug` / `assembleRelease` → **BUILD SUCCESSFUL**；
- `aapt2 dump badging` → `versionCode=4`、`versionName=1.0.3`、`sdkVersion=21`、`targetSdkVersion=34`；
- `apksigner verify` → 签名校验通过。

---

### v1.0.2（versionCode 3）— 渲染闪退修复

**发布日期**：2026-10-15

本次为 Bug 修复版本，解决用户反馈的「运行后仍然闪退」问题。

#### 🐞 修复 1：天气数据加载成功后闪退（主页渲染空指针，**关键**）

- **现象**：App 能正常打开、也发起了网络请求，但在天气数据**加载成功并开始渲染的一瞬间**立刻闪退；
  由于每次渲染都会触发，表现为「打开后反复闪退 / 像卡死一样」。
- **根因**：`MainActivity` 声明了成员变量 `private TextView footer;`，布局
  [activity_main.xml](app/src/main/res/layout/activity_main.xml) 里也有 `@+id/footer`，
  但 `bindViews()` **漏写了** `footer = findViewById(R.id.footer)`。因此 `footer` 始终为 `null`，
  而 `render()` 的最后一行会执行 `footer.setText("更新于 …")` → `NullPointerException` → 主线程崩溃。
- **涉及文件**：[MainActivity.java](app/src/main/java/com/cleanweather/app/MainActivity.java)、
  [activity_main.xml](app/src/main/res/layout/activity_main.xml)
- **修复**：在 `bindViews()` 中补上 `footer = findViewById(R.id.footer);`；
  并在 `render()` 中对该控件增加 `if (footer != null)` 判空，防止后续布局改动再次引入同类问题。

#### 🛡 其他改进

- 汇总本次一并复核并确认已修复的问题（详见第 7 节踩坑记录第 8~12 条）：
  定位回调线程模型、定位监听器生命周期、首次启动权限申请、缺失数据占位、删除城市后的选中态残留。

#### ✅ 本次验证

- `gradle clean assembleRelease` → **BUILD SUCCESSFUL**；
- `aapt2 dump badging` → `versionCode=3`、`versionName=1.0.2`、`sdkVersion=21`、`targetSdkVersion=34`；
- `jarsigner -verify` → `jar verified.`；`apksigner verify` → **v1 + v2 签名均通过**。

#### 🔎 如何自行抓取崩溃日志（adb）

若再次遇到闪退，可连接设备抓取崩溃堆栈（MuMu 模拟器默认 adb 端口为 `127.0.0.1:16384`，
部分版本为 `7555`）：

```bash
adb connect 127.0.0.1:16384        # MuMu 12；老版本用 adb connect 127.0.0.1:7555
adb devices                        # 确认设备已连接
# 方式一：只看崩溃
adb logcat -b crash
# 方式二：过滤本应用
adb logcat | grep -E "AndroidRuntime|cleanweather"
# 看到 “FATAL EXCEPTION: main” 后面的第一段堆栈即为崩溃根因
```

---

### v1.0.1（versionCode 2）— 稳定性修复

**发布日期**：2026-10-15

本次为纯 Bug 修复版本，重点解决「定位相关闪退 / 卡死」与「首次启动不申请定位权限」两大问题。

#### 🐞 修复 1：点击定位后闪退（`NetworkOnMainThreadException`）

- **现象**：点击右上角定位按钮（或首次授权定位后），App 直接闪退。
- **根因**：`Locator.requestOnce()` 把系统定位监听器注册在主线程 Looper 上，因此 `onLocationChanged()`
  回调运行在**主线程**；而回调里紧接着调用了 `WeatherApi.reverseGeocode()`（HTTP 网络请求），
  在主线程做网络 IO 会抛出 `android.os.NetworkOnMainThreadException`，进程被系统杀掉。
- **涉及文件**：[Locator.java](app/src/main/java/com/cleanweather/app/util/Locator.java)、
  [MainActivity.java](app/src/main/java/com/cleanweather/app/MainActivity.java)、
  [CityListActivity.java](app/src/main/java/com/cleanweather/app/CityListActivity.java)
- **修复**：在 `Locator` 内部把所有回调（`onLocation` / `onFailed`）统一投递到一个**后台单线程执行器**
  上执行。这样无论系统回调来自哪个 Looper，调用方拿到的回调一定不在主线程，可以安全做网络 IO。

#### 🐞 修复 2：连续点击几次后卡死（定位监听器堆积）

- **现象**：反复点击定位按钮数次后，界面卡死无响应（ANR）。
- **根因**：每次点击定位都会 `requestLocationUpdates()` 注册一个**新的**监听器，且 `minTime=0`。
  多个监听器同时在主线程高频回调位置更新，主线程被瞬间刷爆；同时这些监听器在请求结束后也没有被及时移除。
- **涉及文件**：[Locator.java](app/src/main/java/com/cleanweather/app/util/Locator.java)
- **修复**：
  1. `Locator` 新增 `cancelActive()`：发起新定位前先移除上一次的监听器，保证**同一时刻只有一个**活跃监听器；
  2. `minTime` 由 `0` 调整为 `1000ms`，降低回调频率；
  3. `MainActivity` / `CityListActivity` 增加 `locating` 标志位，防止连点重复发起定位；
  4. 两个页面新增 `onDestroy()`，在页面销毁时调用 `Locator.cancelActive()`，避免监听器泄漏。

#### 🐞 修复 3：首次启动不申请定位权限，权限默认关闭

- **现象**：首次安装打开 App 时没有任何权限弹窗，定位权限处于关闭状态，导致无法定位当前城市。
- **根因**：`MainActivity.onCreate()` 从未主动申请过定位权限，只有在用户点击定位按钮时才申请。
- **涉及文件**：[MainActivity.java](app/src/main/java/com/cleanweather/app/MainActivity.java)、
  [CityStore.java](app/src/main/java/com/cleanweather/app/data/CityStore.java)
- **修复**：新增 `ensureLocationPermissionOnStart()`，在启动时通过 `root.post()` 延迟到界面布局完成后再申请权限：
  - 未授权且从未申请过（`LocationAsked` 标记，持久化在 SharedPreferences）→ 弹出系统权限对话框；
  - 已授权且还没有「当前位置」城市 → 自动定位一次；
  - 已授权且已有定位城市 → 不打扰，沿用用户上次选择。
  同时把权限被拒绝时的提示改为 `grant_location`（引导用户去授权），而不是笼统的「定位失败」。

#### 🐞 修复 4：缺失数据显示成 "0"（假数据）

- **现象**：接口某些指标缺失（`NaN`）时，界面会显示 `0`、`0°`、`0%`，看起来像真实数据，容易误导。
- **根因**：`FormatUtil` 直接对 `NaN` 做 `Math.round()`，结果恒为 `0`。
- **涉及文件**：[FormatUtil.java](app/src/main/java/com/cleanweather/app/util/FormatUtil.java)
- **修复**：新增 `missing()` 判定，`temp / tempWithUnit / speed / percent / km / mm`
  遇到 `NaN` 或 `Infinity` 统一返回 `--` / `--°` 占位。

#### 🐞 修复 5：删除当前城市后选中状态残留

- **现象**：在城市管理里删掉「当前正在查看的城市」后，主页面可能落到一个不存在的城市 id 上。
- **根因**：删除城市时只移除了列表项，没有同步修正 `selected_city_id`；且 `tempMap` 中残留旧温度。
- **涉及文件**：[CityListActivity.java](app/src/main/java/com/cleanweather/app/CityListActivity.java)
- **修复**：删除后若被删城市正是当前选中城市，则自动切换到剩余城市的第一个（列表为空则清空选中 id），
  并同步清理 `tempMap` 缓存。

#### 🛡 其他改进（防御性加固）

- `MainActivity.renderCity()` / `refresh()` 增加 `city == null` 判空，避免极端情况下空指针。
- `resolveCity()` / `CityListActivity.doLocate()` 在回到主线程更新 UI 前增加 `isFinishing()` / `isDestroyed()` 判断，
  避免向已销毁页面写数据。
- 版本号提升至 `versionCode 2` / `versionName 1.0.1`。

#### ✅ 本次验证

- `gradle clean assembleRelease` **BUILD SUCCESSFUL**，无编译错误；
- `jarsigner -verify` → `jar verified.`；
- `apksigner verify` → **v1 + v2 签名均验证通过**；
- `aapt2 dump badging` 确认：`versionCode=2`、`sdkVersion=21`、`targetSdkVersion=34`。

---

### v1.0.0（versionCode 1）— 首次发布

**发布日期**：2026-10-14

- 基于 MSN 天气公开接口，完成原生安卓天气 App 的首个可用版本。
- 功能：当前天气、分钟级降水、24 小时预报、10 天预报、空气质量、天气详情、生活指数、
  日出日落月相、温度趋势洞察；二级页（每小时曲线 / 每日详情）；城市搜索与管理；离线缓存。
- 技术：纯 Java 零依赖、minSdk 21、自绘曲线/色带/温度区间条、系统 LocationManager 定位。

---

## 11. 免责声明

- 本项目为**学习与技术研究目的**的个人练手项目，**非商业用途**。
- 天气数据版权归原始数据提供方与 MSN 所有；App 仅作为客户端进行展示。
- 项目复用了网页端公开的接口标识（apikey / appId），未破解任何付费能力或私有数据。
- 请勿用于任何商业分发或违反目标网站服务条款的场景。
- 图标为自行绘制的矢量图，未使用任何第三方受版权保护的素材。

---

> **项目状态**：功能已全部实现并构建完成，产物 `app-release.apk`（约 115 KB）已签名可用。
> 适用于 Android 5.0（API 21）及以上设备。
