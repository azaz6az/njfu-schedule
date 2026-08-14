package com.schedule.njfu.data

import com.schedule.njfu.ui.theme.CoursePalette
import kotlin.math.pow

object CourseMapper {
    private val palette = CoursePalette.colors.map { it.value.toInt() }

    /** WCAG 白字对比度阈值：背景需足够深，白字才可读（AA 正文 ≥ 4.5:1） */
    private const val MAX_BG_LUMINANCE = 0.1833

    /**
     * 旧版色板（用于把历史数据映射到当前色板）：
     * 前 6 位为旧莫兰迪浅色（0.1.0 已写入数据库的卡片色），对应映射到加深后的同槽位；
     * 后 10 位为最早的高饱和色，索引偏移 6 后取模结果与旧映射一致，不改变既有数据。
     */
    private val legacyPalette = listOf(
        0xFFA8BCA3, 0xFF9FB4C7, 0xFFD6B8B8, 0xFFC0B4A8, 0xFFB3A9C4, 0xFFA9BDB5,
        0xFF3F51B5, 0xFF00897B, 0xFFF4511E, 0xFF6A1B9A,
        0xFFC62828, 0xFF2E7D32, 0xFFAD1457, 0xFF1565C0, 0xFFEF6C00, 0xFF00838F,
    ).map { it.toInt() }

    fun colorFor(name: String): Int = palette[Math.floorMod(name.hashCode(), palette.size)]

    /**
     * 展示用颜色：
     * 1. 历史 legacy 色板值 → 映射到当前色板对应槽位（保持风格统一）；
     * 2. 其他任何过浅颜色（白字对比度不足 4.5:1，如 JSON 备份导入的浅色/白色）
     *    → 兜底映射到色板槽位，避免「白色卡片 + 白字」完全不可读；
     * 3. 足够深的自定义颜色原样保留。
     */
    fun displayColor(raw: Int): Int {
        if (raw in legacyPalette) return palette[legacyPalette.indexOf(raw) % palette.size]
        return if (whiteTextContrastOk(raw)) raw else palette[Math.floorMod(raw, palette.size)]
    }

    /** 该 ARGB 背景色上白字是否可达 WCAG AA（≥ 4.5:1） */
    fun whiteTextContrastOk(argb: Int): Boolean = relativeLuminance(argb) <= MAX_BG_LUMINANCE

    /** 相对亮度（WCAG 定义，0..1） */
    fun relativeLuminance(argb: Int): Double {
        val r = ((argb shr 16) and 0xFF) / 255.0
        val g = ((argb shr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        fun linear(c: Double) = if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
    }

    /** 供测试断言：colorFor 的结果必须落在 palette 内 */
    fun isPaletteColor(color: Int): Boolean = color in palette
}