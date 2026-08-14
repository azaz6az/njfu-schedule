package com.schedule.njfu.model

import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

object WeekUtils {
    const val MAX_WEEKS = 30

    /** 单周掩码：位 n（1..30）表示第 n 周有课 */
    fun maskFor(startWeek: Int, endWeek: Int): Int {
        var mask = 0
        for (w in startWeek..endWeek) mask = mask or (1 shl (w - 1))
        return mask
    }

    fun maskFor(singleWeek: Int): Int = 1 shl (singleWeek - 1)

    fun oddWeeks(startWeek: Int, endWeek: Int): Int {
        var mask = 0
        for (w in startWeek..endWeek) if (w % 2 == 1) mask = mask or (1 shl (w - 1))
        return mask
    }

    fun evenWeeks(startWeek: Int, endWeek: Int): Int {
        var mask = 0
        for (w in startWeek..endWeek) if (w % 2 == 0) mask = mask or (1 shl (w - 1))
        return mask
    }

    fun contains(mask: Int, week: Int): Boolean =
        week in 1..MAX_WEEKS && (mask and (1 shl (week - 1))) != 0

    /** 两门课是否时间冲突：同一天、周次掩码有交集、节次区间重叠（含部分重叠） */
    fun overlaps(a: Course, b: Course): Boolean =
        a.dayOfWeek == b.dayOfWeek &&
            (a.weeks and b.weeks) != 0 &&
            a.startPeriod <= b.endPeriod && b.startPeriod <= a.endPeriod

    /** 候选课程与现有课程列表的冲突检测（编辑自身 id 相同的课程不算冲突） */
    fun findConflicts(courses: List<Course>, candidate: Course): List<Course> =
        courses.filter { it.id != candidate.id || candidate.id == 0L }
            .filter { overlaps(it, candidate) }

    /** 学期起始日(周一)与今天的周差，从 1 开始；今天早于起始日返回 1 */
    fun currentWeek(start: LocalDate, today: LocalDate): Int {
        if (today.isBefore(start)) return 1
        val startMonday = start.with(WeekFields.of(Locale.CHINA).dayOfWeek(), 1L)
        val days = java.time.temporal.ChronoUnit.DAYS.between(startMonday, today)
        return (days / 7).toInt() + 1
    }

    /**
     * 通用周次文本解析器（各导入器共用）。
     *
     * 支持教务系统常见写法：
     *  - `1-16`、`1,3,5`、`1-6,8-10,12-13`
     *  - `1-12(周)`、`3(周)`、`1-2,4-7,9-10,12-13(周)[03-04节]`（正方课表页）
     *  - `1-12([周])[01-02节]`（教务导出 .xls）
     *  - `1-16(单)`、`2-16(双)`、`1-16单周`、`单周`、`双周`
     *
     * 解析失败返回 0（调用方决定兜底策略，见 [fixMissingWeeks]）。
     */
    fun parseWeeksText(raw: String): Int {
        var t = raw.trim()
        if (t.isEmpty()) return 0
        t = t.replace("([周])", "").replace("（周）", "")
        t = t.substringBefore("[")
        t = t.replace(Regex("[（(]周[)）]"), "")
        val odd = t.contains("单")
        val even = t.contains("双")
        t = t.replace("单周", "").replace("双周", "")
        t = t.replace(Regex("[（(](单|双)[)）]"), "")
        t = t.replace(Regex("周\\s*$"), "").trim()
        var mask = 0
        for (part in t.split(',', '，', '、')) {
            val p = part.trim()
            if (p.isEmpty()) continue
            val range = Regex("^(\\d+)\\s*[-–—~～至到]\\s*(\\d+)$").find(p)
            if (range != null) {
                val a = range.groupValues[1].toInt().coerceIn(1, MAX_WEEKS)
                val b = range.groupValues[2].toInt().coerceIn(1, MAX_WEEKS)
                if (a <= b) mask = mask or maskFor(a, b)
            } else {
                p.toIntOrNull()?.takeIf { it in 1..MAX_WEEKS }?.let { mask = mask or maskFor(it) }
            }
        }
        if (mask == 0 && (odd || even)) mask = maskFor(1, MAX_WEEKS)
        if (odd && !even) mask = mask and oddWeeks(1, MAX_WEEKS)
        if (even && !odd) mask = mask and evenWeeks(1, MAX_WEEKS)
        return mask
    }

    /**
     * 导入数据兜底：周次掩码为 0（解析失败）的课程按全学期显示，避免“课进了库却永远不显示”。
     * @return 修复后的课程列表与修复数量
     */
    fun fixMissingWeeks(courses: List<Course>): Pair<List<Course>, Int> {
        var fixed = 0
        val out = courses.map { c ->
            if (c.weeks != 0) c else { fixed++; c.copy(weeks = maskFor(1, MAX_WEEKS)) }
        }
        return out to fixed
    }

    /** 中文数字（一~九十九）转 Int；用于“第一大节”等标签 */
    fun chineseToInt(s: String): Int? {
        val map = mapOf(
            '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5,
            '六' to 6, '七' to 7, '八' to 8, '九' to 9,
        )
        return when {
            s.isEmpty() -> null
            s == "十" -> 10
            s.length == 1 -> map[s[0]]
            s.startsWith('十') && s.length == 2 -> 10 + (map[s[1]] ?: return null)
            s.endsWith('十') && s.length == 2 -> (map[s[0]] ?: return null) * 10
            s.length == 2 -> {
                val a = map[s[0]] ?: return null
                val b = map[s[1]] ?: return null
                a * 10 + b
            }
            else -> null
        }
    }

    /** 由节次时间段表（periodNo to "HH:mm"）算某节开始时间 */
    fun startTimeOf(period: Int, times: List<Pair<Int, String>>): String =
        times.firstOrNull { it.first == period }?.second ?: ""

    /** 课程结束时间 = 结束节次开始时间 + 1 小时 */
    fun endTimeOf(endPeriod: Int, times: List<Pair<Int, String>>): String {
        val t = startTimeOf(endPeriod, times) ?: return ""
        val hm = t.split(":")
        val minutes = hm[0].toInt() * 60 + hm[1].toInt() + 60
        return String.format("%02d:%02d", minutes / 60 % 24, minutes % 60)
    }
}
