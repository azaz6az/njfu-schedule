package com.schedule.njfu.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    // ---- parseWeeksText（统一周次解析器） ----

    @Test
    fun `parseWeeksText supports ranges and lists`() {
        assertEquals(WeekUtils.maskFor(1, 16), WeekUtils.parseWeeksText("1-16"))
        val m = WeekUtils.parseWeeksText("1,3,5")
        assertTrue(WeekUtils.contains(m, 1))
        assertTrue(WeekUtils.contains(m, 3))
        assertTrue(WeekUtils.contains(m, 5))
        assertFalse(WeekUtils.contains(m, 2))
    }

    @Test
    fun `parseWeeksText supports jwxt page formats`() {
        assertEquals(WeekUtils.maskFor(1, 12), WeekUtils.parseWeeksText("1-12(周)"))
        assertEquals(WeekUtils.maskFor(3), WeekUtils.parseWeeksText("3(周)"))
        assertEquals(WeekUtils.maskFor(1, 12), WeekUtils.parseWeeksText("1-12(周)[01-02节]"))
        val m = WeekUtils.parseWeeksText("1-6,8-10,12-13(周)[03-04节]")
        assertTrue(WeekUtils.contains(m, 10))
        assertFalse(WeekUtils.contains(m, 7))
        assertFalse(WeekUtils.contains(m, 11))
    }

    @Test
    fun `parseWeeksText supports xls export formats`() {
        assertEquals(WeekUtils.maskFor(1, 12), WeekUtils.parseWeeksText("1-12([周])[01-02节]"))
        val m = WeekUtils.parseWeeksText("1-6,8-10,12-13([周])[03-04节]")
        assertTrue(WeekUtils.contains(m, 13))
        assertFalse(WeekUtils.contains(m, 11))
    }

    @Test
    fun `parseWeeksText supports odd and even suffixes`() {
        val odd = WeekUtils.parseWeeksText("1-16(单)")
        assertTrue(WeekUtils.contains(odd, 1))
        assertTrue(WeekUtils.contains(odd, 15))
        assertFalse(WeekUtils.contains(odd, 2))
        val even = WeekUtils.parseWeeksText("2-16（双）")
        assertTrue(WeekUtils.contains(even, 2))
        assertTrue(WeekUtils.contains(even, 16))
        assertFalse(WeekUtils.contains(even, 1))
        assertFalse(WeekUtils.contains(even, 3))
        val oddOnly = WeekUtils.parseWeeksText("单周")
        assertTrue(WeekUtils.contains(oddOnly, 1))
        assertFalse(WeekUtils.contains(oddOnly, 2))
    }

    @Test
    fun `parseWeeksText returns zero for unparseable text`() {
        assertEquals(0, WeekUtils.parseWeeksText(""))
        assertEquals(0, WeekUtils.parseWeeksText("随便写点什么"))
    }

    @Test
    fun `fixMissingWeeks turns zero masks into full term`() {
        val a = Course(name = "A", dayOfWeek = 1, startPeriod = 1, endPeriod = 2, weeks = 0, color = 0)
        val b = Course(name = "B", dayOfWeek = 1, startPeriod = 3, endPeriod = 4,
            weeks = WeekUtils.maskFor(1, 8), color = 0)
        val (fixed, count) = WeekUtils.fixMissingWeeks(listOf(a, b))
        assertEquals(1, count)
        assertEquals(WeekUtils.maskFor(1, WeekUtils.MAX_WEEKS), fixed[0].weeks)
        assertEquals(WeekUtils.maskFor(1, 8), fixed[1].weeks)
    }

    @Test
    fun `chineseToInt converts block labels`() {
        assertEquals(1, WeekUtils.chineseToInt("一"))
        assertEquals(6, WeekUtils.chineseToInt("六"))
        assertEquals(10, WeekUtils.chineseToInt("十"))
        assertEquals(12, WeekUtils.chineseToInt("十二"))
        assertEquals(null, WeekUtils.chineseToInt("大节"))
    }
}
