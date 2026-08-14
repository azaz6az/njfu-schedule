package com.schedule.njfu.data

import com.schedule.njfu.model.Course
import com.schedule.njfu.ui.theme.CoursePalette

object CourseMapper {
    private val palette = CoursePalette.colors.map { it.value.toInt() }

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

    /** 展示用颜色：历史数据若为旧色板颜色，映射到新色板对应槽位，保证风格统一 */
    fun displayColor(raw: Int): Int =
        if (raw in legacyPalette) palette[legacyPalette.indexOf(raw) % palette.size] else raw

    /** 供测试断言：colorFor 的结果必须落在 palette 内 */
    fun isPaletteColor(color: Int): Boolean = color in palette
}