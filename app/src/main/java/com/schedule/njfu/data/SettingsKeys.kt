package com.schedule.njfu.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object SettingsKeys {
    const val SEMESTER_START = "semester_start"        // ISO 日期，学期第一周的周一
    const val REMIND_MINUTES = "remind_minutes"        // "5"|"10"|"15"
    const val PERIOD_TIMES = "period_times"            // JSON: [{"p":1,"t":"08:00"},...]
    const val EXAM_REMIND_ENABLED = "exam_remind_enabled" // "1"|"0"，默认 1（开启）
    const val EXAM_REMIND_DAYS = "exam_remind_days"    // "1"|"2"|"3"|"7"，默认 "1"
    const val HOLIDAY_SHIFTS = "holiday_shifts"        // JSON: {"2025-10-11":1,...} 调休日按周几显示
    const val WIDGET_THEME = "widget_theme"            // "morandi"|"fresh"|"deep"，默认 morandi
}

/** 小组件主题键，默认 morandi */
suspend fun SettingsDao.widgetTheme(): String = get(SettingsKeys.WIDGET_THEME) ?: "morandi"

suspend fun SettingsDao.semesterStart(): LocalDate {
    val v = get(SettingsKeys.SEMESTER_START) ?: return defaultSemesterStart()
    return runCatching { LocalDate.parse(v) }.getOrElse { defaultSemesterStart() }
}

fun defaultSemesterStart(): LocalDate {
    val now = LocalDate.now()
    // 秋季学期从 9 月 1 日、春季学期从 3 月 1 日（含寒假前的 2 月尾巴归入春季）
    // 选则最近一个不晚于今天的开学日，归一化到当周周一
    val candidates = listOf(
        LocalDate.of(now.year, 9, 1),
        LocalDate.of(now.year, 3, 1),
    )
    val future = candidates.filter { !it.isAfter(now) }.maxOrNull() ?: candidates.first()
    return future.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
