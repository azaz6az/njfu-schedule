package com.schedule.njfu.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * defaultSemesterStartFor 的取值逻辑测试（通过注入固定 today 保证可测）。
 *
 * 关键候选：去年 9-1、今年 3-1、今年 9-1；取最近一个不晚于 today 的候选并归一化到当周周一。
 * 覆盖 1 月 / 2 月上旬 / 3-1 当天 / 9-1 当天 / 9 月中旬 / 全部候选在未来 的取值。
 *
 * 星期参考（已验证）：
 *  - 2025-09-01 是周一（无需归一化）
 *  - 2026-03-01 是周日 → 归一化到 2026-02-23（周一）
 *  - 2026-09-01 是周二 → 归一化到 2026-08-31（周一）
 */
class SettingsKeysTest {

    @Test
    fun `january falls back to last years autumn start`() {
        // 1 月~2 月：最近开学日是去年 9-1（此前缺候选导致退回未来 9-1 的 major bug）
        assertEquals(
            LocalDate.parse("2025-09-01"),
            defaultSemesterStartFor(LocalDate.parse("2026-01-15")),
        )
    }

    @Test
    fun `early february still uses last autumn start`() {
        assertEquals(
            LocalDate.parse("2025-09-01"),
            defaultSemesterStartFor(LocalDate.parse("2026-02-10")),
        )
    }

    @Test
    fun `march 1st picks spring start normalized to monday`() {
        // 3-1 当天：候选取今年 3-1；当天是周日 → 归一化到当周周一 2026-02-23
        assertEquals(
            LocalDate.parse("2026-02-23"),
            defaultSemesterStartFor(LocalDate.parse("2026-03-01")),
        )
    }

    @Test
    fun `september 1st picks autumn start normalized to monday`() {
        // 9-1 当天：候选取今年 9-1；当天是周二 → 归一化到当周周一 2026-08-31
        assertEquals(
            LocalDate.parse("2026-08-31"),
            defaultSemesterStartFor(LocalDate.parse("2026-09-01")),
        )
    }

    @Test
    fun `mid september keeps the same autumn start week`() {
        assertEquals(
            LocalDate.parse("2026-08-31"),
            defaultSemesterStartFor(LocalDate.parse("2026-09-15")),
        )
    }

    @Test
    fun `august uses current years spring start`() {
        // 3 月~8 月区间：最近开学日是今年 3-1 → 归一化后 2026-02-23
        assertEquals(
            LocalDate.parse("2026-02-23"),
            defaultSemesterStartFor(LocalDate.parse("2026-08-15")),
        )
    }

    @Test
    fun `early january picks last autumn start and normalizes to monday`() {
        // 2025-01-15：候选 2024-09-01（过去）、2025-03-01（未来）、2025-09-01（未来）
        // → 取 2024-09-01（周日）→ 归一化到当周周一 2024-08-26。
        // 说明：候选中的「去年 9-1」对任意真实日期都必然在过去（去年 9-1 < 今年 1-1 ≤ today），
        // 因此「全部候选在未来」的兜底分支（返回去年 9-1）实际不可达，仅作防御保留。
        assertEquals(
            LocalDate.parse("2024-08-26"),
            defaultSemesterStartFor(LocalDate.parse("2025-01-15")),
        )
    }
}