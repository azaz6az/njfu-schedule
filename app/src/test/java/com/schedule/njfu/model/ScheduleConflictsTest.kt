package com.schedule.njfu.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleConflictsTest {

    private fun course(
        name: String,
        day: Int,
        start: Int,
        end: Int,
        weeks: Int = WeekUtils.maskFor(1, 16),
        id: Long = 0,
    ) = Course(name = name, dayOfWeek = day, startPeriod = start, endPeriod = end,
        weeks = weeks, color = 0, id = id)

    @Test
    fun `same slot overlaps`() {
        assertTrue(WeekUtils.overlaps(course("A", 1, 1, 2), course("B", 1, 1, 2)))
    }

    @Test
    fun `partial overlap detected`() {
        // 1-2 与 1-4 共享 1-2 节
        assertTrue(WeekUtils.overlaps(course("A", 1, 1, 2), course("B", 1, 1, 4)))
        assertTrue(WeekUtils.overlaps(course("A", 3, 2, 4), course("B", 3, 4, 6)))
    }

    @Test
    fun `adjacent periods do not overlap`() {
        // 1-2 与 3-4：紧邻不重叠
        assertEquals(false, WeekUtils.overlaps(course("A", 1, 1, 2), course("B", 1, 3, 4)))
    }

    @Test
    fun `different days do not overlap`() {
        assertEquals(false, WeekUtils.overlaps(course("A", 1, 1, 2), course("B", 2, 1, 2)))
    }

    @Test
    fun `disjoint weeks do not overlap`() {
        val a = course("A", 1, 1, 2, weeks = WeekUtils.maskFor(1, 8))
        val b = course("B", 1, 1, 2, weeks = WeekUtils.maskFor(9, 16))
        assertEquals(false, WeekUtils.overlaps(a, b))
    }

    @Test
    fun `find conflicts lists overlapping courses`() {
        val existing = listOf(
            course("高数", 1, 1, 2, id = 1),
            course("英语", 1, 3, 4, id = 2),
        )
        // 候选 1-2 节：与高数（1-2）重叠，与英语（3-4）不重叠
        val conflicts = WeekUtils.findConflicts(existing, course("体育", 1, 1, 2))
        assertEquals(1, conflicts.size)
        assertEquals("高数", conflicts[0].name)
    }

    @Test
    fun `editing a course does not conflict with itself`() {
        val existing = listOf(course("高数", 1, 1, 2, id = 1))
        // 编辑高数本身（改地点），不应与自身冲突
        val edited = existing[0].copy(location = "新教室")
        assertEquals(0, WeekUtils.findConflicts(existing, edited).size)
    }

    @Test
    fun `no conflicts when slot free`() {
        val existing = listOf(course("高数", 1, 1, 2, id = 1))
        assertEquals(0, WeekUtils.findConflicts(existing, course("英语", 2, 1, 2)).size)
    }
}
