# 南林课程表 App 设计文档

> 项目：kechengbioa（Android 课程表 + 桌面小组件）
> 日期：2026-08-12
> 状态：已获用户批准（头脑风暴流程产物）

## 1. 背景与目标

做一个手机端课程表 App，核心形态是**桌面小组件**，支持**导入学校网站课程表**。

经澄清确认的需求：

| 需求项 | 结论 |
|--------|------|
| 目标学校 | 南京林业大学（教务系统 `jwxt.njfu.edu.cn`，CAS 统一认证 + RSA 密码加密） |
| 平台 | Android（手机为 Android，无需 iOS） |
| 功能范围 | 全功能版：主 App 周课表查看/课程详情编辑/考试安排/设置 + 桌面小组件 + 课前提醒 |
| 导入方式 | 自动抓取（App 内模拟 CAS 登录）为主，手动导入（ICS/Excel/JSON）为兜底 |
| 使用对象 | 自用，但架构设计为可扩展（多学校适配器） |

## 2. 技术选型

- **Kotlin + Jetpack Compose**：App UI（现代 Android 官方路线）
- **Glance**：桌面小组件（Google 官方 Compose 风格 AppWidget 框架）
- **Room**：课程/考试/设置本地存储
- **OkHttp**：CAS 登录与课表抓取
- **AlarmManager + NotificationChannel**：课前提醒
- **WorkManager**：小组件数据周期刷新
- **Keystore 加密存储**：学号密码凭据

## 3. 设计来源（GitHub 调研，2026-08-12）

先调研后设计。借鉴的是社区验证过的设计模式与实现思路，代码为原创（不直接依赖下述项目，原因：zfman 为 2019 停更的 Java 老栈；BetterUntis 为 GPL-3.0 且绑定 WebUntis；正方系爬虫项目均已失效）。

| 设计点 | 借鉴自 | 应用方式 |
|--------|--------|---------|
| 可插拔适配器架构（每校一个解析器） | 小爱课程表解析器生态（AISchedule） | `SchoolAdapter` 接口，南林为第一个适配器 |
| Kotlin + Compose 技术路线 | SapuSeven/BetterUntis（301★，活跃） | 技术选型依据 |
| 周视图网格 + 彩色课程卡 + 空白格点击 + 课程颜色管理 | zfman/TimetableView（722★） | 全功能版 UI 功能清单 |
| 教务登录→解析→导出的标准链路 | miaotony/NUAA_ClassSchedule（活跃） | 数据流设计 |
| CAS 登录实现细节（lt/execution 票据、RSA+salt 加密） | James0608/ZhengFangJWSystemBackend、mzdluo123/TimeTableBot | 登录协议实现参考 |
| ICS 作为通用导入/导出格式 | NUAA_ClassSchedule（iCal 导出）、untis-ics-sync | 手动导入兜底格式 |
| 单测用 HTML fixture 锁定解析格式 | 正方系项目共同维护经验 | 解析器回归测试策略 |

## 4. 架构

```
┌──────────────────────────────────────────┐
│  南林教务系统 jwxt.njfu.edu.cn            │
│  CAS 统一认证 uia.njfu.edu.cn (RSA加密)   │
└──────────────┬───────────────────────────┘
               │ OkHttp 抓取
┌──────────────▼───────────────────────────┐
│  导入层 importer/                          │
│  ├─ SchoolAdapter (接口)  ← 可扩展多学校   │
│  ├─ NjfuAdapter    (CAS登录+课表抓取)      │
│  └─ ManualImporter (ICS/Excel/JSON 兜底)   │
└──────────────┬───────────────────────────┘
               │ 统一 Course 模型
┌──────────────▼───────────────────────────┐
│  数据层 data/ (Room + 仓库)                │
│  Course / Exam / Settings 表              │
└──────────────┬───────────────────────────┘
               │ StateFlow
┌──────────────▼───────────────────────────┐
│  UI 层                                     │
│  ├─ 主App (Compose)：周课表/课程详情/      │
│  │   考试/设置/导入向导                    │
│  └─ 小组件 (Glance)：今日摘要 2×2 /        │
│      周网格 4×3，WorkManager 定时刷新      │
│  提醒：AlarmManager + 通知（课前 N 分钟）  │
└──────────────────────────────────────────┘
```

## 5. 模块设计

### 5.1 导入层 importer/

**`SchoolAdapter` 接口**（可插拔，未来扩展学校的唯一入口）：

```kotlin
interface SchoolAdapter {
    suspend fun login(credentials: Credentials): Result<Unit>
    suspend fun fetchSchedule(): Result<List<Course>>
    suspend fun fetchExams(): Result<List<Exam>>
}
```

**`NjfuAdapter`**：南林实现
- CAS 登录流程：GET `/authserver/login?service=...` → 解析表单 `lt`、`execution`、`pwdDefaultEncryptSalt` → JSEncrypt RSA 加密密码 → POST 表单（username/passwordEncrypt/lt/execution/_eventId）→ 跟随 service 跳转进入教务系统，持会话 Cookie
- 验证码处理：登录页存在 `captchaResponse`/`isSliderCaptcha` 字段。若服务端开启验证码，App 内展示验证码图片并让用户手动输入（自用场景不做自动识别）
- 课表解析：抓取课表页 → 解析 HTML 表格 → 产出统一 `Course` 模型（名称/教师/地点/星期/节次/周次）；解析器单测用样本 HTML fixture 锁定格式

**`ManualImporter`**：手动导入兜底
- ICS：通用日历格式（支持来自 iCal 导出/outlook 等）
- Excel：教务系统导出常见的 xlsx
- JSON：本 App 的导出/导入备份格式

### 5.2 数据层 data/

**Course**：id、name、teacher、location、dayOfWeek（1-7）、startPeriod、endPeriod、weeks（Int 位掩码，支持任意单双周/间断周组合）、color（自动分配或用户指定）、source（auto/manual）、note

**Exam**：id、name、date、location、note

**Settings**：学号、学期起始日期（用于计算当前周）、节次时间段定义（第 1 节 08:00…可配置）、提醒提前量、小组件配置

**凭据**：学号密码经 Keystore 加密后存 Room；提供"退出登录即清除"

### 5.3 桌面小组件 widget/（Glance）

- **2×2 今日摘要**：今日课程列表（或"今天无课"）
- **4×3 本周网格**：本周课程网格，当日高亮，课程按颜色区分
- 数据来源：Room；刷新策略 = WorkManager 周期刷新（每天一次，早晨）+ 打开 App 时主动刷新 + 小组件 onUpdate
- 点击行为：点击课程卡片 → 打开 App 课程详情；点击空白 → 打开 App 当日视图

### 5.4 提醒 reminder/

- AlarmManager 精确闹钟 + 通知；可配置提前 5/10/15 分钟
- 需要 `POST_NOTIFICATIONS`（Android 13+ 运行时权限）
- 提醒仅在"有课的周次"触发（校验 weeks 位掩码与当前周）

### 5.5 主 App UI ui/（Compose）

- **周课表页**：横向滑动切换周次；课程彩色卡片；今日高亮；空白格点击添加课程；周末可折叠
- **课程详情**：点击课程 → 详情/编辑（名称/教师/地点/节次/周次/颜色/备注）
- **考试页**：考试列表（按日期排序，临近考试高亮）
- **设置页**：登录（学号/密码/验证码）、学期起始日期、节次时间段、提醒设置、手动导入、导出备份、清除数据
- **导入向导**：首次启动引导登录导入；数据状态（最近同步时间、失败原因）可见

## 6. 关键决策

1. **验证码/滑块**：不做自动识别，App 内展示图片手动输入；若频繁触发则引导用户使用手动导入兜底
2. **周次模型**：weeks 位掩码支持任意组合（1-16 周、单周、双周、间断周），不依赖固定公式
3. **凭据**：Keystore 加密仅存本机，不联网上传；自用场景无需服务器
4. **可扩展性**：`SchoolAdapter` 接口与 UI/数据层解耦；未来加学校只新增适配器
5. **小组件刷新**：不过度依赖实时性，接受系统对周期刷新的限制

## 7. 风险与缓解

| 风险 | 缓解 |
|------|------|
| CAS 登录改版导致自动导入失效 | 适配器隔离在 NjfuAdapter，修复局部化；手动导入始终可用 |
| 滑块验证码频繁 | 手动输入验证码；必要时退化为手动导入 |
| 小组件刷新被系统限制 | WorkManager 周期刷新 + 打开 App 立即刷新 |
| 课表接口格式变化 | 解析器单测以样本 HTML fixture 锁定格式，改版可快速定位 |
| 提醒被系统杀死/省电策略 | AlarmManager + 引导用户加入电池白名单 |

## 8. 项目结构

```
kechengbioa/
├── app/
│   ├── src/main/java/com/schedule/njfu/
│   │   ├── importer/          # SchoolAdapter / NjfuAdapter / ManualImporter
│   │   ├── data/              # Room 实体/DAO/仓库/凭据加密
│   │   ├── widget/            # Glance 小组件 + 刷新调度
│   │   ├── reminder/          # 课前提醒调度
│   │   └── ui/                # Compose 页面（周课表/详情/考试/设置/导入向导）
│   ├── src/main/AndroidManifest.xml
│   └── src/test/              # RSA 加密 / HTML 解析 / 周次计算 单测
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/ + gradlew
└── README.md
```

## 9. 测试策略

- **单元测试**（JVM）：RSA 密码加密（对照 JSEncrypt 结果）、课表 HTML 解析（fixture）、周次位掩码计算、ICS 解析
- **仪器/手动验证**：真机安装后 CAS 登录、小组件添加与刷新、提醒触发
- 明确不做的自动化：UI 仪器测试（自用项目成本过高）

## 10. 成功标准

1. 真机安装 APK，输入学号密码可自动导入南林课表
2. 桌面添加 2×2 / 4×3 小组件，能正确显示今日/本周课程
3. 上课前 N 分钟收到通知
4. 教务系统登录失效时，可从 ICS/Excel/JSON 手动导入课表
5. 周次计算与校历一致（学期起始日期可配置）

## 11. 明确不做（范围外 / YAGNI）

- 不做 iOS / 跨平台
- 不做多学校适配器（只留 `SchoolAdapter` 接口，南林为首个实现）
- 不做自动验证码识别
- 不做云端同步、账号系统、服务器
- 不做应用市场上架（自用，直接装 APK）
- 不做 UI 仪器自动化测试
