package com.schedule.njfu.importer

import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils

object IcsImporter {

    private data class VEvent(
        val summary: String = "", val location: String = "",
        val dtStart: String = "", val dtEnd: String = "",
        val rrule: String = "",
    )

    /** 默认节次表：将时间映射到节次（第1节 08:00 起每节 1 小时，午休 12:00-14:00 空档） */
    private val periodMap = listOf(
        1 to "08:00", 2 to "09:00", 3 to "10:00", 4 to "11:00",
        5 to "14:00", 6 to "15:00", 7 to "16:00", 8 to "17:00",
        9 to "19:00", 10 to "20:00",
    )

    fun parse(icsText: String): List<Course> {
        val events = mutableListOf<VEvent>()
        var current: VEvent? = null
        for (rawLine in icsText.lineSequence()) {
            val line = rawLine.trim()
            if (line == "BEGIN:VEVENT") { current = VEvent(); continue }
            if (line == "END:VEVENT") { current?.let { events.add(it) }; current = null; continue }
            val cur = current ?: continue
            val colon = line.indexOf(':')
            if (colon < 0) continue
            val key = line.substring(0, colon).uppercase()
            val value = line.substring(colon + 1).trim()
            when (key) {
                "SUMMARY" -> cur.copy(summary = value).also { current = it }
                "LOCATION" -> cur.copy(location = value).also { current = it }
                "DTSTART" -> cur.copy(dtStart = value).also { current = it }
                "DTEND" -> cur.copy(dtEnd = value).also { current = it }
                "RRULE" -> cur.copy(rrule = value).also { current = it }
            }
        }
        return events.mapNotNull { toCourse(it) }
    }

    private fun toCourse(ev: VEvent): Course? {
        if (ev.summary.isBlank() || ev.dtStart.length < 8) return null
        val byday = Regex("BYDAY=([A-Z]{2})").find(ev.rrule)?.groupValues?.get(1) ?: return null
        val count = Regex("COUNT=(\\d+)").find(ev.rrule)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val dayMap = mapOf("MO" to 1, "TU" to 2, "WE" to 3, "TH" to 4, "FR" to 5, "SA" to 6, "SU" to 7)
        val day = dayMap[byday] ?: return null
        val startTime = ev.dtStart.substring(9, 13)   // "20260901T100000Z" → "1000"
        val hh = startTime.substring(0, 2).toInt(); val mm = startTime.substring(2, 4).toInt()
        val minutes = hh * 60 + mm
        val startPeriod = periodMap.indexOfFirst { p -> p.second.minutes() >= minutes }
            .takeIf { it >= 0 }?.let { periodMap[it].first } ?: 1
        var endMinutes = minutes + 60
        val endPeriod = periodMap.indexOfLast { p -> p.second.minutes() <= endMinutes }
            .takeIf { it >= 0 }?.let { periodMap[it].first } ?: startPeriod
        return Course(
            name = ev.summary, location = ev.location, dayOfWeek = day,
            startPeriod = startPeriod, endPeriod = endPeriod.coerceAtLeast(startPeriod),
            weeks = WeekUtils.maskFor(1, count), color = 0,
        )
    }

    private fun String.minutes(): Int {
        val p = split(":")
        return p[0].toInt() * 60 + p[1].toInt()
    }
}
