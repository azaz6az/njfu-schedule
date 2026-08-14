package com.schedule.njfu.ui.schedule

import com.schedule.njfu.model.Course
import com.schedule.njfu.model.HolidayUtils
import com.schedule.njfu.model.WeekUtils
import java.time.LocalDate

object WeekGrid {
    /** 网格最大节数（南林晚课至 11-12 节） */
    const val MAX_PERIODS = 12

    /**
     * 课程在网格中的行列映射：row = startPeriod-1（0 基），col = 课程显示的列（0 基）。
     * 默认 col = dayOfWeek-1；传 [weekDates]/[shifts] 时按调休映射计算：
     *   - 某天被映射为星期 X（如 10-11 → 1）：该天所在列显示星期 X 的课程，
     *     且本周内星期 X 不再显示在自然列（顶替语义，一条配置即可表达「补课+原日放假」）
     *   - 某天映射为 0：该列不显示任何课程（放假）
     * 同列内节次区间重叠（含部分重叠，如 1-2 与 1-4）的多门课，通过
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

    /** 课程的自然星期 → 网格列（0 基）；本周不显示返回 -1；weekDates 不足 7 天时退化为 dayOfWeek-1 */
    fun columnForDay(
        dayOfWeek: Int,
        weekDates: List<LocalDate>,
        shifts: Map<LocalDate, Int>,
    ): Int {
        if (weekDates.size < 7) return (dayOfWeek - 1).coerceIn(0, 6)
        // 1) 星期 X 被某天顶替：课程只显示在映射日所在列
        val replacement = shifts.entries
            .filter { it.value == dayOfWeek }
            .minByOrNull { it.key } // 多日映射取最早日期
        if (replacement != null) {
            val col = weekDates.indexOfFirst { it == replacement.key }
            return if (col in 0..6) col else -1
        }
        // 2) 自然列：若该列日期被映射为其他星期或放假，则本周无课
        val naturalCol = dayOfWeek - 1
        val date = weekDates.getOrNull(naturalCol) ?: return naturalCol
        val shift = shifts[date]
        return if (shift == null || shift == dayOfWeek) naturalCol else -1
    }

    /** 列（0 基）应显示的星期（1-7）：该列日期的映射星期，无映射为自然星期 */
    fun mappedDayForColumn(
        col: Int,
        weekDates: List<LocalDate>,
        shifts: Map<LocalDate, Int>,
    ): Int {
        if (weekDates.size < 7) return (col + 1).coerceIn(1, 7)
        val date = weekDates.getOrNull(col) ?: return (col + 1).coerceIn(1, 7)
        return shifts[date] ?: date.dayOfWeek.value
    }

    fun cellsFor(
        courses: List<Course>,
        week: Int,
        weekDates: List<LocalDate> = emptyList(),
        shifts: Map<LocalDate, Int> = emptyMap(),
    ): List<Cell> {
        // weeks == 0 视为“周次信息缺失”（历史脏数据兜底），按全学期显示，避免课程凭空消失
        val visible = courses
            .filter { WeekUtils.contains(it.weeks, week) || it.weeks == 0 }
            .sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }, { it.endPeriod }))
        return visible
            .mapNotNull { c ->
                val col = columnForDay(c.dayOfWeek, weekDates, shifts)
                if (col < 0) null else c to col
            }
            .groupBy { it.second }
            .flatMap { (col, dayCourses) ->
                // 贪心列分配：按开始节升序遍历，放入第一个与已有课程不重叠的列，否则新建列
                val columns = mutableListOf<MutableList<Course>>()
                val columnOf = mutableMapOf<Course, Int>()
                for ((c, _) in dayCourses) {
                    val idx = columns.indexOfFirst { c2 -> c2.all { it.endPeriod < c.startPeriod } }
                    if (idx >= 0) {
                        columns[idx].add(c)
                        columnOf[c] = idx
                    } else {
                        columns.add(mutableListOf(c))
                        columnOf[c] = columns.size - 1
                    }
                }
                dayCourses.map { (c, _) ->
                    Cell(
                        course = c,
                        row = (c.startPeriod - 1).coerceIn(0, MAX_PERIODS - 1),
                        rowSpan = (c.endPeriod - c.startPeriod + 1).coerceIn(1, MAX_PERIODS),
                        col = col,
                        overlapIndex = columnOf.getValue(c),
                        overlapCount = columns.size.coerceAtLeast(1),
                    )
                }
            }
    }
}
