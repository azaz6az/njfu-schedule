package com.schedule.njfu.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object SettingsKeys {
    const val SEMESTER_START = "semester_start"        // ISO 日期，学期第一周的周一
    const val REMIND_MINUTES = "remind_minutes"        // "5"|"10"|"15"
    const val PERIOD_TIMES = "period_times"            // JSON: [{"p":1,"t":"08:00"},...]
}

suspend fun SettingsDao.semesterStart(): LocalDate {
    val v = get(SettingsKeys.SEMESTER_START) ?: return defaultSemesterStart()
    return runCatching { LocalDate.parse(v) }.getOrElse { defaultSemesterStart() }
}

fun defaultSemesterStart(): LocalDate {
    val now = LocalDate.now()
    // 默认取最近的 9 月 1 日/2 月 1 日（不晚于今天），归一化到当周周一
    val candidates = listOf(
        LocalDate.of(now.year, 9, 1),
        LocalDate.of(now.year, 2, 1),
        LocalDate.of(now.year, 3, 1),
    )
    val future = candidates.filter { !it.isAfter(now) }.maxOrNull() ?: candidates.first()
    return future.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
