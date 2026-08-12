package com.schedule.njfu.data

import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils
import org.junit.Assert.assertEquals
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
}
