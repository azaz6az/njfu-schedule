package com.schedule.njfu.reminder

import com.schedule.njfu.model.Course
import com.schedule.njfu.model.HolidayUtils
import com.schedule.njfu.model.WeekUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReminderSchedulerTest {

    private fun course(dayOfWeek: Int, weeks: Int = WeekUtils.maskFor(1, 16), name: String = "课") =
        Course(id = 1L, name = name, dayOfWeek = dayOfWeek, startPeriod = 1,
            endPeriod = 2, weeks = weeks, color = 0)

    // ---- 调休映射 ----

    @Test
    fun `shift map of 0 on a given date skips that day（放假）`() {
        val date = LocalDate.of(2026, 10, 8)
        // 该日映射为 0 → 放假，scheduleDay 不排任何课
        val shifts = mapOf(date to 0)
        val effectiveDay = HolidayUtils.shiftedDayOfWeek(date, shifts)
        assertEquals(0, effectiveDay)
        assertTrue(effectiveDay !in 1..7)
    }

    @Test
    fun `shift map reuses weekday for class on a substitute day`() {
        // 周六(10-11) 顶替周一：shiftedDayOfWeek 返回 1
        val date = LocalDate.of(2026, 10, 11)
        val shifts = mapOf(date to 1)
        assertEquals(1, HolidayUtils.shiftedDayOfWeek(date, shifts))
    }

    // ---- computeTriggerEpoch（纯函数，不依赖真实时钟） ----

    @Test
    fun `trigger epoch matches HH colon mm minus minutesBefore`() {
        val date = LocalDate.of(2026, 9, 20)
        val e = computeTriggerEpoch(date, "08:00", 10)
        assertNotNull(e)
        val expectedMinute = date.atTime(7, 50)
        assertEquals(
            expectedMinute.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            e,
        )
    }

    @Test
    fun `deduction wraps across midnight`() {
        // 00:05 - 10 分钟 → 前一日 23:55
        val date = LocalDate.of(2026, 9, 20)
        val e = computeTriggerEpoch(date, "00:05", 10)
        assertNotNull(e)
        val expected = date.atTime(0, 5).minusMinutes(10)
        assertEquals(
            expected.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            e,
        )
    }

    @Test
    fun `illegal period time returns null`() {
        assertNull(computeTriggerEpoch(LocalDate.now(), "", 10))
        assertNull(computeTriggerEpoch(LocalDate.now(), "25:99", 10))
        assertNull(computeTriggerEpoch(LocalDate.now(), "not-a-time", 10))
    }

    // 测试核心过滤逻辑：模拟 scheduleDay 的课程筛选（不接触 AlarmManager）

    private fun matches(date: LocalDate, week: Int, courses: List<Course>,
                        shifts: Map<LocalDate, Int>) =
        HolidayUtils.shiftedDayOfWeek(date, shifts).let { effective ->
            if (effective !in 1..7) emptyList()
            else courses.filter {
                it.dayOfWeek == effective && WeekUtils.contains(it.weeks, week)
            }
        }

    @Test
    fun `expired trigger is skipped`() {
        // 用一个必然已过（epoch <= now）的触发时间——这里通过纯函数验证语义：
        // 过去日期的 08:00 一定早于 now，故应被 scheduleDay 的过期守卫跳过。
        val past = LocalDate.of(2020, 1, 1)
        val e = computeTriggerEpoch(past, "08:00", 10)
        assertNotNull(e)
        assertTrue(e!! <= System.currentTimeMillis())
    }

    @Test
    fun `tomorrow week boundary - weekend schedules monday`() {
        // 起点 2026-09-14（周一）。周日 2026-09-20 是本周末尾；明天周一 09-21 跨到第 2 周。
        val start = LocalDate.of(2026, 9, 14)
        val today = LocalDate.of(2026, 9, 20) // 周日
        val tomorrow = today.plusDays(1) // 周一 09-21
        assertEquals(1, WeekUtils.currentWeek(start, today))
        assertEquals(2, WeekUtils.currentWeek(start, tomorrow))
    }

    @Test
    fun `cross week for monday course on sunday`() {
        // 周一的课程（第 2 周有课），在周日用今天周次不匹配，但用明天周次匹配。
        val start = LocalDate.of(2026, 9, 14)
        val today = LocalDate.of(2026, 9, 20) // 周日
        val tomorrow = today.plusDays(1)
        val mondayCourse = course(dayOfWeek = 1, weeks = WeekUtils.maskFor(2))
        val todayWeek = WeekUtils.currentWeek(start, today)
        val tomWeek = WeekUtils.currentWeek(start, tomorrow)
        // 明天是第 2 周周一 → 匹配
        assertTrue(matches(tomorrow, tomWeek, listOf(mondayCourse), emptyMap()).isNotEmpty())
        // 今天第 1 周里，周一的课不匹配周日
        assertTrue(matches(today, todayWeek, listOf(mondayCourse), emptyMap()).isEmpty())
    }

    @Test
    fun `no class when effective day has no matching course`() {
        val today = LocalDate.of(2026, 9, 20) // 周日
        // 只有周二的课，周日有效日 = 7 → 不匹配
        assertTrue(matches(today, 1, listOf(course(dayOfWeek = 2)), emptyMap()).isEmpty())
    }

    @Test
    fun `shift to 0 day yields no class even if a course exists that weekday`() {
        val date = LocalDate.of(2026, 10, 8) // 假设放假（映射 0）
        val shifts = mapOf(date to 0)
        // 周一的课在当天自然周四是周二… 直接断言有效日非 1..7
        assertTrue(HolidayUtils.shiftedDayOfWeek(date, shifts) !in 1..7)
    }

    // ---- effectiveClassDates（调休感知纯函数，2026-09-14 起为周一） ----

    @Test
    fun `shift to 0 skips that occurrence（放假跳过）`() {
        // 三周一的课：第二周周一 09-21 被映射为 0（放假）→ 只响第一、第三周
        val dates = listOf(
            LocalDate.of(2026, 9, 14), // 周一
            LocalDate.of(2026, 9, 21), // 周一，映射为 0（放假）
            LocalDate.of(2026, 9, 28), // 周一
        )
        val shifts = mapOf(LocalDate.of(2026, 9, 21) to 0)
        assertEquals(
            listOf(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 28)),
            effectiveClassDates(dates, courseDayOfWeek = 1, shifts),
        )
    }

    @Test
    fun `substitute day rings the mapped weekday course（周六补周一）`() {
        // 2026-10-10 是周六（自然星期 6），被映射为 1 → 当天按周一课表上课
        val sat = LocalDate.of(2026, 10, 10)
        assertEquals(6, sat.dayOfWeek.value) // 先确认该日确为周六
        val shifts = mapOf(sat to 1)
        // 周一课程：在补课周六响铃
        assertEquals(listOf(sat), effectiveClassDates(listOf(sat), courseDayOfWeek = 1, shifts))
        // 自然周六课程（星期 6）：当日被周一顶替，不响铃
        assertTrue(effectiveClassDates(listOf(sat), courseDayOfWeek = 6, shifts).isEmpty())
    }

    @Test
    fun `no shift keeps natural weekday ringing（无映射按自然星期）`() {
        // 2026-09-14（周一）~ 09-20（周日）整周日期；无映射时周三的课只在周三响
        val week = (0L..6L).map { LocalDate.of(2026, 9, 14).plusDays(it) }
        assertEquals(
            listOf(LocalDate.of(2026, 9, 16)), // 周三
            effectiveClassDates(week, courseDayOfWeek = 3, emptyMap()),
        )
    }

    @Test
    fun `ring dates equal old shiftedDayOfWeek filter（原逻辑不变）`() {
        // 与旧实现 scheduleDay 的筛选（shifts[date] ?: 自然星期 == dayOfWeek）逐项一致：
        // 09-21 放假（0）跳过、10-10 补周一的课（1）被纳入响铃清单
        val dates = listOf(
            LocalDate.of(2026, 9, 14),  // 周一
            LocalDate.of(2026, 9, 21),  // 周一 → 映射 0（放假）
            LocalDate.of(2026, 9, 28),  // 周一
            LocalDate.of(2026, 10, 10), // 周六 → 映射 1（补周一的课）
        )
        val shifts = mapOf(
            LocalDate.of(2026, 9, 21) to 0,
            LocalDate.of(2026, 10, 10) to 1,
        )
        val expected = dates.filter { HolidayUtils.shiftedDayOfWeek(it, shifts) == 1 }
        assertEquals(expected, effectiveClassDates(dates, courseDayOfWeek = 1, shifts))
        // 手算预期：[09-14、09-28、10-10]（09-21 放假剔除）
        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 14),
                LocalDate.of(2026, 9, 28),
                LocalDate.of(2026, 10, 10),
            ),
            expected,
        )
    }

    @Test
    fun `day off yields no rings for every weekday（放假全天无课）`() {
        val off = LocalDate.of(2026, 9, 21) // 周一，映射为 0（放假）
        val shifts = mapOf(off to 0)
        for (d in 1..7) {
            assertTrue(
                "星期 $d 在放假日不应响铃",
                effectiveClassDates(listOf(off), d, shifts).isEmpty(),
            )
        }
    }
}
