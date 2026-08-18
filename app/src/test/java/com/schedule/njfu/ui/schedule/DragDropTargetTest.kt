package com.schedule.njfu.ui.schedule

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 拖拽落点纯函数 [computeDragDropTarget] 的单测：
 * 正常移动、边界 clamp、越界返回 null、rowSpan 保持。
 * 纯 JUnit4，无需 Robolectric（函数不依赖 Android 框架）。
 */
class DragDropTargetTest {

    private val colWidth = 100f
    private val rowHeight = 48f

    /** 默认：卡片 100×96（1-2 节 → rowSpan 2）落在网格原点 (0,0) */
    private fun drop(
        dragOffset: Offset,
        startX: Float = 0f,
        startY: Float = 0f,
        cardWidth: Float = 100f,
        cardHeight: Float = 96f,
        originalStart: Int = 1,
        originalEnd: Int = 2,
    ) = computeDragDropTarget(
        dragOffset = dragOffset,
        cardStartX = startX,
        cardStartY = startY,
        cardWidth = cardWidth,
        cardHeight = cardHeight,
        colWidth = colWidth,
        rowHeight = rowHeight,
        originalStart = originalStart,
        originalEnd = originalEnd,
    )

    @Test
    fun `normal move to another column and period`() {
        // 中心 = (0+150+50, 0+120+48) = (200, 168) → col 2、row 3 → 第 4 节起，共 2 节
        val t = drop(Offset(150f, 120f))!!
        assertEquals(2, t.col)
        assertEquals(4, t.newStart)
        assertEquals(5, t.endPeriod)
    }

    @Test
    fun `drop exactly on column boundary picks the left column`() {
        // 卡片中心 x 恰为 300（colWidth=100 的列边界）→ floor 取左列 col=3
        val t = drop(Offset(250f, 0f))!!
        assertEquals(3, t.col)
    }

    @Test
    fun `drop inside last column and last rows clamps into grid`() {
        // 中心 x = 699.5（第 7 列内最后一像素）→ col 6
        val t = drop(Offset(649.5f, 200f))!!
        assertEquals(6, t.col)
        // 中心 y = 575.9（第 12 行内最后一像素）→ row 11 → rowSpan 2 时开始节 clamp 到 11（11-12 节）
        val t2 = drop(Offset(0f, 527.9f))!!
        assertEquals(11, t2.newStart)
        assertEquals(12, t2.endPeriod)
    }

    @Test
    fun `row span is preserved after move`() {
        // 原 5-8 节（rowSpan 4）：拖到最底行 → 开始节 clamp 到 9，end=12，仍是 4 节
        val t = drop(
            dragOffset = Offset(150f, 240f),
            startY = 4 * rowHeight,
            cardHeight = 4 * rowHeight,
            originalStart = 5,
            originalEnd = 8,
        )!!
        assertEquals(4, t.endPeriod - t.newStart + 1)
        assertEquals(9, t.newStart)
        assertEquals(12, t.endPeriod)
    }

    @Test
    fun `drag out of grid right edge returns null`() {
        // 卡片中心 x = 700 = 7*colWidth → 严格越界
        assertNull(drop(Offset(650f, 0f)))
    }

    @Test
    fun `drag below grid bottom returns null`() {
        // 卡片中心 y = 576 = 12*rowHeight → 严格越界
        assertNull(drop(Offset(0f, 528f)))
    }

    @Test
    fun `drag left of grid origin returns null`() {
        // 卡片中心 x = -50 < 0 → 越界
        assertNull(drop(Offset(-100f, 0f)))
    }

    @Test
    fun `drag above grid top returns null`() {
        // 卡片中心 y = -52 < 0 → 越界
        assertNull(drop(Offset(0f, -100f)))
    }
}