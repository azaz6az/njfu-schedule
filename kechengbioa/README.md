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

- 登录：`uia.njfu.edu.cn`（金智教育 CAS），密码为 **AES-128-CBC 复合加密**（salt 作密钥 + 随机 64 字符前缀 + 随机 IV，与线上 `encrypt.js` 一致，见 `docs/njfu-cas-notes.md`）
- 课表端点：`jwxt.njfu.edu.cn/jsxsd/` 系列（**登录后课表页 URL 需真机验证**，当前实现 `xskb_list.do` 为假设路径，若抓取失败按实际页面调整 `NjfuAdapter.fetchScheduleHtml()`）
- 验证码：`needCaptcha.html` 判定；触发时 App 提示手动导入兜底
- 风险：教务系统改版会导致自动导入失效——适配器隔离在 `importer/njfu/`，修复只动该目录；手动导入始终可用

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
