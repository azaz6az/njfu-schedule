package com.schedule.njfu.ui.schedule

import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils

object WeekGrid {
    /** 网格最大节数（南林晚课至 11-12 节） */
    const val MAX_PERIODS = 12

    /**
     * 课程在网格中的行列映射：row = startPeriod-1（0 基），column = dayOfWeek-1。
     * 同一天内节次区间重叠（含部分重叠，如 1-2 与 1-4）的多门课，通过
     * [overlapIndex]/[overlapCount] 并排显示，避免互相遮挡；不重叠的课程
     * 复用同一列，不压缩卡片宽度。
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
        return visible.groupBy { it.dayOfWeek }.flatMap { (_, dayCourses) ->
            // 贪心列分配：按开始节升序遍历，放入第一个与已有课程不重叠的列，否则新建列
            val columns = mutableListOf<MutableList<Course>>()
            val columnOf = mutableMapOf<Course, Int>()
            for (c in dayCourses) {
                val idx = columns.indexOfFirst { col -> col.all { it.endPeriod < c.startPeriod } }
                if (idx >= 0) {
                    columns[idx].add(c)
                    columnOf[c] = idx
                } else {
                    columns.add(mutableListOf(c))
                    columnOf[c] = columns.size - 1
                }
            }
            dayCourses.map { c ->
                Cell(
                    course = c,
                    row = (c.startPeriod - 1).coerceIn(0, MAX_PERIODS - 1),
                    rowSpan = (c.endPeriod - c.startPeriod + 1).coerceIn(1, MAX_PERIODS),
                    col = (c.dayOfWeek - 1).coerceIn(0, 6),
                    overlapIndex = columnOf.getValue(c),
                    overlapCount = columns.size.coerceAtLeast(1),
                )
            }
        }
    }
}
