package com.schedule.njfu.importer

import com.schedule.njfu.importer.njfu.JwxtParser
import com.schedule.njfu.model.WeekUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JwxtParserTest {

    private fun fixture(): String =
        javaClass.classLoader!!.getResource("fixtures/njfu_schedule_sample.html")!!.readText()

    @Test
    fun `parses real schedule page into 21 courses`() {
        val courses = JwxtParser.parseSchedule(fixture())
        assertEquals("真实课表应解析出 21 个课程块", 21, courses.size)
    }

    @Test
    fun `course fields are populated from font titles`() {
        val courses = JwxtParser.parseSchedule(fixture())
        val stat = courses.first { it.name == "多元统计分析方法" && it.dayOfWeek == 1 }
        assertEquals("耿阳", stat.teacher)
        assertEquals("教五楼 50613", stat.location)
        assertEquals(1, stat.startPeriod)
        assertEquals(2, stat.endPeriod)
        assertTrue(WeekUtils.contains(stat.weeks, 1))
        assertTrue(WeekUtils.contains(stat.weeks, 12))
        assertFalse(WeekUtils.contains(stat.weeks, 13))
    }

    @Test
    fun `multi-block cell splits into separate courses`() {
        // 周四第二大节：区块链 3周/7周/8周 + 大数据 11-13周，共 4 块
        val courses = JwxtParser.parseSchedule(fixture())
        val cell = courses.filter { it.dayOfWeek == 4 && it.startPeriod == 3 && it.endPeriod == 4 }
        assertEquals(4, cell.size)
        val block = cell.first { it.name == "区块链技术与应用" && it.location == "教五楼 50218" }
        assertTrue(WeekUtils.contains(block.weeks, 3))
        assertFalse(WeekUtils.contains(block.weeks, 4))
        val big = cell.first { it.name == "大数据挖掘与可视化" }
        assertEquals("房银海", big.teacher)
        assertTrue(WeekUtils.contains(big.weeks, 11))
        assertTrue(WeekUtils.contains(big.weeks, 13))
    }

    @Test
    fun `comma separated week ranges`() {
        val courses = JwxtParser.parseSchedule(fixture())
        val block = courses.first { it.name == "区块链技术与应用" && it.dayOfWeek == 5 }
        assertTrue(WeekUtils.contains(block.weeks, 8))
        assertTrue(WeekUtils.contains(block.weeks, 10))
        assertFalse(WeekUtils.contains(block.weeks, 7))
        assertFalse(WeekUtils.contains(block.weeks, 11))
    }

    @Test
    fun `remark row is skipped`() {
        val courses = JwxtParser.parseSchedule(fixture())
        assertTrue("备注行不应生成课程", courses.none { it.name.contains("课程设计") })
        assertTrue(courses.none { it.name.contains("备注") })
        assertTrue("不应出现第 11 节以上的伪课程", courses.none { it.startPeriod > 10 })
    }

    @Test
    fun `detects login redirect page`() {
        // 未登录时 jsxsd 返回 200 + JS 跳转脚本（非 302）
        val loginPage = "<html><script languge='javascript'>window.location.href=" +
            "'https://uia.njfu.edu.cn/authserver/login?service=x'</script></html>"
        assertTrue(JwxtParser.isLoginRedirect(loginPage))
        assertFalse(JwxtParser.isLoginRedirect(fixture()))
    }

    @Test
    fun `detects schedule page structure`() {
        assertTrue(JwxtParser.looksLikeSchedulePage(fixture()))
        assertFalse(JwxtParser.looksLikeSchedulePage("<html><head><title>登录</title></head><body></body></html>"))
    }
}
