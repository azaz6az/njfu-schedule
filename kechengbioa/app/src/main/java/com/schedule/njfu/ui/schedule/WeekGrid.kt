package com.schedule.njfu.ui.schedule

import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils

object WeekGrid {
    /** 网格最大节数（南林晚课至 11-12 节） */
    const val MAX_PERIODS = 12

    /**
     * 课程在网格中的行列映射：row = startPeriod-1（0 基），column = dayOfWeek-1。
     * 同格（同天同节次）多门课通过 [overlapIndex]/[overlapCount] 并排显示，避免互相遮挡。
     */
    data class Cell(
        val course: Course,
        val row: Int,
        val rowSpan: Int,
        val col: Int,
        val overlapIndex: Int,
        val overlapCount: Int,
    )

    fun cellsFor(courses: List<Course>, week: Int, currentDay: Int = 0): List<Cell> {
        // weeks == 0 视为“周次信息缺失”（历史脏数据兜底），按全学期显示，避免课程凭空消失
        val visible = courses
            .filter { WeekUtils.contains(it.weeks, week) || it.weeks == 0 }
            .sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }, { it.endPeriod }))
        val grouped = visible.groupBy { Triple(it.dayOfWeek, it.startPeriod, it.endPeriod) }
        return visible.map { c ->
            val key = Triple(c.dayOfWeek, c.startPeriod, c.endPeriod)
            val group = grouped.getValue(key)
            Cell(
                course = c,
                row = (c.startPeriod - 1).coerceIn(0, MAX_PERIODS - 1),
                rowSpan = (c.endPeriod - c.startPeriod + 1).coerceIn(1, MAX_PERIODS),
                col = (c.dayOfWeek - 1).coerceIn(0, 6),
                overlapIndex = group.indexOf(c).coerceAtLeast(0),
                overlapCount = group.size.coerceAtLeast(1),
            )
        }
    }
}
