package com.schedule.njfu.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 小组件主题系统测试（纯 JVM）：
 * 三主题 × 深浅齐全、未知 key 回退 morandi、关键文字与底色对比度达标。
 */
class WidgetThemeTest {

    @Test
    fun `palette for all themes and modes is complete`() {
        for (key in WidgetTheme.allKeys()) {
            for (night in listOf(false, true)) {
                val p = WidgetTheme.paletteFor(key, night)
                assertTrue("$key/$night bg", p.bg != 0)
                assertTrue("$key/$night card", p.card != 0)
                assertTrue("$key/$night textPrimary", p.textPrimary != 0)
                assertTrue("$key/$night textSecondary", p.textSecondary != 0)
                assertTrue("$key/$night accent", p.accent != 0)
                assertTrue("$key/$night headerBg", p.headerBg != 0)
            }
            // 深浅两套必须不同（否则主题切换无意义）
            assertNotEquals(
                "light/dark should differ for $key",
                WidgetTheme.paletteFor(key, false),
                WidgetTheme.paletteFor(key, true),
            )
        }
    }

    @Test
    fun `unknown key falls back to morandi`() {
        assertEquals(WidgetTheme.paletteFor(WidgetTheme.KEY_MORANDI, false), WidgetTheme.paletteFor("typo", false))
        assertEquals(WidgetTheme.paletteFor(WidgetTheme.KEY_MORANDI, true), WidgetTheme.paletteFor("typo", true))
    }

    @Test
    fun `all three themes present`() {
        assertTrue(WidgetTheme.allKeys().containsAll(
            listOf(WidgetTheme.KEY_MORANDI, WidgetTheme.KEY_FRESH, WidgetTheme.KEY_DEEP),
        ))
    }

    @Test
    fun `primary text on bg meets contrast for every theme and mode`() {
        for (key in WidgetTheme.allKeys()) {
            for (night in listOf(false, true)) {
                val p = WidgetTheme.paletteFor(key, night)
                val ratio = WidgetTheme.contrastRatio(p.textPrimary, p.bg)
                assertTrue("$key/$night textPrimary on bg ratio=$ratio", ratio >= 4.0)
                val ratioSecondary = WidgetTheme.contrastRatio(p.textSecondary, p.bg)
                // 次要文字对底色也尽量可读（提示性文字≥3.0）
                assertTrue("$key/$night textSecondary on bg ratio=$ratioSecondary", ratioSecondary >= 3.0)
            }
        }
    }

    @Test
    fun `deep dark theme uses light text`() {
        // 深邃夜间深色套应使用偏高的文字亮度（浅色文字）
        val lightText = WidgetTheme.relativeLuminance(WidgetTheme.paletteFor(WidgetTheme.KEY_DEEP, true).textPrimary)
        val darkBg = WidgetTheme.relativeLuminance(WidgetTheme.paletteFor(WidgetTheme.KEY_DEEP, true).bg)
        assertTrue("light text on dark bg", lightText > darkBg)
    }
}
