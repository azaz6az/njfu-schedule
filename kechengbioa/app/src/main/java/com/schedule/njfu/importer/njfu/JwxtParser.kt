package com.schedule.njfu.importer.njfu

import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils

/**
 * 正方教务系统课表 HTML 解析器。
 *
 * 期望结构（见 test fixtures/njfu_schedule_sample.html）：
 *  - <table> 首行为表头（节次 + 星期一~星期日），其后每行 = 一个节次时段（第1-2节…第9-10节），
 *    每列 = 一个星期；
 *  - 课程单元格内以 <br> 分隔：课程名 / 教师 / 周次 / 节次 / 地点；
 *  - 周次写法支持 "1-16周"、"1-16周(单)"、"1-16周单周"、"2-16周双周"、"单周"、"双周"。
 */
object JwxtParser {

    /**
     * 页面是否含"跳转登录"标记（会话失效：未登录时 jsxsd 返回 200 + JS 跳转脚本，而非 302）。
     * 要求 JS 跳转与登录地址同时出现，避免误伤页面正文/注释中仅提及登录地址的课表页。
     */
    fun isLoginRedirect(html: String): Boolean =
        html.contains("window.location.href") && html.contains("authserver/login")

    /** 页面是否像课表页（含表格与星期表头特征），用于区分"空课表"与"接口异常" */
    fun looksLikeSchedulePage(html: String): Boolean =
        html.contains("<table", ignoreCase = true) &&
            (html.contains("星期") || html.contains("周一") || html.contains("节次"))

    fun parseSchedule(html: String): List<Course> {
        val courses = mutableListOf<Course>()
        val rows = Regex("<tr[^>]*>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL).findAll(html).toList()
        // 第一行是表头（节次 + 星期），跳过；其余每行 = 一个节次时段
        val dataRows = rows.drop(1)
        for ((i, row) in dataRows.withIndex()) {
            // 行 0 → 第1-2节，行 1 → 第3-4节……
            val startPeriod = i * 2 + 1
            val cells = Regex("<td[^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL)
                .findAll(row.groupValues[1]).toList()
            for ((cellIndex, cell) in cells.withIndex()) {
                if (cellIndex == 0) continue // 第 0 列是节次/时间列，非星期
                val text = Regex("<[^>]+>").replace(cell.groupValues[1], "\n")
                    .replace("&nbsp;", " ").trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (text.size < 2) continue // 空单元格
                val course = parseCell(text, dayOfWeek = cellIndex, startPeriod = startPeriod)
                    ?: continue
                courses += course
            }
        }
        return courses
    }

    private fun parseCell(lines: List<String>, dayOfWeek: Int, startPeriod: Int): Course? {
        val name = lines[0]
        val teacher = lines.getOrNull(1) ?: ""
        val location = lines.firstOrNull {
            it.contains("教") || it.contains("楼") || it.contains("室") || it.contains("馆")
        } ?: ""
        val weekText = lines.firstOrNull { it.contains("周") } ?: ""
        return Course(
            name = name,
            teacher = teacher,
            location = location,
            dayOfWeek = dayOfWeek,
            startPeriod = startPeriod,
            endPeriod = startPeriod + 1,
            weeks = parseWeeks(weekText),
            color = 0,
        )
    }

    private fun parseWeeks(text: String): Int {
        if (text.isBlank()) return 0
        val range = Regex("(\\d+)\\s*-\\s*(\\d+)").find(text)
        val start = range?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val end = range?.groupValues?.get(2)?.toIntOrNull() ?: WeekUtils.MAX_WEEKS
        return when {
            text.contains("单") -> WeekUtils.oddWeeks(start, end)
            text.contains("双") -> WeekUtils.evenWeeks(start, end)
            else -> WeekUtils.maskFor(start, end)
        }
    }
}
