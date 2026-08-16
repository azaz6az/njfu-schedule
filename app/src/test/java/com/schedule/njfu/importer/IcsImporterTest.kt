package com.schedule.njfu.importer

import com.schedule.njfu.model.WeekUtils
import java.time.LocalDate
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IcsImporterTest {

    private var originalTz: TimeZone? = null

    @Before
    fun fixZoneToUtc() {
        // 固定系统时区为 UTC，让 Z 后缀换算断言确定且可移植
        originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreZone() {
        TimeZone.setDefault(originalTz)
    }

    // 2026-09-01 是周二。课程：每周二 10:00-11:00 教1-101，2026-09-01 到 2026-11-24（13 周）
    private val ics = """
BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//test//EN
BEGIN:VEVENT
UID:1@test
SUMMARY:大学英语
LOCATION:教1-101
DTSTART:20260901T100000Z
DTEND:20260901T110000Z
RRULE:FREQ=WEEKLY;COUNT=13;BYDAY=TU
END:VEVENT
END:VCALENDAR
""".trimIndent()

    @Test
    fun `parses weekly recurring event with utc z suffix`() {
        // 系统时区固定为 UTC，故 Z 后缀的 10:00 换算后仍是本地 10:00
        val courses = IcsImporter.parse(ics)
        assertEquals(1, courses.size)
        val c = courses[0]
        assertEquals("大学英语", c.name)
        assertEquals("教1-101", c.location)
        assertEquals(2, c.dayOfWeek)          // 周二
        assertEquals(3, c.startPeriod)        // 10:00 → 默认节次表第 3 节(10:00)
        assertEquals(4, c.endPeriod)          // 11:00 下课 → 第 4 节(11:00) 结束
        assertTrue(WeekUtils.contains(c.weeks, 1))
        assertTrue(WeekUtils.contains(c.weeks, 13))
        assertFalse(WeekUtils.contains(c.weeks, 14))
    }

    @Test
    fun `no timezone suffix is treated as local time`() {
        val noSuffix = ics.replace("20260901T100000Z", "20260901T100000")
            .replace("20260901T110000Z", "20260901T110000")
        val c = IcsImporter.parse(noSuffix)[0]
        // 无后缀视为本地时间并入系统时区：UTC 下 10:00 不变 → 仍是第 3 节
        assertEquals(3, c.startPeriod)
        assertEquals(4, c.endPeriod)
    }

    @Test
    fun `negative offset is converted to local time`() {
        // UTC 系统时区下，10:00-0800(Utc+偏移前的瞬时 = 18:00 UTC)？10:00+0800 表示东八区 10:00 → UTC 02:00
        // 系统时区 UTC → 本地 02:00 → 第 1 节(08:00)
        val withOffset = ics.replace("20260901T100000Z", "20260901T100000+0800")
            .replace("20260901T110000Z", "20260901T110000+0800")
        val c = IcsImporter.parse(withOffset)[0]
        assertEquals(1, c.startPeriod)
    }

    @Test
    fun `semesterStart shifts weeks to semester week number`() {
        // semesterStart=2026-08-24，DTSTART=2026-09-01 位于第 2 周
        val start = LocalDate.of(2026, 8, 24)
        val c = IcsImporter.parse(ics, start)[0]
        assertFalse("起始周应从第 2 周开始", WeekUtils.contains(c.weeks, 1))
        assertTrue("第 2 周应有课", WeekUtils.contains(c.weeks, 2))
        assertTrue("第 14 周应有课（第 2+13-1=14）", WeekUtils.contains(c.weeks, 14))
        assertFalse("第 15 周无课", WeekUtils.contains(c.weeks, 15))
    }

    @Test
    fun `semesterStart with event on semester week one`() {
        // semesterStart=2026-08-31（本周一），DTSTART=2026-09-01 → 第 1 周
        val c = IcsImporter.parse(ics, LocalDate.of(2026, 8, 31))[0]
        assertTrue(WeekUtils.contains(c.weeks, 1))
        assertTrue(WeekUtils.contains(c.weeks, 13))
    }

    @Test
    fun `count beyond MAX_WEEKS is clamped`() {
        val tooMany = ics.replace("COUNT=13", "COUNT=50")
        val c = IcsImporter.parse(tooMany)[0]
        assertTrue("应含第 30 周", WeekUtils.contains(c.weeks, 30))
        assertFalse("不应含第 31 周", WeekUtils.contains(c.weeks, 31))
    }

    @Test
    fun `skips non-recurring events without rule`() {
        val oneOff = "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nUID:2\nSUMMARY:单次活动\n" +
            "DTSTART:20260901T100000Z\nDTEND:20260901T110000Z\nEND:VEVENT\nEND:VCALENDAR"
        assertEquals(0, IcsImporter.parse(oneOff).size)
    }
}
