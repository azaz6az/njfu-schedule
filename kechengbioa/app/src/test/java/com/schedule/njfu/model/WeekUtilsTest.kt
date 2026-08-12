package com.schedule.njfu.model

import org.junit.Assert.*
import org.junit.Test

class WeekUtilsTest {

    @Test
    fun `bitmask supports continuous weeks`() {
        val mask = WeekUtils.maskFor(1, 16)
        assertTrue(WeekUtils.contains(mask, 1))
        assertTrue(WeekUtils.contains(mask, 8))
        assertTrue(WeekUtils.contains(mask, 16))
        assertFalse(WeekUtils.contains(mask, 17))
        assertFalse(WeekUtils.contains(mask, 0))
    }

    @Test
    fun `bitmask supports odd weeks`() {
        val mask = WeekUtils.oddWeeks(1, 17)
        assertTrue(WeekUtils.contains(mask, 1))
        assertTrue(WeekUtils.contains(mask, 17))
        assertFalse(WeekUtils.contains(mask, 2))
    }

    @Test
    fun `bitmask supports even weeks`() {
        val mask = WeekUtils.evenWeeks(1, 16)
        assertTrue(WeekUtils.contains(mask, 2))
        assertFalse(WeekUtils.contains(mask, 1))
    }

    @Test
    fun `bitmask supports arbitrary combinations`() {
        val mask = WeekUtils.maskFor(2) or WeekUtils.maskFor(5) or WeekUtils.maskFor(9)
        assertTrue(WeekUtils.contains(mask, 2))
        assertTrue(WeekUtils.contains(mask, 5))
        assertFalse(WeekUtils.contains(mask, 3))
    }

    @Test
    fun `currentWeek from semester start date`() {
        // 2026-09-14 是周一（学期起始日）；第 2 周的周三 = 2026-09-23
        val week = WeekUtils.currentWeek(start = java.time.LocalDate.of(2026, 9, 14),
                                         today = java.time.LocalDate.of(2026, 9, 23))
        assertEquals(2, week)
    }

    @Test
    fun `currentWeek before semester start is week 1`() {
        val week = WeekUtils.currentWeek(start = java.time.LocalDate.of(2026, 9, 14),
                                         today = java.time.LocalDate.of(2026, 8, 1))
        assertEquals(1, week)
    }

    @Test
    fun `period times expand to hourly slots`() {
        val times = listOf(1 to "08:00", 2 to "09:00", 3 to "10:00", 4 to "11:00", 5 to "14:00")
        val start = WeekUtils.startTimeOf(1, times)
        val end = WeekUtils.endTimeOf(4, times)
        assertEquals("08:00", start)
        assertEquals("12:00", end) // 第4节 11:00 + 1h
    }
}
