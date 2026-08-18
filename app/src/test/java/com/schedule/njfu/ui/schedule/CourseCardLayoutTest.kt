package com.schedule.njfu.ui.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseCardLayoutTest {

    // 典型 360dp 屏：单节卡文字区 27dp 宽；两节连堂卡高 87dp、单节卡高 39dp
    private val narrowW = 27f
    private val twoPeriodH = 87f
    private val onePeriodH = 39f

    @Test
    fun `short name fits one line with location and period`() {
        val l = computeCourseNameLayout("高数", locationPresent = true, narrowW, twoPeriodH)
        assertEquals(12f, l.fontSizeSp, 0f)
        assertEquals(1, l.maxLines)
        assertTrue(l.showLocation)
        assertTrue(l.showPeriod)
    }

    @Test
    fun `four char name wraps to two lines keeping meta`() {
        val l = computeCourseNameLayout("高等数学", locationPresent = true, narrowW, twoPeriodH)
        assertEquals(12f, l.fontSizeSp, 0f)
        assertEquals(2, l.maxLines)
        assertTrue(l.showLocation)
        assertTrue(l.showPeriod)
    }

    @Test
    fun `long name takes all lines and drops meta before shrinking font`() {
        // effLen 8.55 → 12sp 5 行（84dp）刚好塞满，放不下小字
        val l = computeCourseNameLayout("大学英语（三）A班", locationPresent = true, narrowW, twoPeriodH)
        assertEquals(12f, l.fontSizeSp, 0f)
        assertEquals(5, l.maxLines)
        assertFalse(l.showLocation)
        assertFalse(l.showPeriod)
    }

    @Test
    fun `one period card shows full name without meta`() {
        val l = computeCourseNameLayout("高等数学", locationPresent = false, narrowW, onePeriodH)
        assertEquals(12f, l.fontSizeSp, 0f)
        assertEquals(2, l.maxLines)
        assertFalse(l.showLocation)
        assertFalse(l.showPeriod)
    }

    @Test
    fun `very long name shrinks font and fills all lines`() {
        // 18 个汉字：9sp 每行 3 字共 6 行（75.6dp），小字已无空间
        val l = computeCourseNameLayout(
            "习近平新时代中国特色社会主义思想概论",
            locationPresent = true,
            narrowW,
            twoPeriodH,
        )
        assertEquals(9f, l.fontSizeSp, 0f)
        assertEquals(6, l.maxLines)
        assertFalse(l.showLocation)
        assertFalse(l.showPeriod)
    }

    @Test
    fun `keeps location and drops period when only one meta line fits`() {
        // 15 个汉字：9sp 每行 3 字共 5 行（63dp），只够再放一行小字 → 保地点、丢节次
        val l = computeCourseNameLayout(
            "高等数学线性代数概率统计方法论",
            locationPresent = true,
            narrowW,
            twoPeriodH,
        )
        assertEquals(9f, l.fontSizeSp, 0f)
        assertEquals(5, l.maxLines)
        assertTrue(l.showLocation)
        assertFalse(l.showPeriod)
    }

    @Test
    fun `tiny card falls back to clipped lines`() {
        val l = computeCourseNameLayout(
            "马克思主义基本原理与新时代中国特色社会主义实践",
            locationPresent = true,
            narrowW,
            onePeriodH,
        )
        assertEquals(9f, l.fontSizeSp, 0f)
        assertEquals(3, l.maxLines) // 39 / (9 * 1.4) = 3 行
        assertFalse(l.showLocation)
        assertFalse(l.showPeriod)
    }

    @Test
    fun `wide card keeps everything on one line`() {
        val l = computeCourseNameLayout("大学英语", locationPresent = true, 100f, twoPeriodH)
        assertEquals(12f, l.fontSizeSp, 0f)
        assertEquals(1, l.maxLines)
        assertTrue(l.showLocation)
        assertTrue(l.showPeriod)
    }

    @Test
    fun `latin characters count as half width`() {
        // effLen 5.65 → 12sp 3 行，地点 + 节次都能放下
        val l = computeCourseNameLayout("C++程序设计", locationPresent = true, narrowW, twoPeriodH)
        assertEquals(12f, l.fontSizeSp, 0f)
        assertEquals(3, l.maxLines)
        assertTrue(l.showLocation)
        assertTrue(l.showPeriod)
    }

    @Test
    fun `no location means at most one meta line`() {
        val l = computeCourseNameLayout("高等数学", locationPresent = false, narrowW, twoPeriodH)
        assertEquals(12f, l.fontSizeSp, 0f)
        assertEquals(2, l.maxLines)
        assertFalse(l.showLocation)
        assertTrue(l.showPeriod)
    }

    // ---- 系统字体缩放（fontScale）----
    // sp 文字实际占用 = sp * fontScale；预算按 dp 算，故 fontScale > 1 时必须折算预算，
    // 否则文字超出预算、卡底行被裁（历史 bug）。

    @Test
    fun `fontScale 2x budgets text so it never overflows the card`() {
        // scale=1：12sp 两行 + 地点 + 节次
        val atScale1 = computeCourseNameLayout("高等数学", locationPresent = true, narrowW, twoPeriodH)
        // scale=2：每行只能放 1 字 → 12sp 需 4 行放不下，缩小字号并按可用行裁剪，丢弃小字行
        val atScale2 = computeCourseNameLayout("高等数学", locationPresent = true, narrowW, twoPeriodH, fontScale = 2f)
        assertEquals(2, atScale1.maxLines)
        assertEquals(12f, atScale1.fontSizeSp, 0f)
        assertTrue(atScale1.showLocation)
        assertTrue(atScale1.showPeriod)
        assertEquals(9f, atScale2.fontSizeSp, 0f)
        assertEquals(3, atScale2.maxLines)
        assertFalse(atScale2.showLocation)
        assertFalse(atScale2.showPeriod)
        // 关键不变量：文字实际渲染总高（sp * lineFactor * fontScale）不超出卡高 → 不会裁掉卡底行
        val renderedHeight = atScale2.maxLines * atScale2.fontSizeSp * COURSE_NAME_LINE_FACTOR * 2f
        assertTrue("文字渲染高度 $renderedHeight 超出卡高 $twoPeriodH", renderedHeight <= twoPeriodH + 0.001f)
    }

    @Test
    fun `fontScale 2x clips tiny card to fewer lines`() {
        val atScale1 = computeCourseNameLayout("高等数学", locationPresent = false, narrowW, onePeriodH)
        val atScale2 = computeCourseNameLayout("高等数学", locationPresent = false, narrowW, onePeriodH, fontScale = 2f)
        assertEquals(2, atScale1.maxLines)
        assertEquals(1, atScale2.maxLines) // 39 / 2 = 19.5 → 19.5 / (9*1.4) = 1 行
        assertFalse(atScale2.showLocation)
        assertFalse(atScale2.showPeriod)
    }

    @Test
    fun `fontScale smaller than one is already budgeted`() {
        // scale < 1（用户调小字体）：按更宽裕的预算排版，行数减少
        val atScale05 = computeCourseNameLayout("高等数学", locationPresent = true, narrowW, twoPeriodH, fontScale = 0.5f)
        assertEquals(1, atScale05.maxLines)
        assertEquals(12f, atScale05.fontSizeSp, 0f)
        assertTrue(atScale05.showLocation)
        assertTrue(atScale05.showPeriod)
    }
}
