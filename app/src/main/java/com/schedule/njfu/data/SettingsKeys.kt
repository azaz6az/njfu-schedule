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

fun defaultSemesterStart(): LocalDate = defaultSemesterStartFor(LocalDate.now())

/**
 * 按给定日期推算默认开学日。
 * 候选开学日：去年 9-1（秋季）、今年 3-1（春季）、今年 9-1（秋季）。
 * 取【最近一个不晚于 today】的候选并归一化到当周周一；
 * 全部在未来（极端场景，如 today 早于去年 9-1）时兜底取去年 9-1。
 *
 * 覆盖全年时段：
 *  - 1 月~2 月 → 最近开学日是去年 9-1（此前实现漏掉去年候选，导致此时全部候选在未来，
 *    退回未来 9-1 使 currentWeek 恒为 1）；
 *  - 3 月~8 月 → 今年 3-1；
 *  - 9 月~12 月 → 今年 9-1。
 *
 * 抽成 internal 以便测试注入固定 now（公开 API [defaultSemesterStart] 保持不变）。
 */
internal fun defaultSemesterStartFor(today: LocalDate): LocalDate {
    // 秋季学期从 9 月 1 日、春季学期从 3 月 1 日（含寒假前的 2 月尾巴归入春季）
    // 选最近一个不晚于今天的开学日，归一化到当周周一
    val candidates = listOf(
        LocalDate.of(today.year - 1, 9, 1), // 去年秋季
        LocalDate.of(today.year, 3, 1),     // 今年春季
        LocalDate.of(today.year, 9, 1),     // 今年秋季
    )
    val past = candidates.filter { !it.isAfter(today) }
    val chosen = past.maxOrNull() ?: LocalDate.of(today.year - 1, 9, 1)
    return chosen.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
