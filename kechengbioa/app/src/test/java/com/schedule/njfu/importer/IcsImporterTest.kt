package com.schedule.njfu.importer

import com.schedule.njfu.model.WeekUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class IcsImporterTest {

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
    fun `parses weekly recurring event`() {
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
    fun `skips non-recurring events without rule`() {
        val oneOff = "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nUID:2\nSUMMARY:单次活动\n" +
            "DTSTART:20260901T100000Z\nDTEND:20260901T110000Z\nEND:VEVENT\nEND:VCALENDAR"
        assertEquals(0, IcsImporter.parse(oneOff).size)
    }
}
