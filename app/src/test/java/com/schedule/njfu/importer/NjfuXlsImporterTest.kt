package com.schedule.njfu.importer

import com.schedule.njfu.model.WeekUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NjfuXlsImporterTest {

    @Test
    fun `parses weeks with comma-separated ranges`() {
        // 1-6,8-10,12-13 → 跳过第 7、11 周
        val mask = NjfuXlsImporter.parseWeeks("1-6,8-10,12-13([周])[01-02节]")
        assertTrue(WeekUtils.contains(mask, 6))
        assertTrue(WeekUtils.contains(mask, 8))
        assertTrue(WeekUtils.contains(mask, 13))
        assertTrue(!WeekUtils.contains(mask, 7))
        assertTrue(!WeekUtils.contains(mask, 11))
    }

    @Test
    fun `parses single week and single-range`() {
        val m1 = NjfuXlsImporter.parseWeeks("3([周])[03-04节]")
        assertTrue(WeekUtils.contains(m1, 3))
        assertTrue(!WeekUtils.contains(m1, 4))

        val m2 = NjfuXlsImporter.parseWeeks("1-12([周])[05-06节]")
        assertTrue(WeekUtils.contains(m2, 1))
        assertTrue(WeekUtils.contains(m2, 12))
    }

    @Test
    fun `parses week text with mixed single and range`() {
        // 1,3-12 → 含 1 和 3..12，不含 2
        val mask = NjfuXlsImporter.parseWeeks("1,3-12([周])[05-06节]")
        assertTrue(WeekUtils.contains(mask, 1))
        assertTrue(WeekUtils.contains(mask, 3))
        assertTrue(WeekUtils.contains(mask, 12))
        assertTrue(!WeekUtils.contains(mask, 2))
    }

    @Test
    fun `chineseToInt converts period labels`() {
        assertEquals(1, NjfuXlsImporter.chineseToInt("一"))
        assertEquals(5, NjfuXlsImporter.chineseToInt("五"))
        assertEquals(10, NjfuXlsImporter.chineseToInt("十"))
        assertEquals(12, NjfuXlsImporter.chineseToInt("十二"))
        assertEquals(null, NjfuXlsImporter.chineseToInt("大节"))
    }

    @Test
    fun parsesRealExportedScheduleXls() {
        // 相对 CWD 的 File 路径在 Gradle 与 IDE 下不可靠，改用 classpath 资源加载（与 JwxtParserTest 一致）
        val f = javaClass.classLoader!!.getResource("fixtures/njfu_schedule_sample.xls")
        assertTrue("缺少 fixture", f != null)
        val cs = f!!.openStream().use { NjfuXlsImporter.parse(it) }
        assertTrue("应解析出课程，实际 " + cs.size, cs.isNotEmpty())

        // 多元统计分析方法：周一 第一大节(01-02节) 1-12周 地点50613
        val stat = cs.firstOrNull { it.name == "多元统计分析方法" && it.dayOfWeek == 1 }
        assertTrue("应有 多元统计分析方法", stat != null)
        assertEquals("50613", stat!!.location)
        assertEquals(1, stat.startPeriod)
        assertEquals(2, stat.endPeriod)
        assertTrue(WeekUtils.contains(stat.weeks, 1))
        assertTrue(WeekUtils.contains(stat.weeks, 12))

        // 区块链技术与应用：周五 第一大节 1-6,8-10,12-13（跳过 7、11 周）
        val block = cs.firstOrNull { it.name == "区块链技术与应用" && it.dayOfWeek == 5 }
        assertTrue("应有 区块链技术与应用（周五）", block != null)
        assertTrue(WeekUtils.contains(block!!.weeks, 10))
        assertTrue(!WeekUtils.contains(block.weeks, 7))
        assertTrue(!WeekUtils.contains(block.weeks, 11))
    }
}
