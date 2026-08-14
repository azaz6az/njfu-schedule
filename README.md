# 南林课程表（kechengbioa）

Android 课程表 App：自动导入南林教务课表 + 手动导入兜底，桌面小组件，课前提醒。

## 功能

- **自动导入**：学号密码登录南林统一身份认证（CAS）→ 抓取课表（课表页 URL 需按真机实测调整，见下）
- **手动导入兜底**：JSON / ICS / Excel 三种格式（教务系统改版或遇验证码时使用）
- **桌面小组件**：2×2 今日课程摘要 + 4×3 本周网格（Glance），每日自动刷新
- **周课表**：周次滑动切换、彩色课程卡片、今日高亮、空白格点击加课、课程详情编辑
- **考试安排**：列表展示，临近 7 天高亮
- **课前提醒**：提前 5/10/15 分钟通知（AlarmManager），开机自动重排
- **设置**：学期起始日期、节次时间段、提醒提前量、JSON 备份导出/导入

## 版本历史

### v0.3.0（2026-08-14）

- **修复卡片全透明（白底无字）的根因**：CourseMapper 用 Color.value.toInt() 提取 ARGB，但 Compose Color.value 编码为 0xAARRGGBB00000000（ARGB 在高 32 位），.toInt() 取低 32 位恒为 0 → 所有课程卡颜色为 0 = 全透明，露出白色网格底、白字不可见。改为 (value shr 32).toInt() 后卡片恢复彩色、文字清晰（0.1.0 起就存在的真 bug，此前对比度修复无效的原因）
- 新增回归测试锁定色板提取与卡片不透明度（69 个单元测试全绿）

### v0.2.0（2026-08-12）

- **修复课程卡片可读性**：课程卡 6 色板加深为可读莫兰迪档，白字对比度由 1.8~2.2:1 提升至 5.1~6.2:1（WCAG AA ≥ 4.5:1），历史浅色数据自动映射到新色板同槽位；课程名 11sp→12sp、地点/节次 9sp→10sp，透明度提至 95%/92%
- **修复同格多课重叠**：同一日期间部分重叠（如 1-2 与 1-4 节）的课程不再互相遮挡，按贪心列分配并排显示；不冲突的课程复用同一列保持卡片宽度
- **日期范围显示年份**：跨年的周次显示「2026年12月28日 – 2027年1月3日」，消除寒假跨年歧义
- **今日高亮仅在当前周**：切换到其他周次时不再高亮今日列；今日日期每分钟刷新，跨零点自动更新
- **FAB 加课默认今天**：右下角「加课」默认落到今天（而非固定周一）

## 构建

环境要求：

- JDK 17+（本机已验证 JDK 21）
- Android SDK（`local.properties` 中 `sdk.dir` 指向 SDK 路径，如 `sdk.dir=D:/Android/Sdk`）

```bash
./gradlew assembleDebug          # 构建 APK
./gradlew :app:testDebugUnitTest # 运行单元测试
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`，直接安装到手机即可（需允许未知来源）。

## 使用

1. 打开 App →「导入」Tab → 输入学号密码 → 开始导入
2. 桌面长按 → 添加小组件 → 选择「今日课程」或「本周课表」
3. 设置页配置学期起始日期（影响周次计算）与提醒提前量
4. 提醒权限：Android 12+ 需在系统设置授予「闹钟与提醒」，Android 13+ 需通知权限

## 手动导入格式

- **JSON**：App 设置页「导出备份」产出的格式（`{"version":1,"courses":[...],"exams":[...]}`），也支持裸课程数组 `[...]`
- **ICS**：iCal 周重复事件（RRULE 带 BYDAY/COUNT），时间按默认节次表映射
- **Excel**（xlsx）：表头 + 数据行，列顺序：`课程名,教师,地点,星期(1-7),开始节,结束节,周次`；周次支持 `1-16`、`1,3,5`、`单周`、`双周`

## 南林教务对接说明

- **自动导入 = WebView 登录 + Cookie 桥接**：`CasLoginActivity` 用系统 Chrome 内核打开 CAS 登录页，用户完成登录（含验证码）后读取 jwxt 会话 Cookie（`bzb_jsxsd`），`NjfuAdapter.fetchScheduleWithCookies()` 带 Cookie 抓取 `xskb_list.do` 课表页。
  - 原因：实测 jwxt 反向代理会拒绝一切非浏览器客户端的 CAS ticket 落地（OkHttp/curl/httpx 均 404，仅真实浏览器通过），故登录必须走 WebView；带有效会话 Cookie 的普通页面请求则可正常访问。
  - 课表页结构（`<table id="timetable">`、大节行、kbcontent 变体 div、`-----` 分隔多课程块）已用真实页面 fixture 锁定，见 `JwxtParser`。
- **手动导入兜底**：推荐教务网页端「学生个人课表」导出的 `.xls`（`NjfuXlsImporter`，真实导出文件已验证）；另支持 JSON 备份 / ICS / Excel（xlsx）。
- **周次解析**：统一入口 `WeekUtils.parseWeeksText()`，支持 `1-16`、`1,3,5`、`1-12(周)[01-02节]`、`1-12([周])[01-02节]`、`1-16(单)`、`2-16(双)`、`单周` 等写法；解析失败按全学期显示并在导入结果中提示。
- 历史参考：`CasLoginClient`/`RsaEncryptor` 实现了表单登录协议（AES-128-CBC 复合加密，与线上 `encrypt.js` 一致），现因 WAF 拦截不作为主路径，保留供协议回归测试。

## 项目结构

```
app/src/main/java/com/schedule/njfu/
├── importer/          # SchoolAdapter 接口 / NjfuAdapter（CAS 登录+课表）/ JSON/ICS/Excel 导入
├── data/              # Room 实体/DAO/仓库/凭据 Keystore 加密
├── widget/            # Glance 小组件（2×2 今日 / 4×3 周网格）+ 周期刷新
├── reminder/          # 课前提醒（AlarmManager + 开机重排）
└── ui/                # Compose：周课表/考试/设置/导入向导 + 底部导航
```

设计文档：`docs/superpowers/specs/2026-08-12-njfu-schedule-widget-design.md`
实现计划：`docs/superpowers/plans/2026-08-12-njfu-schedule-widget.md`

## 已知限制

- 自动导入的课表页 URL 尚未真机验证（`jsxsd/xskb_list.do` 为假设）
- 验证码/滑块场景未做完整交互（自动导入失败时走手动导入）
- 小组件刷新为每日一次（WorkManager），打开 App 时也会刷新
- 提醒依赖系统「闹钟与提醒」授权，省电策略可能延迟通知