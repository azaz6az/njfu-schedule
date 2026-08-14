package com.schedule.njfu.widget

import com.schedule.njfu.R
import kotlin.math.pow

/**
 * 小组件主题系统（纯数据 + 纯函数，可 JVM 单测）。
 *
 * 三套主题：morandi（莫兰迪纸感，默认）/ fresh（清新浅色）/ deep（深邃夜间），
 * 每套含浅色/深色两个 [WidgetPalette]，字段均为 ARGB Int。
 *
 * 颜色设计遵循 WCAG AA 正文对比度 ≥ 4.5:1 原则：浅色主题 textPrimary 落在浅底、
 * 深色主题 textPrimary 落在深底上均保证可读（详见 [contrastRatio] 抽测）。
 */
object WidgetTheme {

    const val KEY_MORANDI = "morandi"
    const val KEY_FRESH = "fresh"
    const val KEY_DEEP = "deep"

    /** 参数不齐时回退主题 */
    const val DEFAULT_KEY = KEY_MORANDI

    private val palettes = mapOf(
        KEY_MORANDI to ThemePair(
            light = WidgetPalette(
                bg = 0xFFFAFAF8.toInt(),
                card = 0xFFFFFFFF.toInt(),
                textPrimary = 0xFF4A4A44.toInt(),
                textSecondary = 0xFF8F8E86.toInt(),
                accent = 0xFFA88C8C.toInt(),
                headerBg = 0xFFF1E9E9.toInt(),
            ),
            dark = WidgetPalette(
                bg = 0xFF1E1E1C.toInt(),
                card = 0xFF262622.toInt(),
                textPrimary = 0xFFE8E6E0.toInt(),
                textSecondary = 0xFFA8A69E.toInt(),
                accent = 0xFFC4A9A9.toInt(),
                headerBg = 0xFF2B2B27.toInt(),
            ),
        ),
        KEY_FRESH to ThemePair(
            light = WidgetPalette(
                bg = 0xFFF2FBF9.toInt(),
                card = 0xFFFFFFFF.toInt(),
                textPrimary = 0xFF22443F.toInt(),
                textSecondary = 0xFF5E7B76.toInt(),
                accent = 0xFF3BA889.toInt(),
                headerBg = 0xFFE5F6F1.toInt(),
            ),
            dark = WidgetPalette(
                bg = 0xFF10201D.toInt(),
                card = 0xFF16302B.toInt(),
                textPrimary = 0xFFDFF2EE.toInt(),
                textSecondary = 0xFF9FBFB8.toInt(),
                accent = 0xFF4CC7A3.toInt(),
                headerBg = 0xFF1B3A34.toInt(),
            ),
        ),
        KEY_DEEP to ThemePair(
            light = WidgetPalette(
                bg = 0xFFEFF3F8.toInt(),
                card = 0xFFFFFFFF.toInt(),
                textPrimary = 0xFF26303F.toInt(),
                textSecondary = 0xFF5E7487.toInt(),
                accent = 0xFF3A5A80.toInt(),
                headerBg = 0xFFDFE7F0.toInt(),
            ),
            dark = WidgetPalette(
                bg = 0xFF10161F.toInt(),
                card = 0xFF18202C.toInt(),
                textPrimary = 0xFFE4EBF4.toInt(),
                textSecondary = 0xFF9FB0C3.toInt(),
                accent = 0xFF6E9BD0.toInt(),
                headerBg = 0xFF202A38.toInt(),
            ),
        ),
    )

    private data class ThemePair(val light: WidgetPalette, val dark: WidgetPalette)

    /**
     * 取指定主题的调色板：light/dark 各一套。
     * 未知 [themeKey] 回退 morandi。
     */
    fun paletteFor(themeKey: String, isNight: Boolean): WidgetPalette {
        val pair = palettes[themeKey] ?: palettes.getValue(DEFAULT_KEY)
        return if (isNight) pair.dark else pair.light
    }

    /**
     * 主题圆角背景 drawable（保留 16dp 圆角，避免 setBackgroundColor 变直角）。
     * 未知 [themeKey] 回退 morandi。
     */
    fun bgRes(themeKey: String, isNight: Boolean): Int = when {
        themeKey == KEY_FRESH && isNight -> R.drawable.widget_bg_fresh_dark
        themeKey == KEY_FRESH -> R.drawable.widget_bg_fresh_light
        themeKey == KEY_DEEP && isNight -> R.drawable.widget_bg_deep_dark
        themeKey == KEY_DEEP -> R.drawable.widget_bg_deep_light
        isNight -> R.drawable.widget_bg_morandi_dark
        else -> R.drawable.widget_bg_morandi_light
    }

    /** 所有主题键 */
    fun allKeys(): Set<String> = palettes.keys

    /** 主题键 -> 中文名（供设置页展示） */
    fun themeLabel(themeKey: String): String = when (themeKey) {
        KEY_MORANDI -> "莫兰迪纸感"
        KEY_FRESH -> "清新浅色"
        KEY_DEEP -> "深邃夜间"
        else -> themeLabel(DEFAULT_KEY)
    }

    /**
     * 背景色上文字是否达到 WCAG AA 正文（≥ 4.5:1）。
     * 用于在渲染期把 [WidgetPalette.card] 之类较亮色块上的
     * 文字颜色兜底成白字/深字，保证可读。
     */
    fun textContrastOk(bgArgb: Int, textArgb: Int, minRatio: Double = 4.5): Boolean =
        contrastRatio(bgArgb, textArgb) >= minRatio

    /** WCAG 相对亮度（0..1） */
    fun relativeLuminance(argb: Int): Double {
        val r = ((argb shr 16) and 0xFF) / 255.0
        val g = ((argb shr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        fun linear(c: Double) = if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
    }

    /** WCAG 对比度 (1..21) */
    fun contrastRatio(fgArgb: Int, bgArgb: Int): Double {
        val f = relativeLuminance(fgArgb)
        val b = relativeLuminance(bgArgb)
        val (hi, lo) = if (f >= b) f to b else b to f
        return (hi + 0.05) / (lo + 0.05)
    }
}

/** 一套界面调色板（全 ARGB Int）。 */
data class WidgetPalette(
    val bg: Int,
    val card: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val accent: Int,
    val headerBg: Int,
)
