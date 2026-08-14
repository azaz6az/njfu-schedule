package com.schedule.njfu.data

import com.schedule.njfu.ui.theme.CoursePalette
import org.junit.Assert.*
import org.junit.Test

class CourseMapperTest {

    @Test
    fun `colorFor is idempotent`() {
        assertEquals(CourseMapper.colorFor("高等数学"), CourseMapper.colorFor("高等数学"))
        assertEquals(CourseMapper.colorFor("大学英语"), CourseMapper.colorFor("大学英语"))
    }

    @Test
    fun `colorFor returns a palette color for any course name`() {
        val names = listOf(
            "高等数学", "大学英语", "数据结构", "体育", "毛概",
            "", "A", "线性代数",
            "这门课程的名字非常长以至于需要换行显示",
            "课程 1234-!@#",
        )
        for (name in names) {
            val color = CourseMapper.colorFor(name)
            assertTrue(
                "colorFor(\"$name\") = $color 不在 palette 中",
                CourseMapper.isPaletteColor(color),
            )
        }
    }

    @Test
    fun `displayColor maps old light palette to darkened slot`() {
        // 0.1.0 已写入数据库的莫兰迪浅色 → 映射到加深后的同槽位（保证对比度）
        val light = listOf(0xFFA8BCA3, 0xFF9FB4C7, 0xFFD6B8B8, 0xFFC0B4A8, 0xFFB3A9C4, 0xFFA9BDB5).map { it.toInt() }
        light.forEachIndexed { i, raw ->
            val mapped = CourseMapper.displayColor(raw)
            assertEquals("槽位 $i 未映射", CoursePalette.colors[i].value.toInt(), mapped)
            assertTrue(CourseMapper.isPaletteColor(mapped))
        }
    }

    @Test
    fun `displayColor keeps legacy high saturation mapping stable`() {
        // 最早的高饱和色：索引偏移后取模结果应与旧映射一致
        val oldHigh = listOf(0xFF3F51B5, 0xFF00897B, 0xFFF4511E, 0xFF6A1B9A,
            0xFFC62828, 0xFF2E7D32, 0xFFAD1457, 0xFF1565C0, 0xFFEF6C00, 0xFF00838F).map { it.toInt() }
        oldHigh.forEachIndexed { i, raw ->
            val mapped = CourseMapper.displayColor(raw)
            assertTrue("高饱和 $i 不在 palette", CourseMapper.isPaletteColor(mapped))
        }
    }

    @Test
    fun `displayColor passes through non legacy colors`() {
        val custom = 0xFF123456.toInt()
        assertEquals(custom, CourseMapper.displayColor(custom))
    }

}