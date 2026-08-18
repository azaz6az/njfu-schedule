package com.schedule.njfu.share

import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils
import com.schedule.njfu.ui.schedule.WeekGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 「课表分享成图」纯函数单测（JVM，不依赖 Robolectric/Android 绘制）：
 * 周日期推导、整图布局预算、Cell→占位矩形、课程名分行/截断/字号选择。
 */
class ShareScheduleImageTest {

    private fun course(name: String, day: Int, start: Int, end: Int) =
        Course(
            name = name,
            dayOfWeek = day,
            startPeriod = start,
            endPeriod = end,
            weeks = WeekUtils.maskFor(1, 16),
            color = 0,
        )

    private fun layout(rowHeightDp: Float = 48f) =
        computeShareLayout(imageWidthPx = 1080, density = 3f, rowHeightDp = rowHeightDp)

    // ---- weekDatesFor ----

    @Test
    fun `week1 returns the 7 dates of the semester week`() {
        val dates = weekDatesFor(LocalDate.parse("2025-09-01"), week = 1)
        assertEquals(7, dates.size)
        assertEquals(LocalDate.parse("2025-09-01"), dates[0])
        assertEquals(LocalDate.parse("2025-09-07"), dates[6])
    }

    @Test
    fun `non-monday semester start is normalized to its monday`() {
        // 2025-09-04 是周四 → 第 1 周应从当周周一（09-01）起算
        val dates = weekDatesFor(LocalDate.parse("2025-09-04"), week = 1)
        assertEquals(LocalDate.parse("2025-09-01"), dates[0])
    }

    @Test
    fun `week n advances by 7 days per week`() {
        val dates = weekDatesFor(LocalDate.parse("2025-09-01"), week = 3)
        assertEquals(LocalDate.parse("2025-09-15"), dates[0])
        assertEquals(LocalDate.parse("2025-09-21"), dates[6])
    }

    @Test
    fun `week below 1 is clamped to week 1`() {
        val dates = weekDatesFor(LocalDate.parse("2025-09-01"), week = 0)
        assertEquals(LocalDate.parse("2025-09-01"), dates[0])
    }

    // ---- computeShareLayout ----

    @Test
    fun `layout derives full grid geometry`() {
        val l = layout()
        // 1080px @ density 3 → 360dp 逻辑宽
        assertEquals(360f, l.widthDp, 0.001f)
        assertEquals(56f, l.gridLeftDp, 0.001f)          // 16 padding + 40 标签列
        assertEquals(41.142857f, l.dayColWidthDp, 0.001f) // (360-56-16)/7
        assertEquals(98f, l.gridTopDp, 0.001f)            // 16 + 48 标题 + 34 表头
        assertEquals(576f, l.gridHeightDp, 0.001f)        // 12 行 × 48
        assertEquals(690f, l.heightDp, 0.001f)            // 98 + 576 + 16 底边
    }

    // ---- cardRect ----

    @Test
    fun `cell maps to its grid slot rect`() {
        val l = layout()
        val cell = WeekGrid.Cell(course("高数", 1, 1, 2), row = 0, rowSpan = 2, col = 0, overlapIndex = 0, overlapCount = 1)
        val r = cardRect(l, cell)
        assertEquals(l.gridLeftDp, r.left, 0.001f)
        assertEquals(l.gridTopDp, r.top, 0.001f)
        assertEquals(l.dayColWidthDp, r.width, 0.001f)
        assertEquals(2 * 48f, r.height, 0.001f)
    }

    @Test
    fun `overlapping cells are split side by side within the column`() {
        val l = layout()
        val a = WeekGrid.Cell(course("A", 1, 1, 2), row = 0, rowSpan = 2, col = 1, overlapIndex = 0, overlapCount = 2)
        val b = WeekGrid.Cell(course("B", 1, 1, 2), row = 0, rowSpan = 2, col = 1, overlapIndex = 1, overlapCount = 2)
        val ra = cardRect(l, a)
        val rb = cardRect(l, b)
        val half = l.dayColWidthDp / 2f
        assertEquals(half, ra.width, 0.001f)
        assertEquals(half, rb.width, 0.001f)
        assertEquals(ra.top, rb.top, 0.001f)
        assertEquals(ra.bottom, rb.bottom, 0.001f)
        assertEquals(ra.right, rb.left, 0.001f) // 双卡无缝拼满整列
    }

    @Test
    fun `non overlapping courses keep the full column width`() {
        val l = layout()
        val cell = WeekGrid.Cell(course("A", 2, 3, 4), row = 2, rowSpan = 2, col = 1, overlapIndex = 0, overlapCount = 1)
        val r = cardRect(l, cell)
        assertEquals(l.dayColWidthDp, r.width, 0.001f)
        assertEquals(l.gridLeftDp + l.dayColWidthDp, r.left, 0.001f) // 第 2 列起点
        assertEquals(l.gridTopDp + 2 * 48f, r.top, 0.001f)           // 第 3 行起点
    }

    // ---- wrapGreedy ----

    @Test
    fun `wrap breaks at unit limit and counts ascii as narrow`() {
        // maxUnits=2：中=1 单位、A=0.55 → "AB" 1.1 放得下，"AB中" 2.1 放不下则换行
        assertEquals(listOf("AB", "中"), wrapGreedy("AB中", maxUnits = 2))
        // 整字不拆：单个字超限仍独占一行
        assertEquals(listOf("中中", "中"), wrapGreedy("中中中", maxUnits = 2))
        assertEquals(listOf(""), wrapGreedy("", maxUnits = 2))
    }

    // ---- planTextLines ----

    @Test
    fun `short text fits in one line without truncation`() {
        val plan = planTextLines("高数", maxUnits = 4, maxLines = 3)
        assertEquals(listOf("高数"), plan.lines)
        assertFalse(plan.truncated)
    }

    @Test
    fun `long text wraps into multiple complete lines`() {
        val plan = planTextLines("高等数学与线性代数", maxUnits = 4, maxLines = 3)
        assertEquals(listOf("高等数学", "与线性代", "数"), plan.lines)
        assertFalse(plan.truncated) // 行数未超，无需截断
    }

    @Test
    fun `overflow beyond maxLines truncates last visible line with ellipsis`() {
        val plan = planTextLines("高等数学与线性代数", maxUnits = 4, maxLines = 2)
        assertEquals(listOf("高等数学", "与线性…"), plan.lines) // 末行只留 3 单位 + 省略号
        assertTrue(plan.truncated)
    }

    @Test
    fun `maxLines one yields a single line, ellipsis only when text overflows`() {
        // 名字一行放得下：完整显示，不截断
        val fits = planTextLines("高等数学", maxUnits = 4, maxLines = 1)
        assertEquals(1, fits.lines.size)
        assertEquals(listOf("高等数学"), fits.lines)
        assertFalse(fits.truncated)
        // 名字超出一行：保留 0 个完整行，末行截到 budget=3 单位 + 省略号
        val overflow = planTextLines("高等数学与线性代数", maxUnits = 4, maxLines = 1)
        assertEquals(1, overflow.lines.size)
        assertEquals("高等数…", overflow.lines[0])
        assertTrue(overflow.truncated)
    }

    // ---- planCardText ----

    @Test
    fun `card picks largest font and keeps location when room allows`() {
        // 36dp 宽 × 90dp 高：12sp 下 2 行名 + 地点行放得下
        val plan = planCardText("高等数学", "教五楼201", textWidthDp = 36f, textHeightDp = 90f)
        assertEquals(12f, plan.fontSizeSp, 0.001f)
        assertEquals(listOf("高等数", "学"), plan.nameLines) // 36/12=3 字宽 → 3+1 字两行
        assertFalse(plan.truncatedName)
        assertTrue(plan.showLocation)
    }

    @Test
    fun `card drops location row when height is tight`() {
        // 36dp 宽 × 40dp 高：12sp 两行 31.2 + 14 放不下 → 退到 9sp 或去掉地点行
        val plan = planCardText("高等数学", "教五楼201", textWidthDp = 36f, textHeightDp = 40f)
        assertFalse(plan.showLocation)
        assertEquals(12f, plan.fontSizeSp, 0.001f) // 12sp 两行 31.2 ≤ 40 即成立，仅放弃地点行
        assertFalse(plan.truncatedName)
    }

    @Test
    fun `mini card falls back to 9sp with truncated last line`() {
        // 10dp × 12dp：任何字号都放不下全部行 → 9sp、1 行省略号、无地点
        val plan = planCardText("高等数学", "教五楼201", textWidthDp = 10f, textHeightDp = 12f)
        assertEquals(9f, plan.fontSizeSp, 0.001f)
        assertEquals(1, plan.nameLines.size)
        assertTrue(plan.nameLines[0].endsWith("…"))
        assertTrue(plan.truncatedName)
        assertFalse(plan.showLocation)
    }

    @Test
    fun `blank location never shows a location row`() {
        val plan = planCardText("高等数学", "", textWidthDp = 36f, textHeightDp = 120f)
        assertFalse(plan.showLocation)
    }
}