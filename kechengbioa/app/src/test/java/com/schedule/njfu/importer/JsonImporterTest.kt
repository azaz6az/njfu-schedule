package com.schedule.njfu.importer

import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonImporterTest {

    private val course = Course(
        name = "高等数学", teacher = "张三", location = "教1-201",
        dayOfWeek = 1, startPeriod = 1, endPeriod = 2,
        weeks = WeekUtils.maskFor(1, 16), color = 0xFF3F51B5.toInt(),
    )

    @Test
    fun `export then import roundtrips courses`() {
        val json = JsonImporter.export(listOf(course))
        val imported = JsonImporter.import(json)
        assertEquals(listOf(course), imported)
    }

    @Test
    fun `import empty array is empty list`() {
        assertEquals(emptyList<Course>(), JsonImporter.import("[]"))
    }

    @Test
    fun `import invalid json throws`() {
        try {
            JsonImporter.import("not json")
            throw AssertionError("should throw")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }
}
