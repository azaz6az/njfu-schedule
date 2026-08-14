# v0.5.0 设计：广西大学导入 + 小组件添加引导 + 小组件 A3 体系

日期：2026-08-14
状态：已获用户批准（设计 v1，含三项决策确认）

## 背景与目标

南林课程表 App（com.schedule.njfu，minSdk 26 / targetSdk 35，Kotlin + Compose）当前仅支持南林教务导入，桌面小组件为 2×2 今日课程 + 4×3 本周网格。本次发布 v0.5.0 三个目标：

1. **广西大学课表/考试导入**：新增学校适配器，覆盖正方 jwglxt 新版教务（jwxt2018.gxu.edu.cn）。
2. **小组件添加引导**：设置页内嵌"如何把小组件加到桌面"图文引导，含厂商差异。
3. **小组件 A3 方案**：极简框架（4×1 下一节课 / 2×2 今日 / 4×2 今日列表）+ 2×2 考试倒计时 + 3 套主题，全部保持 RemoteViews 实现（Glance 在 Android 16 有兼容问题，已弃用）。

## 已确认的决策（用户拍板）

- 4×2 今日列表底部保留"考试倒计时"小字行。
- 小组件主题由用户在设置页自行选择（默认初始值莫兰迪纸感，不强制首启引导）。
- 学年学期推导失败时，由用户手动选择（向导内下拉/弹窗），不静默猜测。

## 第 1 节：广西大学课表/考试导入

### 登录流程（与南林同构，更简单）

- WebView 打开 `https://jwxt2018.gxu.edu.cn/jwglxt/xtgl/login_slogin.html`（正方标准登录页，用户输学号密码，可能含验证码/滑块，由用户在 WebView 内手动完成）。
- 登录成功后 jwglxt 下发 `JSESSIONID` 会话 Cookie；WebView 页面上 Cookie 含 `JSESSIONID` 即视为登录成功，回传 Cookie 字符串。
- 之后 OkHttp 携带 Cookie 调 JSON 接口（教务只拒绝非浏览器客户端的登录落地，普通页面/API 请求带会话 Cookie 可访问——与南林同构的既有结论）。

### 接口

- 课表：`POST https://jwxt2018.gxu.edu.cn/jwglxt/kbcx/xskbcx_cxXsKb?gnmkdm=N2151`
  - 表单参数：`xnm`（学年，如 2025）、`xqm`（学期：3=秋季 / 12=春季 / 16=夏季）
  - 响应 JSON `kbList[]`，字段（以真机实测为准，兼容变体）：
    - `kcmc` 课程名称、`xm` 教师、`xqj` 星期（1-7）、`jcs` 节次（"1-2"/"01-02" 等变体）、`zcd` 周次（"1-16周"/"1-16" 等变体）、`jsmc`/`jxdd`/`xqmc` 地点校区
- 考试：`POST https://jwxt2018.gxu.edu.cn/jwglxt/kscx_cxXsksxxDg.html?gnmkdm=N358105`
  - 同参 `xnm`/`xqm`，响应 JSON `items[]`：`kssj`（yyyy-MM-dd HH:mm:ss）、`kcmc`、`cdmc`（考场）等 → `Exam`

### 代码结构

- 新增 `importer/gxu/GxuAdapter.kt`（实现 `SchoolAdapter`：`login` / `fetchSchedule` / `fetchExams`）+ `GxuParser.kt`（JSON→Course/Exam 解析，纯函数可单测）。
- `fetchScheduleWithCookies(cookies)` / `fetchExamsWithCookies(cookies)` 供导入向导调用（复用南林 `NjfuAdapter.fetchScheduleWithCookies` 模式）。
- 通用化 `ui/import/CasLoginActivity.kt`：起始 URL、成功域名前缀、成功 Cookie 标志（南林 `bzb_jsxsd` / 西大 `JSESSIONID`）改为 Intent extra 传入；南林参数为默认值，行为不变。
- 学年学期推导：从设置 `semester_start` 推导（9 月起 → xnm=start.year、xqm=3；3 月起 → xnm=start.year-1、xqm=12）；推导失败/用户想改 → 向导内下拉手动选择（学年 + 学期）。
- 导入向导 `ImportWizardScreen.kt` + `ImportViewModel.kt`：
  - 自动导入卡片顶部学校选择（南京林业大学 / 广西大学），SharedPreferences 记住上次选择。
  - 选广西大学时显示"将导入 2025-2026 学年第 1 学期"及可改下拉。
  - 登录回传 Cookie 后按学校分发到对应 Adapter；课表 + 考试一并抓取，走现有"解析 → 周次兜底 → 差异预览 → 确认落库"链路：确认导入时 `ScheduleRepository.replaceAll(courses, exams)` 一并落库（该函数已支持 exams 参数；考试不单独做差异预览，沿用现有行为）。
- 解析细节：`zcd` 去"周"后缀后复用 `WeekUtils.parseWeeksText()`；`jcs` 归一化（"01-02"→1-2）；地点 = 校区/教学楼/教室组合去空。

### 风险

- 接口路径、字段名、学期编码以真机实测为准；README「已知限制」注明（与南林课表页 URL 待实测同级别）。
- 考试接口若改版失败，仅提示考试抓取失败，不阻断课表导入（课表/考试解耦容错）。

## 第 2 节：小组件添加引导（设置页内嵌）

- `SettingsScreen.kt` 新增「桌面小组件」区块（放在「小米设备优化」区块旁），由独立自包含 composable `ui/settings/WidgetGuideSection.kt` 提供，避免与主题选择改动冲突。
- 内容：
  - **通用三步**：① 桌面空白处长按 → ② 选择「小组件 / 窗口小工具 / 添加小部件」→ ③ 找到「今日课程 / 下一节课 / 考试倒计时」拖到桌面。
  - **厂商差异折叠项**（ExpandingCard 风格）：小米/澎湃 OS、华为鸿蒙、OPPO ColorOS、vivo OriginOS、荣耀 MagicOS、三星 One UI、原生 Android——各 2-3 行要点（如小米桌面双指捏合进入添加）。
  - **常见问题折叠项**：添加后不更新（打开一次 App 或点一下小组件）；找不到小组件（在「全部」分类中找「课程表」）。
- 文案数据放 `ui/settings/WidgetGuideData.kt`（纯数据类 + 单测校验文案完整性：每家厂商都有非空标题与步骤、无占位符）。

## 第 3 节：小组件 A3 方案（全部 RemoteViews）

### 小组件矩阵

| 小组件 | 尺寸 | 内容 |
|---|---|---|
| 今日课程（改造现有 ScheduleWidgetProvider） | 2×2 | 表头「周X · 第N周 · N节」+ 课程色块列表（沿用 widget_schedule.xml / widget_course_item.xml 结构，加表头行） |
| 下一节课（新增 NextClassWidgetProvider） | 4×1 | 小字「下一节课 · 第N周 周X」；大字倒计时「距上课 X 分钟 / 上课中 · 距下课 X 分钟 / 今日无课 / 今天课已上完」；第三行「课程名 · 地点」 |
| 今日列表（新增 TodayWidgetProvider） | 4×2 | 表头「周X · 第N周 · N节课」+ 最多 4 行（节次/课程/地点色块）+ 底部考试倒计时小字（距最近考试 X 天） |
| 考试倒计时（新增 ExamCountdownWidgetProvider） | 2×2 | 小字「下一场考试」；大字「还有 X 天」；「课程 · 日期」 |
| 本周网格（改造现有 WeekWidgetProvider） | 4×3 | 应用主题背景/表头配色，课程色块保持课程色 |

- 新增布局：`widget_next.xml`、`widget_today.xml`、`widget_exam.xml`；新增 `xml/next_widget_info.xml`（4×1，targetCellWidth 4 / targetCellHeight 1，resizeMode horizontal）、`xml/exam_widget_info.xml`（2×2）、`xml/today_widget_info.xml`（4×2）。
- AndroidManifest 注册三个新 receiver（现有两个 receiver 模式照抄）。
- 点按行为：全部小组件点按打开 MainActivity；考试倒计时小组件点按后落到「考试」Tab——`MainActivity`/`AppNav` 支持 intent extra（如 `start_tab=exam`）指定初始 Tab。

### 主题系统

- `SettingsKeys.WIDGET_THEME = "widget_theme"`，取值 `morandi`（默认）/ `fresh` / `deep`，设置页「小组件主题」三选一（`ui/settings/WidgetThemeSection.kt` 独立 composable，不与其他改动冲突）。
- 每套主题含浅/深两套色板（背景、卡片、文字、表头、强调色），渲染时按系统 uiMode（`context.resources.configuration.uiMode`）取用——RemoteViews 渲染期算色，兼容所有 launcher 与 Android 版本（minSdk 26 起无需 -night 资源）。
- 主题色板数据结构 `widget/WidgetTheme.kt`（纯数据 + 提取函数，可 JVM 单测：三主题 × 深浅 × 关键角色齐全、对比度达标）。

### 刷新策略

- 保留：数据变更（导入/加课/设置/考试变更）、打开 App、每日 WorkManager 刷新。
- 新增**自适应下一刷新调度**（`widget/WidgetRefreshScheduler.kt`）：
  - 4×1 倒计时在"课前 60 分钟 ～ 当天最后一节课结束后 60 分钟"窗口内，AlarmManager 一次性闹钟**每分钟**刷新；每次更新后计算并重排下一个边界（下一分钟整点）。
  - 窗口外只在：下一次上课前 60 分钟、次日 08:00、数据变更时刷新。
  - 无任何小组件实例（onDisabled / 无 appWidgetId）时取消全部闹钟；有实例时 onUpdate 后确保调度存在。
  - 其他小组件（今日/考试/周网格）不参与分钟级刷新（内容粒度不需要），仍走每日 + 数据变更刷新。
- 倒计时计算逻辑放 `widget/WidgetData.kt`（纯函数：`nextClassState(courses, week, now)` 返回 距上课/上课中/已结束/今日无课 + 剩余分钟 + 下一门课；`nextExamCountdown(exams, now)`），可 JVM 单测（含跨日、无课、全周结束等边界）。

## 第 4 节：配套

- README 更新：功能列表（广西大学导入、小组件矩阵 5 件、主题、引导）、版本历史 v0.5.0、已知限制。
- 版本：versionCode 8 / versionName 0.5.0。
- 测试（全部 JVM 单测，不引入模拟器）：
  - `GxuParserTest`：课表 JSON fixture（含节次/周次变体、空课程、地点组合）、考试 JSON fixture（含空列表）。
  - 学年学期推导（含 3 月/9 月开学、边界、设置缺失→手动选择兜底）。
  - `WidgetDataTest`：倒计时状态机（上课前/中/后、跨日、无课）、考试倒计时（无考试、当天考试、跨年）。
  - `WidgetThemeTest`：色板完整性 + 白字对比度。
  - `WidgetGuideDataTest`：引导文案完整性。
  - 现有 104 个测试保持全绿。

## 实现方式

- 三个并行工作流（互不触碰同一文件）：
  - **A：广西大学导入**（importer/gxu/*、CasLoginActivity、ImportWizardScreen、ImportViewModel、相关单测）
  - **B：小组件引导**（WidgetGuideData.kt、WidgetGuideSection.kt、单测——不编辑 SettingsScreen.kt）
  - **C：小组件 A3**（widget/* 全部、新布局与 info xml、Manifest、MainActivity/AppNav、SettingsKeys、WidgetThemeSection.kt、单测——不编辑 SettingsScreen.kt）
- 主 agent 在三个工作流完成后统一集成：SettingsScreen 挂载两个 Section、README、版本号、全量构建与测试、修复冲突、commit。
