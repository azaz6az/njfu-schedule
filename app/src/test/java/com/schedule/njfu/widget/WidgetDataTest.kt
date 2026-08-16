package com.schedule.njfu.widget

import com.schedule.njfu.R
import com.schedule.njfu.model.Course
import com.schedule.njfu.model.Exam
import com.schedule.njfu.model.WeekUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 小组件数据逻辑测试（纯 JVM，无需模拟器）：
 * 今日课程筛选与周网格分组排序。
 */
class WidgetDataTest {

    private fun course(
        name: String,
        day: Int,
        start: Int,
        weeks: Int = WeekUtils.maskFor(1, 16),
    ) = Course(name = name, dayOfWeek = day, startPeriod = start, endPeriod = start,
        weeks = weeks, color = 0)

    // 2025-09-01 是周一
    private val monday = LocalDate.parse("2025-09-01")

    @Test
    fun `today courses filter by day and week`() {
        val mondayCourse = course("高数", day = 1, start = 1)
        val tuesdayCourse = course("英语", day = 2, start = 1)
        val otherWeekCourse = course("体育", day = 1, start = 3, weeks = WeekUtils.maskFor(9, 16))
        val result = WidgetData.todayCourses(
            listOf(mondayCourse, tuesdayCourse, otherWeekCourse),
            week = 5,
            today = monday,
        )
        assertEquals(listOf("高数"), result.map { it.name })
    }

    @Test
    fun `today courses sorted by start period`() {
        val late = course("晚课", day = 1, start = 9)
        val early = course("早课", day = 1, start = 1)
        val result = WidgetData.todayCourses(listOf(late, early), week = 1, today = monday)
        assertEquals(listOf("早课", "晚课"), result.map { it.name })
    }

    @Test
    fun `today courses includes zero-week fallback`() {
        // weeks=0 的历史脏数据不应凭空消失
        val broken = course("未知周次", day = 1, start = 1, weeks = 0)
        val result = WidgetData.todayCourses(listOf(broken), week = 10, today = monday)
        assertEquals(1, result.size)
    }

    @Test
    fun `week courses grouped by day with limit`() {
        val m1 = course("高数", day = 1, start = 1)
        val m2 = course("线代", day = 1, start = 3)
        val t1 = course("英语", day = 2, start = 1)
        val byDay = WidgetData.weekCoursesByDay(listOf(m1, m2, t1), week = 1, maxPerDay = 1)
        assertEquals(1, byDay.getValue(1).size)   // 每天最多 1 门
        assertEquals(1, byDay.getValue(2).size)
        assertTrue(byDay.getValue(3).isEmpty())   // 周三无课
        assertEquals("高数", byDay.getValue(1)[0].name) // 按节次排序取最早
    }

    @Test
    fun `week courses filter by week mask`() {
        val oddWeek = course("单周课", day = 1, start = 1, weeks = WeekUtils.oddWeeks(1, 16))
        val byDayOdd = WidgetData.weekCoursesByDay(listOf(oddWeek), week = 3)
        assertEquals(1, byDayOdd.getValue(1).size)
        val byDayEven = WidgetData.weekCoursesByDay(listOf(oddWeek), week = 4)
        assertTrue(byDayEven.getValue(1).isEmpty())
    }

    // ---- 下一节课状态机 ----
    // 默认节次表位于 WidgetData.DEFAULT：1 节 08:00，2 节 08:55，3 节 09:50

    private fun courseAt(name: String, day: Int, start: Int, weeks: Int = WeekUtils.maskFor(1, 16)) =
        Course(name = name, dayOfWeek = day, startPeriod = start, endPeriod = start,
            weeks = weeks, color = 0)

    @Test
    fun `next class picking correct branch at boundary`() {
        // 周一 08:20 落在第 1 节（08:00 上课）内 → IN_PROGRESS，距下课 25 分钟
        val state = WidgetData.nextClassState(
            listOf(courseAt("高数", day = 1, start = 1)),
            1,
            LocalDateTime.of(2025, 9, 1, 8, 20),
            emptyMap(),
        )
        assertEquals(WidgetData.NextClassPhase.IN_PROGRESS, state.phase)
        assertEquals(25, state.minutes)
    }

    @Test
    fun `next class before - approaching upcoming`() {
        // 周一 07:30，第1节 08:00 未到 → BEFORE，距上课 30 分钟
        val state = WidgetData.nextClassState(
            listOf(courseAt("高数", day = 1, start = 1)),
            1,
            LocalDateTime.of(2025, 9, 1, 7, 30),
            emptyMap(),
        )
        assertEquals(WidgetData.NextClassPhase.BEFORE, state.phase)
        assertEquals(30, state.minutes)
        assertEquals("高数", state.course?.name)
    }

    @Test
    fun `next class in progress`() {
        // 周一 08:10，第1节 08:00 上课中 → 距下课 35 分钟
        val state = WidgetData.nextClassState(
            listOf(courseAt("高数", day = 1, start = 1)),
            1,
            LocalDateTime.of(2025, 9, 1, 8, 10),
            emptyMap(),
        )
        assertEquals(WidgetData.NextClassPhase.IN_PROGRESS, state.phase)
        assertEquals(35, state.minutes)
    }

    @Test
    fun `next class after all over`() {
        // 周一 12:30，第1节(08:45下课)与第2节(09:40下课)都已结束 → AFTER
        val state = WidgetData.nextClassState(
            listOf(courseAt("高数", day = 1, start = 1), courseAt("线代", day = 1, start = 2)),
            1,
            LocalDateTime.of(2025, 9, 1, 12, 30),
            emptyMap(),
        )
        assertEquals(WidgetData.NextClassPhase.AFTER, state.phase)
    }

    @Test
    fun `next class no class that day`() {
        // 周一课程但今天周三(day=3)无课
        val state = WidgetData.nextClassState(
            listOf(courseAt("高数", day = 1, start = 1)),
            1,
            LocalDateTime.of(2025, 9, 3, 10, 0),
            emptyMap(),
        )
        assertEquals(WidgetData.NextClassPhase.NO_CLASS_TODAY, state.phase)
        assertNull(state.course)
    }

    @Test
    fun `next class ignores classes not in current week`() {
        // 该课只在 9-16 周，第 2 周不显示 → 无课
        val state = WidgetData.nextClassState(
            listOf(courseAt("体育", day = 1, start = 1, weeks = WeekUtils.maskFor(9, 16))),
            2,
            LocalDateTime.of(2025, 9, 8, 12, 0),
            emptyMap(),
        )
        assertEquals(WidgetData.NextClassPhase.NO_CLASS_TODAY, state.phase)
    }

    @Test
    fun `next class respects custom period times`() {
        // 自定义第1节 09:00，08:30 应仍在课前
        val state = WidgetData.nextClassState(
            listOf(courseAt("高数", day = 1, start = 1)),
            1,
            LocalDateTime.of(2025, 9, 1, 8, 30),
            mapOf(1 to "09:00"),
        )
        assertEquals(WidgetData.NextClassPhase.BEFORE, state.phase)
        assertEquals(30, state.minutes)
    }

    @Test
    fun `next class across midnight - late night only`() {
        // 周一 23:50，当日无课（课在周二）→ 无课周一
        val state = WidgetData.nextClassState(
            listOf(courseAt("英语", day = 2, start = 1)),
            1,
            LocalDateTime.of(2025, 9, 8, 23, 50),
            emptyMap(),
        )
        assertEquals(WidgetData.NextClassPhase.NO_CLASS_TODAY, state.phase)
    }

    // ---- 考试倒计时 ----

    @Test
    fun `exam countdown - none`() {
        val none = WidgetData.nextExamCountdown(
            listOf(Exam(name = "期末", date = "2025-08-01", location = "")),
            LocalDate.parse("2025-09-01"),
        )
        assertNull(none)
    }

    @Test
    fun `exam countdown - future exam`() {
        val (exam, days) = WidgetData.nextExamCountdown(
            listOf(
                Exam(name = "期末", date = "2025-09-10", location = "教二"),
                Exam(name = "期中", date = "2025-09-05", location = "教一"),
            ),
            LocalDate.parse("2025-09-01"),
        )!!
        assertEquals("期中", exam.name)   // 最早的未来考试
        assertEquals(4, days)
    }

    @Test
    fun `exam countdown - today exam`() {
        val (exam, days) = WidgetData.nextExamCountdown(
            listOf(Exam(name = "高数", date = "2025-09-03", location = "")),
            LocalDate.parse("2025-09-03"),
        )!!
        assertEquals("高数", exam.name)
        assertEquals(0, days)
    }

    // ---- 星期中文名资源 id ----

    @Test
    fun `day name res maps to weekday string resources`() {
        assertEquals(R.string.weekday_mon, WidgetData.dayNameRes(1))
        assertEquals(R.string.weekday_fri, WidgetData.dayNameRes(5))
        assertEquals(R.string.weekday_sun, WidgetData.dayNameRes(7))
        assertEquals(0, WidgetData.dayNameRes(0))
        assertEquals(0, WidgetData.dayNameRes(8))
    }
}
