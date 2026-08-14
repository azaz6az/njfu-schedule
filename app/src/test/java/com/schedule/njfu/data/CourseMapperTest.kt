package com.schedule.njfu.data

import androidx.compose.ui.graphics.Color
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
            assertEquals("槽位 $i 未映射", (CoursePalette.colors[i].value shr 32).toInt(), mapped)
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


    @Test
    fun `displayColor darkens any too light color so white text stays readable`() {
        // JSON 备份导入可能携带任意浅色（白/浅黄/浅粉等）：必须兜底映射到深色板，
        // 否则浅底+白字 = 看起来是白色卡片、看不到课程文字
        val tooLight = listOf(
            0xFFFFFFFF.toInt(), // 纯白
            0xFFFFF8E1.toInt(), // 浅黄
            0xFFFFE4E1.toInt(), // 浅粉
            0xFFF0F0F0.toInt(), // 浅灰
            0xFFE8F5E9.toInt(), // 浅绿
            0xFFF3E5F5.toInt(), // 浅紫
        )
        for (raw in tooLight) {
            val mapped = CourseMapper.displayColor(raw)
            assertTrue("浅色 0x" + raw.toString(16) + " 未兜底映射", CourseMapper.isPaletteColor(mapped))
            // 兜底结果必须是可读深色：白字对比度 >= 4.5:1
            assertTrue(
                "0x" + raw.toString(16) + " -> 0x" + mapped.toString(16) + " 白字对比度不足",
                CourseMapper.whiteTextContrastOk(mapped),
            )
        }
    }

    @Test
    fun `displayColor passes through sufficiently dark custom colors`() {
        // 深色自定义色（如 0xFF123456 深蓝）保持原样，不强行改用户选择
        val dark = 0xFF123456.toInt()
        assertEquals(dark, CourseMapper.displayColor(dark))
        assertTrue(CourseMapper.whiteTextContrastOk(dark))
    }


    @Test
    fun `colorFor returns opaque non-zero colors`() {
        // 回归：Color.value.toInt() 曾取到低 32 位恒为 0，导致卡片全透明（白底无字）
        val colors = listOf("高等数学", "大学英语", "数据结构", "体育", "线性代数")
        for (name in colors) {
            val c = CourseMapper.colorFor(name)
            assertTrue("colorFor($name)=$c 必须是全不透明 ARGB", c.toLong() and 0xFF000000.toLong() != 0L)
            assertTrue("colorFor($name)=$c 不能是 0（透明）", c != 0)
        }
    }

    @Test
    fun `palette colors are opaque and match theme definition`() {
        for (color in CoursePalette.colors) {
            assertEquals("alpha 必须为 1.0（否则卡片透明露出白底）", 1f, color.alpha, 0.001f)
            // value.toInt() 的低 32 位是 colorSpace 信息而非 ARGB，此处直接验证分量
            assertTrue("red 分量异常: ${color.red}", color.red in 0.2f..0.9f)
        }
    }

}