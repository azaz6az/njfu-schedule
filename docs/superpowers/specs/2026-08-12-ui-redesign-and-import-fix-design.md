# 南林课表 App：UI 莫兰迪纸感改造 + 自动导入修复 — 设计

日期：2026-08-12 · 分支：feature/njfu-schedule

## 一、背景

1. **UI 现状**：`Theme.kt` 仅 12 行，使用默认 `lightColorScheme()`；课表网格为硬编码灰白棋盘格（无圆角、无边框）；周切换为朴素 `◀ ▶` 文本按钮；底部为全宽「＋ 加课」按钮；无深色模式。
2. **自动导入失败**（用户报告「无法获取课程表」，课表页 `https://jwxt.njfu.edu.cn/jsxsd/framework/xsMainV.htmlx`）。代码审查 + curl 侦察确认根因：

   - **Bug 1 · 会话 Cookie 从未保存**：`OkHttpClient` 默认 `CookieJar.NO_COOKIES`；`CasLoginClient` 与 `NjfuAdapter` 各建独立 client 且均未配置 CookieJar。登录响应中的 `Set-Cookie` 全部丢弃，后续抓课表请求无会话。
   - **Bug 2 · 跳过 sso.jsp**：curl 证实未登录访问 jsxsd 任何页面返回 **HTTP 200 + JS 跳转脚本**（非 302）。CAS 登录成功返回 302 + `ticket=`，需再访问 `sso.jsp` 校验 ticket 并建立 jsxsd 会话；现代码拿到 302 即宣告成功，从未访问 sso.jsp。
   - **Bug 3 · 静默失败**：抓课表拿到登录跳转脚本时解析出 0 门课，UI 显示「成功导入 0 门课程」或泛化错误，无有效指引。
   - **Bug 4 · cleartext 未放行**：`sso.jsp` 为 `http://` 明文地址，Manifest 未配置明文许可，跟随 sso.jsp 时会抛 `Cleartext HTTP traffic not permitted`。

## 二、方案 A：自动导入修复

1. **共享会话 `HttpSession`**（新文件 `importer/HttpSession.kt`）：
   - 内存 `CookieJar`（按 host 分组存 `Cookie`，`loadForRequest` 用 `matches(url)` 过滤）。
   - 暴露两个共享 client（同一 CookieJar）：`client`（跟随重定向）与 `noRedirectClient`（`followRedirects(false)`，登录流程用）。
2. **`CasLoginClient.login` 补全**：
   - 改用 `HttpSession.noRedirectClient`。
   - 302 + `Location` 含 `ticket=` 后，手动跟随重定向链（最多 5 跳）访问 `sso.jsp` → jsxsd 框架页，收下全部 `Set-Cookie`，真正建立目标系统会话。
   - 相对 Location 解析（`/jsxsd/...` → origin 拼接）。
3. **`NjfuAdapter.fetchScheduleHtml` 会话/结构校验**：
   - `JwxtParser` 新增两个纯函数：
     - `isLoginRedirect(html)`：含 `authserver/login` 或 `window.location.href` → 会话失效。
     - `looksLikeSchedulePage(html)`：含 `<table` 且含 `星期`/`周一`/`节次` 之一。
   - 抓取后先查登录跳转（报「登录会话已失效，请重新登录」），再查页面结构（报「课表接口返回异常页面，接口可能已改版」），避免静默解析空课表。
4. **明文放行**：新增 `res/xml/network_security_config.xml`，仅对 `jwxt.njfu.edu.cn` / `uia.njfu.edu.cn` 允许 cleartext；Manifest application 引用。
5. **测试**：
   - `CasLoginClientTest` 新增：登录成功后跟随 sso.jsp（mock 302→302→200），断言第 4 个请求为 sso.jsp 且携带 `Cookie: JSESSIONID=...`。
   - `JwxtParserTest` 新增：`isLoginRedirect` / `looksLikeSchedulePage` 用例（fixture 课表页 → 两者均不触发登录跳转、结构合法）。

## 三、方案 B：UI 莫兰迪纸感改造（原型已批准）

风格：低饱和莫兰迪、纸感卡片、圆角、留白；浅色 + 深色跟随系统；全部 4 个页面统一。

1. **`Theme.kt` 重写**：
   - 浅色：背景 `#FAFAF8`、表面 `#FFFFFF`、主色灰粉 `#A88C8C`、次级灰蓝 `#9FB4C7`、三级灰绿 `#A8BCA3`、容器 `#F1E9E9`、表面变体 `#F3F2EC`、文字 `#4A4A44`、次级文字 `#8F8E86`。
   - 深色：背景 `#1E1E1C`、表面 `#262622`、主色浅灰粉 `#C4A9A9`、容器 `#332B2B`、文字 `#E8E6E0`。
   - `isSystemInDarkTheme()` 自动切换。
   - 新增 `CoursePalette`：课程卡 6 色板（灰绿/灰蓝/灰粉/暖灰/灰紫/灰青）。
2. **`CourseMapper`**：palette 换为莫兰迪 6 色；新增 `displayColor(raw)` 将旧 palette 颜色映射到新色板（老数据自动换新色）。
3. **课表主页 `ScheduleScreen`**：
   - 周切换器：左侧「第 X 周」大标题 + 副标题（本周日期范围，由 semesterStart 计算）+「当前第 X 周」chip + 右侧圆形箭头按钮。
   - 网格：外层圆角 16dp 浅灰容器（纸感），单元格白底 + 1dp 间隙自然成网格线；今日列 `primaryContainer` 高亮；表头今日 primary 加粗；节次标签保留。
   - 课程卡：圆角 8dp、`displayColor` 背景、左侧 3dp 白色条、白字（B 原型风格）。
   - 「＋ 加课」改右下角悬浮 FAB（圆角 16dp、primary），移除底部全宽按钮。
   - `ScheduleViewModel` 暴露 `semesterStart: StateFlow<LocalDate>` 供副标题使用。
4. **考试页 `ExamScreen`**：顶部「考试安排」标题 + 副标题（共 N 门 · 最近一场 X 天后）；卡片圆角 12dp + 1dp 纸感阴影；临近考试 `primaryContainer` 高亮、已过期置灰；底部按钮胶囊化。
5. **设置页 `SettingsScreen`**：按钮统一胶囊圆角；分组标题/分隔线随主题自动变色；其余结构不动。
6. **导入页 `ImportWizardScreen`**：两张纸感卡片（圆角 16dp、1dp 阴影）；按钮胶囊化；输入框保持 Outlined 随主题；已登录提示 chip 化。
7. **底部导航 `AppNav`**：选中指示器随主题；图标换更贴切的 core 图标（课表 `Apps` 网格感、考试 `DateRange`、设置 `Settings`、导入 `Add`）；如需更佳图标可引入 `material-icons-extended`（GridView / EditCalendar / FileDownload）。
8. **`themes.xml`**：新增 `values-night/themes.xml`（`android:Theme.Material.NoActionBar`），系统栏图标深浅随系统。

## 四、文件改动清单

| 文件 | 改动 |
|---|---|
| `importer/HttpSession.kt` | 新增：共享 CookieJar + 双 client |
| `importer/njfu/CasLoginClient.kt` | 用共享 client；302 后跟随 sso.jsp |
| `importer/njfu/JwxtParser.kt` | 新增 `isLoginRedirect` / `looksLikeSchedulePage` |
| `importer/njfu/NjfuAdapter.kt` | 共享 client + 会话/结构校验 + 明确报错 |
| `AndroidManifest.xml` | 引用 network security config |
| `res/xml/network_security_config.xml` | 新增：放行两个域名明文 |
| `ui/theme/Theme.kt` | 重写：莫兰迪浅/深色板 + CoursePalette |
| `data/CourseMapper.kt` | 莫兰迪 6 色板 + displayColor 映射 |
| `ui/schedule/ScheduleScreen.kt` | 周切换器、网格、课程卡、FAB |
| `ui/schedule/ScheduleViewModel.kt` | 暴露 semesterStart |
| `ui/schedule/ExamScreen.kt` | 标题区、卡片、按钮 |
| `ui/settings/SettingsScreen.kt` | 按钮胶囊化 |
| `ui/import/ImportWizardScreen.kt` | 卡片纸感化、按钮胶囊化 |
| `ui/navigation/AppNav.kt` | 图标调整 |
| `res/values/themes.xml` + `res/values-night/themes.xml` | 深色系统栏 |
| 测试 | CasLoginClientTest / JwxtParserTest / CourseMapperTest 更新 |

## 五、验证

- `./gradlew test`（现有 30 测试全绿 + 新增用例）
- `./gradlew assembleDebug` 构建通过
- 真机验证：登录 → 抓课表链路；浅/深色切换
