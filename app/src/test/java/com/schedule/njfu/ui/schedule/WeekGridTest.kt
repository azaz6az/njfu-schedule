package com.schedule.njfu.ui.schedule

import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekGridTest {

    private fun course(name: String, day: Int, start: Int, end: Int, weeks: Int) =
        Course(name = name, dayOfWeek = day, startPeriod = start, endPeriod = end,
            weeks = weeks, color = 0)

    @Test
    fun `filters courses not in the week`() {
        val c1 = course("A", 1, 1, 2, WeekUtils.maskFor(1, 8))
        val c2 = course("B", 1, 1, 2, WeekUtils.maskFor(9, 16))
        val cells = WeekGrid.cellsFor(listOf(c1, c2), week = 5)
        assertEquals(1, cells.size)
        assertEquals("A", cells[0].course.name)
    }

    @Test
    fun `maps day and period to grid coordinates`() {
        val c = course("高数", day = 3, start = 2, end = 4, weeks = WeekUtils.maskFor(1, 16))
        val cells = WeekGrid.cellsFor(listOf(c), week = 1)
        assertEquals(3, cells[0].col + 1)          // col 0 基 → dayOfWeek 3
        assertEquals(2, cells[0].row + 1)          // row 0 基 → startPeriod 2
        assertEquals(3, cells[0].rowSpan)          // 2-4 节 = 3 行
    }

    @Test
    fun `sorts by day then period`() {
        val c1 = course("晚课", day = 1, start = 9, end = 10, weeks = WeekUtils.maskFor(1, 16))
        val c2 = course("早课", day = 1, start = 1, end = 2, weeks = WeekUtils.maskFor(1, 16))
        val cells = WeekGrid.cellsFor(listOf(c1, c2), week = 1)
        assertEquals("早课", cells[0].course.name)
    }
}
