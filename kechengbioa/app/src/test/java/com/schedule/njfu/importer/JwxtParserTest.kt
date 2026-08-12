package com.schedule.njfu.importer

import com.schedule.njfu.importer.njfu.JwxtParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JwxtParserTest {

    private fun fixture(): String =
        javaClass.classLoader!!.getResource("fixtures/njfu_schedule_sample.html")!!.readText()

    @Test
    fun `parses schedule table rows`() {
        val courses = JwxtParser.parseSchedule(fixture())
        assertTrue("fixture 应至少解析出 3 门课，实际 ${courses.size}", courses.size >= 3)
    }

    @Test
    fun `fixture course fields are populated`() {
        val courses = JwxtParser.parseSchedule(fixture())
        val first = courses.first()
        assertTrue(first.name.isNotBlank())
        assertTrue(first.dayOfWeek in 1..7)
        assertTrue(first.startPeriod >= 1)
        assertTrue(first.endPeriod >= first.startPeriod)
        assertTrue(first.weeks != 0)
    }

    @Test
    fun `odd week course only contains odd weeks`() {
        val courses = JwxtParser.parseSchedule(fixture())
        val odd = courses.firstOrNull { it.name == "大学英语" }
        assertTrue("未解析到大学英语", odd != null)
        assertTrue(com.schedule.njfu.model.WeekUtils.contains(odd!!.weeks, 1))
        assertFalse(com.schedule.njfu.model.WeekUtils.contains(odd.weeks, 2))
    }
}
