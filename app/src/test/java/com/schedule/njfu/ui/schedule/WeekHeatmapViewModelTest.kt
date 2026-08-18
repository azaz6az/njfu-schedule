package com.schedule.njfu.ui.schedule

import com.schedule.njfu.data.CourseMapper
import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 周次热力图纯函数（courses → 热力行数据）的单元测试 */
class WeekHeatmapViewModelTest {

    /** 构造测试用课程：默认 周一 1-2 节、无周次掩码（= 脏数据场景） */
    private fun course(
        name: String = "高等数学",
        dayOfWeek: Int = 1,
        weeks: Int = 0,
        color: Int = 0,
    ) = Course(
        id = 0L,
        name = name,
        teacher = "张老师",
        location = "A101",
        dayOfWeek = dayOfWeek,
        startPeriod = 1,
        endPeriod = 2,
        weeks = weeks,
        color = color,
    )

    @Test
    fun `effectiveWeeksMask falls back zero mask to full term`() {
        val full = WeekUtils.maskFor(1, WeekUtils.MAX_WEEKS)
        assertEquals(full, effectiveWeeksMask(course(weeks = 0)))
        // 负数等异常脏数据同样按全学期兜底
        assertEquals(full, effectiveWeeksMask(course(weeks = -5)))
    }

    @Test
    fun `effectiveWeeksMask keeps valid mask`() {
        val mask = WeekUtils.maskFor(3, 8)
        assertEquals(mask, effectiveWeeksMask(course(weeks = mask)))
    }

    @Test
    fun `weekRanges parses continuous weeks`() {
        assertEquals(listOf(1..16), weekRanges(WeekUtils.maskFor(1, 16)))
        assertEquals(listOf(3..8), weekRanges(WeekUtils.maskFor(3, 8)))
        assertEquals(
            listOf(1..WeekUtils.MAX_WEEKS),
            weekRanges(WeekUtils.maskFor(1, WeekUtils.MAX_WEEKS)),
        )
    }

    @Test
    fun `weekRanges splits across term segments`() {
        // 1-5 周 + 7-11 周：中间第 6 周断开，得到两个区间
        val mask = WeekUtils.maskFor(1, 5) or WeekUtils.maskFor(7, 11)
        assertEquals(listOf(1..5, 7..11), weekRanges(mask))
    }

    @Test
    fun `weekRanges handles odd weeks`() {
        val ranges = weekRanges(WeekUtils.oddWeeks(1, 17))
        assertEquals(9, ranges.size)
        assertEquals(1..1, ranges.first())
        assertEquals(17..17, ranges.last())
    }

    @Test
    fun `weekRanges returns empty for empty mask`() {
        assertEquals(emptyList<IntRange>(), weekRanges(0))
    }

    @Test
    fun `buildHeatmapRows flags each week from mask`() {
        val row = buildHeatmapRows(listOf(course(weeks = WeekUtils.maskFor(1, 8))))[0]
        assertEquals(WeekUtils.MAX_WEEKS, row.weekFlags.size)
        assertTrue(row.hasClass(1))
        assertTrue(row.hasClass(8))
        assertFalse(row.hasClass(9))
        assertFalse(row.hasClass(WeekUtils.MAX_WEEKS))
        assertFalse(row.hasClass(0))
        assertEquals(listOf(1..8), row.ranges)
    }

    @Test
    fun `buildHeatmapRows treats zero weeks as full term`() {
        val row = buildHeatmapRows(listOf(course(weeks = 0)))[0]
        // 整学期 = 全部周都有课（与 WeekGrid.cellsFor 的 weeks==0 兜底语义一致）
        assertTrue((1..WeekUtils.MAX_WEEKS).all { row.hasClass(it) })
        assertEquals(listOf(1..WeekUtils.MAX_WEEKS), row.ranges)
    }

    @Test
    fun `buildHeatmapRows uses palette color like course cards`() {
        // 颜色为 0 → 按课名取色板色，与课表卡片 CourseCard 的取色逻辑一致
        val named = buildHeatmapRows(listOf(course(name = "大学英语", color = 0)))[0]
        assertEquals(CourseMapper.displayColor(CourseMapper.colorFor("大学英语")), named.color)
        // 自定义深色 → displayColor 原样保留
        val custom = 0xFF123456.toInt()
        val dark = buildHeatmapRows(listOf(course(name = "体育", color = custom)))[0]
        assertEquals(custom, dark.color)
        // 过浅的自定义色（白底白字风险）→ 兜底到色板色
        val tooLight = 0xFFFFFFFF.toInt()
        val light = buildHeatmapRows(listOf(course(name = "体育", color = tooLight)))[0]
        assertTrue(CourseMapper.isPaletteColor(light.color))
    }

    @Test
    fun `buildHeatmapRows keeps detail fields and input order`() {
        val c1 = course(name = "高等数学", dayOfWeek = 2, weeks = WeekUtils.maskFor(1, 16))
        val c2 = course(name = "数据结构", dayOfWeek = 4, weeks = WeekUtils.maskFor(1, 8))
        val rows = buildHeatmapRows(listOf(c2, c1))
        // 行序与输入一致（库序按星期/节次排序，直接透传）
        assertEquals(listOf("数据结构", "高等数学"), rows.map { it.name })
        val first = rows[0]
        assertEquals(4, first.dayOfWeek)
        assertEquals(1, first.startPeriod)
        assertEquals(2, first.endPeriod)
        assertEquals("A101", first.location)
        assertEquals("张老师", first.teacher)
        assertEquals(listOf(1..8), first.ranges)
    }
}