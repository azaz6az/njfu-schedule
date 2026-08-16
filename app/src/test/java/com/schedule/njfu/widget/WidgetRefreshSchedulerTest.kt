package com.schedule.njfu.widget

import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

/**
 * 4x1 下一节课小组件的自适应刷新时间计算测试（纯 JVM）。
 * 依赖默认节次表（第 1 节 08:00 上课，45 分钟一节的单节课）。
 */
class WidgetRefreshSchedulerTest {

    private fun mondayCourse(start: Int) =
        Course(name = "高数", dayOfWeek = 1, startPeriod = start, endPeriod = start,
            weeks = WeekUtils.maskFor(1, 16), color = 0)

    // 2025-09-01 是周一
    private fun at(day: Int, hour: Int, minute: Int): LocalDateTime =
        LocalDateTime.of(2025, 9, day, hour, minute)

    @Test
    fun `inside window before class - next minute`() {
        // 第 1 节 08:00，07:30 在「上课前 60 分钟窗口」内 → 下一分钟整点
        val next = WidgetRefreshScheduler.nextRefreshAt(
            listOf(mondayCourse(1)), 1, at(1, 7, 30), emptyMap(),
        )
        assertEquals(at(1, 7, 31), next)
    }

    @Test
    fun `inside window during class - next minute`() {
        // 08:10 上课中 → 08:11
        val next = WidgetRefreshScheduler.nextRefreshAt(
            listOf(mondayCourse(1)), 1, at(1, 8, 10), emptyMap(),
        )
        assertEquals(at(1, 8, 11), next)
    }

    @Test
    fun `inside window just after class end - next minute`() {
        // 第 1 节 08:45 下课，09:00 仍在「下课 60 分钟窗口」内 → 09:01
        val next = WidgetRefreshScheduler.nextRefreshAt(
            listOf(mondayCourse(1)), 1, at(1, 9, 0), emptyMap(),
        )
        assertEquals(at(1, 9, 1), next)
    }

    @Test
    fun `outside window waiting - class start minus 60 min`() {
        // 00:30 距 08:00 超过 60 分钟，不在窗口 → 下一节课开始前 60 分钟 = 07:00
        val next = WidgetRefreshScheduler.nextRefreshAt(
            listOf(mondayCourse(1)), 1, at(1, 0, 30), emptyMap(),
        )
        assertEquals(at(1, 7, 0), next)
    }

    @Test
    fun `no class that day - next day 08 00`() {
        // 周二（day=2）课程在第 1 周且周一，但 now 是周二 09:00 → 今天无课 → 次日 08:00（周三）
        val next = WidgetRefreshScheduler.nextRefreshAt(
            listOf(mondayCourse(1)), 1, at(2, 9, 0), emptyMap(),
        )
        assertEquals(at(3, 8, 0), next)
    }

    @Test
    fun `all today classes over past window - next day 08 00`() {
        // 周一 20:00，第 1 节 08:45 早已下课且远超 60 分钟窗口 → 次日（周二）08:00
        val next = WidgetRefreshScheduler.nextRefreshAt(
            listOf(mondayCourse(1)), 1, at(1, 20, 0), emptyMap(),
        )
        assertEquals(at(2, 8, 0), next)
    }

    @Test
    fun `custom period times respected`() {
        // 自定义第 1 节 10:00，09:30 距开始 30 分钟 → 在窗口 → 下一分钟
        val next = WidgetRefreshScheduler.nextRefreshAt(
            listOf(mondayCourse(1)), 1, at(1, 9, 30), mapOf(1 to "10:00"),
        )
        assertEquals(at(1, 9, 31), next)
    }

    @Test
    fun `window exit point - now equals end plus 60 min leaves window`() {
        // 第 1 节 08:00 上 45 分钟 → 08:45 下课，窗口右边界 = 09:45。
        // now == 09:44（边界前）仍在窗口 → 逐分钟档（短触发，触发 setWindow 分支）。
        val inside = WidgetRefreshScheduler.nextRefreshAt(
            listOf(mondayCourse(1)), 1, at(1, 9, 44), emptyMap(),
        )
        assertEquals(at(1, 9, 45), inside)

        // now == 09:45（end + 60min，精确退出点）已离开窗口，
        // 且「课前 60 分钟」档（07:00）早过 → 回落次日 08:00 常规档位（触发 setExact 分支）。
        val outside = WidgetRefreshScheduler.nextRefreshAt(
            listOf(mondayCourse(1)), 1, at(1, 9, 45), emptyMap(),
        )
        assertEquals(at(2, 8, 0), outside)
    }

    @Test
    fun `two classes spaced over 2h picks next class start minus 60 min`() {
        // 第 1 节 08:00、第 4 节 11:00，两门跨越 3 小时（>2h）。
        // now = 09:50 落在两门课的倒计时窗口间隙（既不覆盖课 1 到 09:45，也早于课 4 的 10:00），
        // 因此选中「下一次上课（第 4 节）开始前 60 分钟」= 10:00 常规档位。
        val next = WidgetRefreshScheduler.nextRefreshAt(
            listOf(mondayCourse(1), mondayCourse(4)), 1, at(1, 9, 50),
            mapOf(1 to "08:00", 4 to "11:00"),
        )
        assertEquals(at(1, 10, 0), next)
    }

    @Test
    fun `no courses - no per-minute spam, falls to next day 08 00`() {
        // 空课程表（等价于无任何可刷新实例）：不进入逐分钟档，
        // 直接落到次日 08:00 常规档位，避免每 5 分钟空转闹钟。
        val next = WidgetRefreshScheduler.nextRefreshAt(
            emptyList(), 1, at(1, 9, 50), emptyMap(),
        )
        assertEquals(at(2, 8, 0), next)
    }
}
