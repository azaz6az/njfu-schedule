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

    /** 学期起始日(周一)与今天的周差，从 1 开始；今天早于起始日返回 1 */
    fun currentWeek(start: LocalDate, today: LocalDate): Int {
        if (today.isBefore(start)) return 1
        val startMonday = start.with(WeekFields.of(Locale.CHINA).dayOfWeek(), 1L)
        val days = java.time.temporal.ChronoUnit.DAYS.between(startMonday, today)
        return (days / 7).toInt() + 1
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
