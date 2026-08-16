package com.schedule.njfu.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HolidayUtilsTest {

    @Test
    fun `parse shifts from json`() {
        val shifts = HolidayUtils.parseShifts("""{"2025-10-11":1,"2025-09-28":2}""")
        assertEquals(2, shifts.size)
        assertEquals(1, shifts[LocalDate.parse("2025-10-11")])
        assertEquals(2, shifts[LocalDate.parse("2025-09-28")])
    }

    @Test
    fun `parse ignores invalid entries`() {
        val shifts = HolidayUtils.parseShifts("""{"bad-date":1,"2025-10-11":8,"2025-10-12":3}""")
        assertEquals(1, shifts.size) // 非法日期与越界星期被忽略
        assertEquals(3, shifts[LocalDate.parse("2025-10-12")])
    }

    @Test
    fun `parse accepts zero as day off`() {
        val shifts = HolidayUtils.parseShifts("""{"2025-10-08":0}""")
        assertEquals(0, shifts[LocalDate.parse("2025-10-08")])
        assertEquals(0, HolidayUtils.shiftedDayOfWeek(LocalDate.parse("2025-10-08"), shifts))
    }

    @Test
    fun `parse null or blank returns empty`() {
        assertTrue(HolidayUtils.parseShifts(null).isEmpty())
        assertTrue(HolidayUtils.parseShifts("").isEmpty())
        assertTrue(HolidayUtils.parseShifts("not json").isEmpty())
    }

    @Test
    fun `serialize round trips`() {
        val shifts = mapOf(
            LocalDate.parse("2025-10-11") to 1,
            LocalDate.parse("2025-09-28") to 5,
        )
        val parsed = HolidayUtils.parseShifts(HolidayUtils.serializeShifts(shifts))
        assertEquals(shifts, parsed)
    }

    @Test
    fun `serialize emits ISO keys in stable sorted order`() {
        // 与 parseShifts 使用同一 kotlinx.serialization json 实例，编解码对称；
        // 键为 LocalDate.toString() 的 ISO 字符串，Map<String, Int> 序列化无引号转义
        val shifts = mapOf(
            LocalDate.parse("2025-10-11") to 1,
            LocalDate.parse("2025-09-28") to 5,
        )
        val json = HolidayUtils.serializeShifts(shifts)
        assertEquals("""{"2025-09-28":5,"2025-10-11":1}""", json)
        assertEquals(shifts, HolidayUtils.parseShifts(json))
    }

    @Test
    fun `serialize preserves day-off zero values`() {
        val json = HolidayUtils.serializeShifts(mapOf(LocalDate.parse("2025-10-08") to 0))
        assertEquals("""{"2025-10-08":0}""", json)
        assertEquals(0, HolidayUtils.parseShifts(json)[LocalDate.parse("2025-10-08")])
    }

    @Test
    fun `shifted day returns mapping when present`() {
        val shifts = mapOf(LocalDate.parse("2025-10-11") to 1)
        // 2025-10-11 是周六（自然星期 6），映射为周一（1）
        assertEquals(6, LocalDate.parse("2025-10-11").dayOfWeek.value)
        assertEquals(1, HolidayUtils.shiftedDayOfWeek(LocalDate.parse("2025-10-11"), shifts))
    }

    @Test
    fun `shifted day falls back to natural weekday`() {
        val date = LocalDate.parse("2025-10-13") // 周一
        assertEquals(1, HolidayUtils.shiftedDayOfWeek(date, emptyMap()))
        assertEquals(1, HolidayUtils.shiftedDayOfWeek(date, mapOf(LocalDate.parse("2025-10-11") to 1)))
    }

    @Test
    fun `is shifted detects mapped dates`() {
        val shifts = mapOf(LocalDate.parse("2025-10-11") to 1)
        assertTrue(HolidayUtils.isShifted(LocalDate.parse("2025-10-11"), shifts))
        assertFalse(HolidayUtils.isShifted(LocalDate.parse("2025-10-12"), shifts))
    }
}
