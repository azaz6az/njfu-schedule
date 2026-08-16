package com.schedule.njfu.importer

import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object IcsImporter {

    private data class VEvent(
        val summary: String = "", val location: String = "",
        val dtStart: String = "", val dtEnd: String = "",
        val rrule: String = "",
    )

    /** 默认节次表：将时间映射到节次（第1节 08:00 起每节 1 小时，午休 12:00-14:00 空档，晚课至 12 节） */
    private val periodMap = listOf(
        1 to "08:00", 2 to "09:00", 3 to "10:00", 4 to "11:00",
        5 to "14:00", 6 to "15:00", 7 to "16:00", 8 to "17:00",
        9 to "19:00", 10 to "20:00", 11 to "21:00", 12 to "22:00",
    )

    fun parse(icsText: String, semesterStart: LocalDate? = null): List<Course> {
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
        return events.mapNotNull { toCourse(it, semesterStart) }
    }

    private fun toCourse(ev: VEvent, semesterStart: LocalDate?): Course? {
        if (ev.summary.isBlank() || ev.dtStart.length < 8) return null
        val byday = Regex("BYDAY=([A-Z]{2})").find(ev.rrule)?.groupValues?.get(1) ?: return null
        val count = Regex("COUNT=(\\d+)").find(ev.rrule)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val dayMap = mapOf("MO" to 1, "TU" to 2, "WE" to 3, "TH" to 4, "FR" to 5, "SA" to 6, "SU" to 7)
        val day = dayMap[byday] ?: return null
        // 时区换算：带 Z（UTC）或 ±HHMM 偏移 → 换算到系统本地时区；无后缀 → 视为本地时间
        val zone = ZoneId.systemDefault()
        val startToString = parseDtToLocalTime(ev.dtStart, zone) ?: return null
        val minutes = startToString.minutes()
        // 周次偏移：给了学期起始日则按 DTSTART 在学期中的周号定位，count 超限时整体 clamp
        val weeks = computeWeeksMask(ev.dtStart, count, semesterStart)
        val startPeriod = periodMap.indexOfFirst { p -> p.second.minutes() >= minutes }
            .takeIf { it >= 0 }?.let { periodMap[it].first } ?: 1
        var endMinutes = minutes + 60
        val endPeriod = periodMap.indexOfLast { p -> p.second.minutes() <= endMinutes }
            .takeIf { it >= 0 }?.let { periodMap[it].first } ?: startPeriod
        return Course(
            name = ev.summary, location = ev.location, dayOfWeek = day,
            startPeriod = startPeriod, endPeriod = endPeriod.coerceAtLeast(startPeriod),
            weeks = weeks, color = 0,
        )
    }

    /**
     * 把 ICS 的 DTSTART/DTEND 时间串解析为系统本地时区的 "HH:mm"。
     *  - 后缀 `Z` → UTC，`+HHMM`/`-HHMM` → 对应偏移：先转瞬时再换到 [zone]
     *  - 无后缀 → 视为本地时间（本地格式，直接取 HH:mm）
     *  - 无法解析返回 null
     * 兼容常见 `20260901T100000Z`、`20260901T100000+0200`、`20260901T100000`、`20260901T100000.000Z` 等。
     */
    private fun parseDtToLocalTime(raw: String, zone: ZoneId): String? {
        if (raw.length < 15) return null
        // 形如 20260901T100000，T 之后为时分秒（可能带 .fff 或偏移后缀）
        val datePart = raw.substring(0, 8)
        val date = runCatching { LocalDate.parse(datePart, DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()
            ?: return null
        val rest = raw.substring(9)
        val m = Regex("^(\\d{2})(\\d{2})(\\d{2})(?:\\.\\d+)?(.*)$").find(rest) ?: return null
        val hh = m.groupValues[1].toInt()
        val mm = m.groupValues[2].toInt()
        val ss = m.groupValues[3].toInt()
        val suffix = m.groupValues[4]
        return if (suffix.isEmpty()) {
            // 无后缀：本地时间
            String.format("%02d:%02d", hh, mm)
        } else {
            // 带偏移（Z 或 ±HHMM）：构造带偏移的 ZonedDateTime 后换到本地时区
            val offset = when {
                suffix == "Z" -> java.time.ZoneOffset.UTC
                Regex("^[+-]\\d{4}$").matches(suffix) -> {
                    val oh = suffix.substring(1, 3).toInt()
                    val om = suffix.substring(3, 5).toInt()
                    val sign = if (suffix.startsWith('-')) -1 else 1
                    java.time.ZoneOffset.ofHoursMinutes(sign * oh, sign * om)
                }
                else -> return String.format("%02d:%02d", hh, mm) // 无法识别的偏移，退回原值
            }
            val zdt = java.time.ZonedDateTime.of(
                date, java.time.LocalTime.of(hh, mm, ss, 0), offset,
            )
            String.format("%02d:%02d", zdt.withZoneSameInstant(zone).hour, zdt.withZoneSameInstant(zone).minute)
        }
    }

    /**
     * 计算周次掩码。
     *  - semesterStart 非空：起始周 = DTSTART 在学期中的周号（复用 [WeekUtils.currentWeek]），
     *    weeks = maskFor(起始周, 起始周+count-1)，并把起始周与结束周都 clamp 到 1..MAX_WEEKS。
     *  - semesterStart 为 null：保持原行为 maskFor(1, count)，同样 clamp 防 Int 位掩码溢出。
     */
    private fun computeWeeksMask(dtStart: String, count: Int, semesterStart: LocalDate?): Int {
        val max = WeekUtils.MAX_WEEKS
        val clampedCount = count.coerceAtLeast(0)
        if (semesterStart != null && dtStart.length >= 8) {
            val evDate = runCatching {
                LocalDate.parse(dtStart.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE)
            }.getOrNull()
            if (evDate != null) {
                val startWeek = WeekUtils.currentWeek(semesterStart, evDate).coerceIn(1, max)
                val endWeek = (startWeek + clampedCount - 1).coerceIn(1, max).coerceAtLeast(startWeek)
                return WeekUtils.maskFor(startWeek, endWeek)
            }
        }
        val startWeek = 1
        val endWeek = (startWeek + clampedCount - 1).coerceIn(1, max).coerceAtLeast(startWeek)
        return WeekUtils.maskFor(startWeek, endWeek)
    }

    private fun String.minutes(): Int {
        val p = split(":")
        return p[0].toInt() * 60 + p[1].toInt()
    }
}
