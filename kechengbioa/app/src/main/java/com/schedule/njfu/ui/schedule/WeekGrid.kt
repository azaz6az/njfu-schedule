package com.schedule.njfu.ui.schedule

import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils

object WeekGrid {
    const val MAX_PERIODS = 10

    /** 课程在网格中的行列映射：row = startPeriod-1（0 基），column = dayOfWeek-1 */
    data class Cell(val course: Course, val row: Int, val rowSpan: Int, val col: Int)

    fun cellsFor(courses: List<Course>, week: Int, currentDay: Int = 0): List<Cell> {
        val visible = courses
            .filter { WeekUtils.contains(it.weeks, week) }
            .sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }))
        return visible.map { c ->
            Cell(
                course = c,
                row = c.startPeriod - 1,
                rowSpan = (c.endPeriod - c.startPeriod + 1).coerceIn(1, MAX_PERIODS),
                col = c.dayOfWeek - 1,
            )
        }
    }
}
