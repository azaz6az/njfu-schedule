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
        // 表头 + 数据行：课程名,教师,地点,星期,开始节,结束节,周次(如"1-16"或"单周")
        ws.value(0, 0, "课程名"); ws.value(0, 1, "教师"); ws.value(0, 2, "地点")
        ws.value(0, 3, "星期"); ws.value(0, 4, "开始节"); ws.value(0, 5, "结束节"); ws.value(0, 6, "周次")
        ws.value(1, 0, "高等数学"); ws.value(1, 1, "张三"); ws.value(1, 2, "教1-201")
        ws.value(1, 3, "1"); ws.value(1, 4, "1"); ws.value(1, 5, "2"); ws.value(1, 6, "1-16")
        ws.value(2, 0, "大学物理"); ws.value(2, 1, "李四"); ws.value(2, 2, "理2-105")
        ws.value(2, 3, "3"); ws.value(2, 4, "3"); ws.value(2, 5, "4"); ws.value(2, 6, "单周")
        wb.finish()
        return out.toByteArray()
    }

    @Test
    fun `parses xlsx with header row`() {
        val courses = ExcelImporter.parse(ByteArrayInputStream(buildSampleXlsx()))
        assertEquals(2, courses.size)
        val c1 = courses[0]
        assertEquals("高等数学", c1.name)
        assertEquals(1, c1.dayOfWeek)
        assertEquals(1, c1.startPeriod)
        assertEquals(2, c1.endPeriod)
        assertTrue(com.schedule.njfu.model.WeekUtils.contains(c1.weeks, 8))
        val c2 = courses[1]
        assertTrue(com.schedule.njfu.model.WeekUtils.contains(c2.weeks, 1))
        assertFalse(com.schedule.njfu.model.WeekUtils.contains(c2.weeks, 2))
    }
}
