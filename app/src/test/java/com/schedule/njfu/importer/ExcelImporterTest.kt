package com.schedule.njfu.importer

import org.dhatim.fastexcel.Workbook
import org.dhatim.fastexcel.Worksheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ExcelImporterTest {

    private fun buildSampleXlsx(): ByteArray {
        val out = ByteArrayOutputStream()
        val wb = Workbook(out, "test", "1.0")
        val ws: Worksheet = wb.newWorksheet("Sheet1")
        // 表头 + 数据行：课程名,教师,地点,星期,开始节,结束节,周次
        ws.value(0, 0, "课程名"); ws.value(0, 1, "教师"); ws.value(0, 2, "地点")
        ws.value(0, 3, "星期"); ws.value(0, 4, "开始节"); ws.value(0, 5, "结束节"); ws.value(0, 6, "周次")
        ws.value(1, 0, "高等数学"); ws.value(1, 1, "张三"); ws.value(1, 2, "教1-201")
        ws.value(1, 3, "1"); ws.value(1, 4, "1"); ws.value(1, 5, "2"); ws.value(1, 6, "1-16")
        ws.value(2, 0, "大学英语"); ws.value(2, 1, "李四"); ws.value(2, 2, "教2-305")
        ws.value(2, 3, "3"); ws.value(2, 4, "3"); ws.value(2, 5, "4"); ws.value(2, 6, "1-16(单)")
        ws.value(3, 0, "大学物理"); ws.value(3, 1, "王五"); ws.value(3, 2, "理2-105")
        ws.value(3, 3, "5"); ws.value(3, 4, "5"); ws.value(3, 5, "6"); ws.value(3, 6, "2-16(双)")
        ws.value(4, 0, "体育"); ws.value(4, 1, "赵六"); ws.value(4, 2, "体育馆")
        ws.value(4, 3, "2"); ws.value(4, 4, "7"); ws.value(4, 5, "8"); ws.value(4, 6, "单周")
        wb.finish()
        return out.toByteArray()
    }

    @Test
    fun `parses xlsx with header row`() {
        val courses = ExcelImporter.parse(ByteArrayInputStream(buildSampleXlsx()))
        assertEquals(4, courses.size)
        val c1 = courses[0]
        assertEquals("高等数学", c1.name)
        assertEquals(1, c1.dayOfWeek)
        assertEquals(1, c1.startPeriod)
        assertEquals(2, c1.endPeriod)
        assertTrue(com.schedule.njfu.model.WeekUtils.contains(c1.weeks, 8))
    }

    @Test
    fun `odd and even week suffixes produce valid masks`() {
        val courses = ExcelImporter.parse(ByteArrayInputStream(buildSampleXlsx()))
        val odd = courses.first { it.name == "大学英语" }
        assertTrue(com.schedule.njfu.model.WeekUtils.contains(odd.weeks, 1))
        assertTrue(com.schedule.njfu.model.WeekUtils.contains(odd.weeks, 15))
        assertFalse(com.schedule.njfu.model.WeekUtils.contains(odd.weeks, 2))
        val even = courses.first { it.name == "大学物理" }
        assertTrue(com.schedule.njfu.model.WeekUtils.contains(even.weeks, 2))
        assertTrue(com.schedule.njfu.model.WeekUtils.contains(even.weeks, 16))
        assertFalse(com.schedule.njfu.model.WeekUtils.contains(even.weeks, 3))
        val oddOnly = courses.first { it.name == "体育" }
        assertTrue(com.schedule.njfu.model.WeekUtils.contains(oddOnly.weeks, 5))
        assertFalse(com.schedule.njfu.model.WeekUtils.contains(oddOnly.weeks, 6))
    }

    @Test
    fun `chinese weekdays are parsed`() {
        fun parse(dayCell: String): Int? {
            val out = ByteArrayOutputStream()
            val wb = Workbook(out, "test", "1.0")
            val ws: Worksheet = wb.newWorksheet("Sheet1")
            ws.value(0, 0, "课程名"); ws.value(0, 3, "星期")
            ws.value(1, 0, "课"); ws.value(1, 3, dayCell); ws.value(1, 4, "1"); ws.value(1, 6, "1-16")
            wb.finish()
            return ExcelImporter.parse(ByteArrayInputStream(out.toByteArray()))
                .firstOrNull()?.dayOfWeek
        }
        assertEquals(1, parse("星期一"))
        assertEquals(3, parse("周三"))
        assertEquals(5, parse("星期五"))
        assertEquals(7, parse("周日"))
        assertEquals(7, parse("星期天"))
    }

    @Test
    fun `invalid weekday row is skipped and logged`() {
        val out = ByteArrayOutputStream()
        val wb = Workbook(out, "test", "1.0")
        val ws: Worksheet = wb.newWorksheet("Sheet1")
        // 合法行 + 非法星期行 + 合法行
        ws.value(0, 0, "课程名"); ws.value(0, 3, "星期"); ws.value(0, 4, "开始节")
        ws.value(1, 0, "高等数学"); ws.value(1, 3, "1"); ws.value(1, 4, "1")
        ws.value(2, 0, "坏星期课"); ws.value(2, 3, "星期八"); ws.value(2, 4, "1")
        ws.value(3, 0, "线性代数"); ws.value(3, 3, "2"); ws.value(3, 4, "3")
        wb.finish()
        val courses = ExcelImporter.parse(ByteArrayInputStream(out.toByteArray()))
        assertEquals("非法星期行应被跳过", 2, courses.size)
        assertEquals("高等数学", courses[0].name)
        assertEquals("线性代数", courses[1].name)
    }

    @Test
    fun `invalid start period row is skipped and logged`() {
        val out = ByteArrayOutputStream()
        val wb = Workbook(out, "test", "1.0")
        val ws: Worksheet = wb.newWorksheet("Sheet1")
        ws.value(0, 0, "课程名"); ws.value(0, 3, "星期"); ws.value(0, 4, "开始节")
        ws.value(1, 0, "高等数学"); ws.value(1, 3, "1"); ws.value(1, 4, "1")
        ws.value(2, 0, "坏节课"); ws.value(2, 3, "3"); ws.value(2, 4, "abc")
        ws.value(3, 0, "线性代数"); ws.value(3, 3, "2"); ws.value(3, 4, "3")
        wb.finish()
        val courses = ExcelImporter.parse(ByteArrayInputStream(out.toByteArray()))
        assertEquals("非法开始节行应被跳过", 2, courses.size)
    }
}
