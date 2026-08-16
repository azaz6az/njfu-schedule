package com.schedule.njfu.widget

import com.schedule.njfu.R
import com.schedule.njfu.model.Course
import com.schedule.njfu.model.Exam
import com.schedule.njfu.model.WeekUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * 小组件数据逻辑（纯函数，可 JVM 单测）：
 * 今日小组件与周网格小组件的课程筛选、下一节课倒计时、考试倒计时，与渲染解耦。
 */
object WidgetData {

    /** 今日课程：过滤出 [today] 当天、[week] 周有课的课程，按节次排序 */
    fun todayCourses(courses: List<Course>, week: Int, today: LocalDate): List<Course> {
        val day = today.dayOfWeek.value
        return courses
            .filter { it.dayOfWeek == day && (WeekUtils.contains(it.weeks, week) || it.weeks == 0) }
            .sortedBy { it.startPeriod }
    }

    /** 周网格：按星期分组（1-7），每天最多 [maxPerDay] 门，按节次排序 */
    fun weekCoursesByDay(
        courses: List<Course>,
        week: Int,
        maxPerDay: Int = 4,
    ): Map<Int, List<Course>> =
        (1..7).associateWith { day ->
            courses
                .filter { it.dayOfWeek == day && (WeekUtils.contains(it.weeks, week) || it.weeks == 0) }
                .sortedBy { it.startPeriod }
                .take(maxPerDay)
        }

    // ---- 下一节课倒计时 ----

    /** 倒计时状态机 */
    enum class NextClassPhase { BEFORE, IN_PROGRESS, AFTER, NO_CLASS_TODAY }

    /**
     * 下一节课状态。
     * - BEFORE：上课前，[minutes] 距离上课的分钟数
     * - IN_PROGRESS：上课中，[minutes] 距离下课的分钟数，[course] 即当前这节
     * - AFTER：今天课已全部上完，[minutes] 无意义(0)
     * - NO_CLASS_TODAY：当日无课
     *
     * [course] 仅在 BEFORE / IN_PROGRESS 时有值。
     */
    data class NextClassState(
        val phase: NextClassPhase,
        val course: Course?,
        val minutes: Int,
        /** 星期中文名的字符串资源 id（1=一 .. 7=日），渲染时经 context.getString 取串 */
        val todayRes: Int,
        val week: Int,
    )

    /** 默认节次表：第 1 节 08:00 起每节 45 分钟、节间 10 分钟休息；跨午间/晚间各留一段长休 */
    private val DEFAULT_PERIOD_TIMES: Map<Int, LocalTime> = mapOf(
        1 to LocalTime.of(8, 0),
        2 to LocalTime.of(8, 55),
        3 to LocalTime.of(9, 50),
        4 to LocalTime.of(10, 45),
        5 to LocalTime.of(11, 40),
        6 to LocalTime.of(14, 0),
        7 to LocalTime.of(14, 55),
        8 to LocalTime.of(15, 50),
        9 to LocalTime.of(16, 45),
        10 to LocalTime.of(19, 0),
        11 to LocalTime.of(19, 55),
        12 to LocalTime.of(20, 50),
    )

    /**
     * 由 {节次: "HH:mm"} 映射构建节次起始时间表。
     * 未知 key 回退默认节次表。
     */
    fun periodTimeMap(periodTimes: Map<Int, String>): Map<Int, LocalTime> {
        if (periodTimes.isEmpty()) return DEFAULT_PERIOD_TIMES
        return DEFAULT_PERIOD_TIMES.mapValues { (k, def) ->
            periodTimes[k]?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: def
        }
    }

    /**
     * 解析设置里 PERIOD_TIMES 的 JSON 串（["p":1,"t":"08:00"]...）为节次映射。
     * null / 空 / 解析失败 → 空 Map（后续回退默认节次表）。
     */
    fun parsePeriodTimesJson(raw: String?): Map<Int, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            buildMap {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val p = o.optInt("p")
                    val t = o.optString("t")
                    if (p > 0 && t.isNotBlank()) put(p, t)
                }
            }
        }.getOrElse { emptyMap() }
    }

    /**
     * 计算「下一节课」状态（当日视角，跨天不处理）。
     *
     * 逻辑：按 [now] 的星期 + [week] 周次过滤出当日课程（weeks 判断与 [todayCourses] 一致），
     * 每门课占用 [startPeriod 开始, endPeriod 结束+45min] 一段：
     * - 当前时间落在一门课区间内 → IN_PROGRESS，[minutes]=距下课分钟，course=该课（按上课节次取最早）
     * - 否则当前时间 < 最早未来课程的开始 → BEFORE，[minutes]=距上课分钟
     * - 所有课都已下课 → AFTER
     * - 当日无课 → NO_CLASS_TODAY
     */
    fun nextClassState(
        courses: List<Course>,
        week: Int,
        now: LocalDateTime,
        periodTimes: Map<Int, String>,
    ): NextClassState {
        val day = now.dayOfWeek.value
        val todayRes = dayNameRes(day)
        val tables = periodTimeMap(periodTimes)
        val dayCourses = courses
            .filter { it.dayOfWeek == day && (WeekUtils.contains(it.weeks, week) || it.weeks == 0) }
            .sortedBy { it.startPeriod }
        if (dayCourses.isEmpty()) {
            return NextClassState(NextClassPhase.NO_CLASS_TODAY, null, 0, todayRes, week)
        }

        val nowTime = now.toLocalTime()
        // 门课的空闲区间 [start, end]（end = endPeriod 开始 + 45min）
        fun spanOf(c: Course): Pair<LocalTime, LocalTime>? {
            val s = tables[c.startPeriod] ?: return null
            val e = (tables[c.endPeriod] ?: s).plusMinutes(45)
            return s to e
        }

        // 上课中：now 落在某门课区间内（同 day 课程无冲突，只会命中一节）
        dayCourses.firstOrNull { c ->
            val (s, e) = spanOf(c) ?: return@firstOrNull false
            !nowTime.isBefore(s) && nowTime.isBefore(e)
        }?.let { active ->
            val (_, end) = spanOf(active)!!
            val minutes = ChronoUnit.MINUTES.between(nowTime, end).toInt().coerceAtLeast(0)
            return NextClassState(NextClassPhase.IN_PROGRESS, active, minutes, todayRes, week)
        }

        // 上课前：找最早 start>now 的课
        dayCourses.firstOrNull { c ->
            val (s, _) = spanOf(c) ?: return@firstOrNull false
            nowTime.isBefore(s)
        }?.let { upcoming ->
            val (s, _) = spanOf(upcoming)!!
            val minutes = ChronoUnit.MINUTES.between(nowTime, s).toInt()
            return NextClassState(NextClassPhase.BEFORE, upcoming, minutes, todayRes, week)
        }

        // 无 future 课且无 in-progress → 今天已上完
        return NextClassState(NextClassPhase.AFTER, null, 0, todayRes, week)
    }

    /**
     * 最近一场考试（date >= [today] 且最早），返回 (考试, 剩余天数)。
     * 无考试返回 null。
     */
    fun nextExamCountdown(exams: List<Exam>, today: LocalDate): Pair<Exam, Long>? {
        val upcoming = exams
            .mapNotNull { e -> e.date.let { d -> runCatching { LocalDate.parse(d) }.getOrNull()?.let { it to e } } }
            .filter { (d, _) -> !d.isBefore(today) }
            .sortedBy { it.first }
            .firstOrNull()
            ?: return null
        val days = ChronoUnit.DAYS.between(today, upcoming.first)
        return upcoming.second to days
    }

    /**
     * 星期几中文名的字符串资源 id：1=一 .. 7=日；越界返回 0（无资源）。
     * 渲染（RemoteViews / Compose）时经 context.getString / stringResource 取串。
     */
    fun dayNameRes(dayOfWeek: Int): Int = when (dayOfWeek) {
        1 -> R.string.weekday_mon
        2 -> R.string.weekday_tue
        3 -> R.string.weekday_wed
        4 -> R.string.weekday_thu
        5 -> R.string.weekday_fri
        6 -> R.string.weekday_sat
        7 -> R.string.weekday_sun
        else -> 0
    }
}
