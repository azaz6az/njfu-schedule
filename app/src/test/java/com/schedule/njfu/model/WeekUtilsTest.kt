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
    fun `currentWeek normalizes non-monday start to monday boundary`() {
        // 开学日 2026-09-02 是周三：先归一化到当周周一 2026-08-31 再算周差
        val start = java.time.LocalDate.of(2026, 9, 2)
        // 开学当天（周三）与同周周日（9-06）都是第 1 周
        assertEquals(1, WeekUtils.currentWeek(start, java.time.LocalDate.of(2026, 9, 2)))
        assertEquals(1, WeekUtils.currentWeek(start, java.time.LocalDate.of(2026, 9, 6)))
        // 归一化周一（8-31）当天也算第 1 周；其前一天返回 1（早于开学周）
        assertEquals(1, WeekUtils.currentWeek(start, java.time.LocalDate.of(2026, 8, 31)))
        assertEquals(1, WeekUtils.currentWeek(start, java.time.LocalDate.of(2026, 8, 30)))
        // 下周一（9-07）起进入第 2 周
        assertEquals(2, WeekUtils.currentWeek(start, java.time.LocalDate.of(2026, 9, 7)))
        assertEquals(2, WeekUtils.currentWeek(start, java.time.LocalDate.of(2026, 9, 13)))
    }

    @Test
    fun `currentWeek clamps beyond max weeks`() {
        val start = java.time.LocalDate.of(2020, 9, 7) // 周一
        assertEquals(1, WeekUtils.currentWeek(start, start))
        // 第 41 周（远超 30 周学期）→ 封顶 MAX_WEEKS
        val week = WeekUtils.currentWeek(start, start.plusWeeks(40))
        assertEquals(WeekUtils.MAX_WEEKS, week)
    }

    @Test
    fun `maskFor clamps ranges beyond max weeks`() {
        // 区间 clamp：越界部分被截断，结果与 1..MAX_WEEKS 一致（Int 位掩码只有 32 位，防溢出）
        assertEquals(WeekUtils.maskFor(1, WeekUtils.MAX_WEEKS), WeekUtils.maskFor(1, 40))
        assertEquals(WeekUtils.maskFor(25, WeekUtils.MAX_WEEKS), WeekUtils.maskFor(25, 40))
        // 越界单周返回 0（此前 1 shl (0-1) / 1 shl 30 会产生无意义或符号位结果）
        assertEquals(0, WeekUtils.maskFor(0))
        assertEquals(0, WeekUtils.maskFor(-1))
        assertEquals(0, WeekUtils.maskFor(31))
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
    fun `parseWeeksText supports per-segment odd suffixes like gxu v9`() {
        // 正方 V9 实测（广西大学课表页）："1-5周,7-11周(单),12-15周"
        // 只有带 (单) 的段按单周过滤，其余段全周
        val m = WeekUtils.parseWeeksText("1-5周,7-11周(单),12-15周")
        assertTrue(WeekUtils.contains(m, 1))
        assertTrue(WeekUtils.contains(m, 5))
        assertTrue(WeekUtils.contains(m, 7))
        assertTrue(WeekUtils.contains(m, 9))
        assertFalse("7-11(单) 的偶数周应无课", WeekUtils.contains(m, 8))
        assertFalse("7-11(单) 的偶数周应无课", WeekUtils.contains(m, 10))
        assertTrue("无标记的 12-15 段应为全周", WeekUtils.contains(m, 12))
        assertTrue(WeekUtils.contains(m, 13))
        assertTrue(WeekUtils.contains(m, 14))
        assertTrue(WeekUtils.contains(m, 15))
    }

    @Test
    fun `parseWeeksText keeps global suffix behavior for single odd range`() {
        // 整体单周标记依旧生效
        val odd = WeekUtils.parseWeeksText("1-16(单)")
        assertTrue(WeekUtils.contains(odd, 1))
        assertTrue(WeekUtils.contains(odd, 15))
        assertFalse(WeekUtils.contains(odd, 16))
    }

    @Test
    fun `parseWeeksText returns zero for unparseable text`() {
        assertEquals(0, WeekUtils.parseWeeksText(""))
        assertEquals(0, WeekUtils.parseWeeksText("随便写点什么"))
    }

    @Test
    fun `parseWeeksText strips week chars inside text`() {
        // 「周」在字符串中部（非行尾）的变体：应全部剔除后按段解析
        val m = WeekUtils.parseWeeksText("1-12周,14-16周")
        assertTrue(WeekUtils.contains(m, 12))
        assertTrue(WeekUtils.contains(m, 14))
        assertFalse(WeekUtils.contains(m, 13))
        // 中间含周 + (单) 后缀：去周后仍按单周过滤
        val odd = WeekUtils.parseWeeksText("1-16周(单)")
        assertTrue(WeekUtils.contains(odd, 1))
        assertTrue(WeekUtils.contains(odd, 15))
        assertFalse(WeekUtils.contains(odd, 2))
    }

    @Test
    fun `endTimeOf handles missing and malformed times`() {
        // 空节次 / 无此节次 → ""
        assertEquals("", WeekUtils.endTimeOf(1, emptyList()))
        assertEquals("", WeekUtils.endTimeOf(2, listOf(1 to "08:00")))
        // 空白字符串（elvis 拦不住的 ""）→ ""
        assertEquals("", WeekUtils.endTimeOf(1, listOf(1 to "")))
        // 坏格式 / 缺分钟 → ""（不抛 NumberFormatException）
        assertEquals("", WeekUtils.endTimeOf(1, listOf(1 to "bad")))
        assertEquals("", WeekUtils.endTimeOf(1, listOf(1 to "8")))
        // 正常：第 1 节 08:00 → 结束 09:00；HH:mm 单位小时也兼容
        assertEquals("09:00", WeekUtils.endTimeOf(1, listOf(1 to "08:00")))
        assertEquals("22:00", WeekUtils.endTimeOf(4, listOf(4 to "21:00")))
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
