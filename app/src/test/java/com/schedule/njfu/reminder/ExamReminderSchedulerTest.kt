package com.schedule.njfu.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ExamReminderSchedulerTest {

    private fun epoch(date: LocalDate, time: Int, minute: Int) =
        date.atTime(time, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `advance 1 day stays within same month`() {
        val (advance, dayOf) = examReminderEpochs(LocalDate.of(2026, 9, 20), 1)
        // 提前 1 天 09:00 = 09-19 09:00；当天 08:00 = 09-20 08:00
        assertEquals(epoch(LocalDate.of(2026, 9, 19), 9, 0), advance)
        assertEquals(epoch(LocalDate.of(2026, 9, 20), 8, 0), dayOf)
    }

    @Test
    fun `advance crosses months`() {
        // 考试 11-02，提前 3 天 → 10-30 09:00
        val (advance, _) = examReminderEpochs(LocalDate.of(2026, 11, 2), 3)
        assertEquals(epoch(LocalDate.of(2026, 10, 30), 9, 0), advance)
    }

    @Test
    fun `advance crosses years`() {
        // 考试 2027-01-03，提前 7 天 → 2026-12-27 09:00
        val (advance, _) = examReminderEpochs(LocalDate.of(2027, 1, 3), 7)
        assertEquals(epoch(LocalDate.of(2026, 12, 27), 9, 0), advance)
    }

    @Test
    fun `advance 2 days schedule also returns same-day 8am`() {
        val exam = LocalDate.of(2026, 9, 20)
        val (advance, dayOf) = examReminderEpochs(exam, 2)
        assertEquals(epoch(exam.minusDays(2), 9, 0), advance)
        assertEquals(epoch(exam, 8, 0), dayOf)
    }

    @Test
    fun `past exam advances to an epoch before now（触发已过 → cancel 分支）`() {
        // 过去很久的考试，提前提醒一定早于 now
        val exam = LocalDate.of(2020, 1, 1)
        val (advance, _) = examReminderEpochs(exam, 1)
        assertTrue("提前提醒应已过期", advance <= System.currentTimeMillis())
        val now = System.currentTimeMillis()
        // 复现 ExamReminderScheduler.schedule 的分支：epoch <= now → cancel
        val shouldCancel = advance <= now
        assertTrue(shouldCancel)
    }

    @Test
    fun `invalid date string is null-fallback（兜底取消）`() {
        // 复现 rescheduleExams 对非法日期的处理：LocalDate.parse 抛异常 → 返回 null → cancel
        val bad = "2026-13-40"
        val parsed = runCatching { LocalDate.parse(bad) }.getOrNull()
        assertEquals("非法日期应解析失败返回 null", null, parsed)
    }

    @Test
    fun `epochs are positive and ordered advance before day-of`() {
        val exam = LocalDate.of(2026, 9, 20)
        val (advance, dayOf) = examReminderEpochs(exam, 3)
        assertTrue(advance < dayOf)
        assertTrue(dayOf > 0)
    }
}
