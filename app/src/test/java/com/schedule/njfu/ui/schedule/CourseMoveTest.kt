package com.schedule.njfu.ui.schedule

import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [computeMovedCourse]（ScheduleViewModel.moveCourse 的默认 clamp 语义）单测：
 * dayOfWeek clamp、start 上限 clamp（保证 end ≤ 12）、rowSpan 保持、其余字段不变。
 * 纯 JUnit4，无需 Robolectric。
 */
class CourseMoveTest {

    private fun course(
        id: Long = 1L,
        day: Int = 1,
        start: Int = 1,
        end: Int = 2,
    ) = Course(
        id = id,
        name = "高数",
        teacher = "张老师",
        location = "教一 101",
        dayOfWeek = day,
        startPeriod = start,
        endPeriod = end,
        weeks = WeekUtils.maskFor(1, 16),
        color = 0xFF4488AA.toInt(),
        source = "manual",
        note = "备注",
    )

    @Test
    fun `moves to new day and period preserving row span`() {
        val m = computeMovedCourse(course(day = 1, start = 1, end = 2), newDay = 3, newStart = 5)
        assertEquals(3, m.dayOfWeek)
        assertEquals(5, m.startPeriod)
        assertEquals(6, m.endPeriod)
        assertEquals(2, m.endPeriod - m.startPeriod + 1)
        assertEquals(1L, m.id)
    }

    @Test
    fun `clamps day into one to seven`() {
        assertEquals(1, computeMovedCourse(course(), newDay = 0, newStart = 1).dayOfWeek)
        assertEquals(7, computeMovedCourse(course(), newDay = 8, newStart = 1).dayOfWeek)
    }

    @Test
    fun `clamps start so end period does not exceed max`() {
        // 原 5-8 节（rowSpan 4）：最大开始节 = 12-4+1 = 9 → end=12
        val m = computeMovedCourse(course(start = 5, end = 8), newDay = 2, newStart = 12)
        assertEquals(9, m.startPeriod)
        assertEquals(12, m.endPeriod)
    }

    @Test
    fun `clamps start to at least period one`() {
        val m = computeMovedCourse(course(start = 1, end = 2), newDay = 1, newStart = 0)
        assertEquals(1, m.startPeriod)
        assertEquals(2, m.endPeriod)
    }

    @Test
    fun `keeps weeks and other fields unchanged`() {
        val c = course(id = 7, day = 2, start = 3, end = 4)
        val m = computeMovedCourse(c, newDay = 5, newStart = 6)
        assertEquals(c.weeks, m.weeks)
        assertEquals(c.color, m.color)
        assertEquals(c.name, m.name)
        assertEquals(c.teacher, m.teacher)
        assertEquals(c.location, m.location)
        assertEquals(c.source, m.source)
        assertEquals(c.note, m.note)
        assertEquals(c.id, m.id)
    }
}