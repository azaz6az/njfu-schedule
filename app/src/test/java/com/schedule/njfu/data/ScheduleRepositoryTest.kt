package com.schedule.njfu.data

import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleRepositoryTest {

    private fun course(name: String, day: Int, period: Int, weeks: Int) =
        Course(name = name, dayOfWeek = day, startPeriod = period, endPeriod = period,
            weeks = weeks, color = 0)

    @Test
    fun `merge deduplicates identical auto courses`() {
        val a = course("高数", 1, 1, WeekUtils.maskFor(1, 16))
        val merged = ScheduleRepository.merge(auto = listOf(a, a), manual = emptyList())
        assertEquals(1, merged.size)
    }

    @Test
    fun `merge keeps manual courses when same name differs`() {
        val manual = course("高数", 1, 1, WeekUtils.maskFor(1, 16)).copy(source = "manual")
        val merged = ScheduleRepository.merge(auto = emptyList(), manual = listOf(manual))
        assertEquals(1, merged.size)
        assertEquals("manual", merged[0].source)
    }

    // ---- diff 差异对比 ----

    @Test
    fun `diff detects added courses`() {
        val existing = listOf(course("高数", 1, 1, WeekUtils.maskFor(1, 16)))
        val incoming = listOf(
            course("高数", 1, 1, WeekUtils.maskFor(1, 16)),
            course("英语", 2, 1, WeekUtils.maskFor(1, 16)),
        )
        val diff = ScheduleRepository.diff(existing, incoming)
        assertEquals(1, diff.added.size)
        assertEquals("英语", diff.added[0].name)
        assertEquals(0, diff.removed.size)
        assertEquals(1, diff.unchanged.size)
    }

    @Test
    fun `diff detects removed courses`() {
        val existing = listOf(
            course("高数", 1, 1, WeekUtils.maskFor(1, 16)),
            course("英语", 2, 1, WeekUtils.maskFor(1, 16)),
        )
        val incoming = listOf(course("英语", 2, 1, WeekUtils.maskFor(1, 16)))
        val diff = ScheduleRepository.diff(existing, incoming)
        assertEquals(1, diff.removed.size)
        assertEquals("高数", diff.removed[0].name)
        assertEquals(1, diff.unchanged.size)
    }

    @Test
    fun `diff detects changed courses by location`() {
        val existing = listOf(course("高数", 1, 1, WeekUtils.maskFor(1, 16)).copy(location = "教A101"))
        val incoming = listOf(course("高数", 1, 1, WeekUtils.maskFor(1, 16)).copy(location = "教B201"))
        val diff = ScheduleRepository.diff(existing, incoming)
        assertEquals(1, diff.changed.size)
        assertEquals("教A101", diff.changed[0].first.location)
        assertEquals("教B201", diff.changed[0].second.location)
    }

    @Test
    fun `diff detects internal conflicts in incoming`() {
        val incoming = listOf(
            course("高数", 1, 1, WeekUtils.maskFor(1, 16)).copy(endPeriod = 2),
            course("体育", 1, 2, WeekUtils.maskFor(1, 16)).copy(endPeriod = 3),
        )
        val diff = ScheduleRepository.diff(emptyList(), incoming)
        assertEquals(1, diff.conflicts.size)
    }

    @Test
    fun `diff empty when identical`() {
        val existing = listOf(course("高数", 1, 1, WeekUtils.maskFor(1, 16)))
        val diff = ScheduleRepository.diff(existing, existing)
        assertTrue(diff.isEmpty)
        assertEquals(1, diff.unchanged.size)
        assertEquals(1, diff.incomingSize)
    }
}
