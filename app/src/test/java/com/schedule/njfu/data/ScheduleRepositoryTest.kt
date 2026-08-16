package com.schedule.njfu.data

import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleRepositoryTest {

    private fun course(name: String, day: Int, period: Int, weeks: Int) =
        Course(name = name, dayOfWeek = day, startPeriod = period, endPeriod = period,
            weeks = weeks, color = 0)

    @Test
    fun `merge deduplicates identical auto courses`() {
        val a = course("高数", 1, 1, WeekUtils.maskFor(1, 16))
        val merged = ScheduleRepository.merge(auto = listOf(a, a), manual = emptyList())
        assertEquals(1, merged.size)
    }

    @Test
    fun `merge keeps manual courses when same name differs`() {
        val manual = course("高数", 1, 1, WeekUtils.maskFor(1, 16)).copy(source = "manual")
        val merged = ScheduleRepository.merge(auto = emptyList(), manual = listOf(manual))
        assertEquals(1, merged.size)
        assertEquals("manual", merged[0].source)
    }

    // ---- diff 差异对比 ----

    @Test
    fun `diff detects added courses`() {
        val existing = listOf(course("高数", 1, 1, WeekUtils.maskFor(1, 16)))
        val incoming = listOf(
            course("高数", 1, 1, WeekUtils.maskFor(1, 16)),
            course("英语", 2, 1, WeekUtils.maskFor(1, 16)),
        )
        val diff = ScheduleRepository.diff(existing, incoming)
        assertEquals(1, diff.added.size)
        assertEquals("英语", diff.added[0].name)
        assertEquals(0, diff.removed.size)
        assertEquals(1, diff.unchanged.size)
    }

    @Test
    fun `diff detects removed courses`() {
        val existing = listOf(
            course("高数", 1, 1, WeekUtils.maskFor(1, 16)),
            course("英语", 2, 1, WeekUtils.maskFor(1, 16)),
        )
        val incoming = listOf(course("英语", 2, 1, WeekUtils.maskFor(1, 16)))
        val diff = ScheduleRepository.diff(existing, incoming)
        assertEquals(1, diff.removed.size)
        assertEquals("高数", diff.removed[0].name)
        assertEquals(1, diff.unchanged.size)
    }

    @Test
    fun `diff detects changed courses by location`() {
        val existing = listOf(course("高数", 1, 1, WeekUtils.maskFor(1, 16)).copy(location = "教A101"))
        val incoming = listOf(course("高数", 1, 1, WeekUtils.maskFor(1, 16)).copy(location = "教B201"))
        val diff = ScheduleRepository.diff(existing, incoming)
        assertEquals(1, diff.changed.size)
        assertEquals("教A101", diff.changed[0].first.location)
        assertEquals("教B201", diff.changed[0].second.location)
    }

    @Test
    fun `diff detects internal conflicts in incoming`() {
        val incoming = listOf(
            course("高数", 1, 1, WeekUtils.maskFor(1, 16)).copy(endPeriod = 2),
            course("体育", 1, 2, WeekUtils.maskFor(1, 16)).copy(endPeriod = 3),
        )
        val diff = ScheduleRepository.diff(emptyList(), incoming)
        assertEquals(1, diff.conflicts.size)
    }

    @Test
    fun `diff empty when identical`() {
        val existing = listOf(course("高数", 1, 1, WeekUtils.maskFor(1, 16)))
        val diff = ScheduleRepository.diff(existing, existing)
        assertTrue(diff.isEmpty)
        assertEquals(1, diff.unchanged.size)
        assertEquals(1, diff.incomingSize)
    }

    @Test
    fun `diff pairs same-key courses in order and marks extras`() {
        // 同键（name+day+start+end+weeks）多门课（重复数据）：按顺序两两配对，
        // 多余的新课进 added、多余的旧课进 removed
        val existing = listOf(
            course("高数", 1, 1, WeekUtils.maskFor(1, 16)).copy(location = "A"),
            course("高数", 1, 1, WeekUtils.maskFor(1, 16)).copy(location = "B"),
        )
        val incoming = listOf(
            course("高数", 1, 1, WeekUtils.maskFor(1, 16)).copy(location = "A1"),
            course("高数", 1, 1, WeekUtils.maskFor(1, 16)).copy(location = "B1"),
            course("高数", 1, 1, WeekUtils.maskFor(1, 16)).copy(location = "C1"),
        )
        val diff = ScheduleRepository.diff(existing, incoming)
        // 前两对按索引配对进 changed，且保持 (旧, 新) 顺序
        assertEquals(2, diff.changed.size)
        assertEquals("A", diff.changed[0].first.location)
        assertEquals("A1", diff.changed[0].second.location)
        assertEquals("B", diff.changed[1].first.location)
        assertEquals("B1", diff.changed[1].second.location)
        // 多余的新课进 added
        assertEquals(1, diff.added.size)
        assertEquals("C1", diff.added[0].location)
        assertEquals(0, diff.removed.size)
    }

    @Test
    fun `diff pairs extra old courses as removed`() {
        val existing = listOf(
            course("英语", 2, 1, WeekUtils.maskFor(1, 16)).copy(location = "X"),
            course("英语", 2, 1, WeekUtils.maskFor(1, 16)).copy(location = "Y"),
        )
        val incoming = listOf(
            course("英语", 2, 1, WeekUtils.maskFor(1, 16)).copy(location = "X1"),
        )
        val diff = ScheduleRepository.diff(existing, incoming)
        assertEquals(1, diff.changed.size)
        assertEquals("X", diff.changed[0].first.location)
        assertEquals("X1", diff.changed[0].second.location)
        assertEquals(1, diff.removed.size)
        assertEquals("Y", diff.removed[0].location)
    }

    @Test
    fun `conflicts bucket stays separate from added`() {
        // conflicts 是「新数据内部」的冲突分桶提示；这些课相对旧数据仍然是新增，
        // 分桶后 added/unchanged 语义不变（高数 vs 体育 冲突，英语无冲突）
        val incoming = listOf(
            course("高数", 1, 1, WeekUtils.maskFor(1, 16)).copy(endPeriod = 2),
            course("体育", 1, 2, WeekUtils.maskFor(1, 16)).copy(endPeriod = 3),
            course("英语", 2, 1, WeekUtils.maskFor(1, 16)),
        )
        val diff = ScheduleRepository.diff(emptyList(), incoming)
        assertEquals(1, diff.conflicts.size)
        val (a, b) = diff.conflicts[0]
        assertEquals("高数", a.name)
        assertEquals("体育", b.name)
        // 冲突课程仍在 added 桶里（对旧数据而言是新增）
        assertEquals(3, diff.added.size)
        assertEquals(0, diff.unchanged.size)
        assertEquals(0, diff.removed.size)
    }
}
