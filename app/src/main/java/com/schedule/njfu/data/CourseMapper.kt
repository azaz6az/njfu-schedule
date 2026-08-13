package com.schedule.njfu.data

import com.schedule.njfu.model.Course
import com.schedule.njfu.ui.theme.CoursePalette

object CourseMapper {
    private val palette = CoursePalette.colors.map { it.value.toInt() }

    /** 旧版高饱和色板（用于把历史数据映射到莫兰迪新色板） */
    private val legacyPalette = listOf(0xFF3F51B5, 0xFF00897B, 0xFFF4511E, 0xFF6A1B9A,
        0xFFC62828, 0xFF2E7D32, 0xFFAD1457, 0xFF1565C0, 0xFFEF6C00, 0xFF00838F)
        .map { it.toInt() }

    fun colorFor(name: String): Int = palette[Math.floorMod(name.hashCode(), palette.size)]

    /** 展示用颜色：历史数据若为旧色板颜色，映射到新色板对应槽位，保证风格统一 */
    fun displayColor(raw: Int): Int =
        if (raw in legacyPalette) palette[legacyPalette.indexOf(raw) % palette.size] else raw

    /** 供测试断言：colorFor 的结果必须落在 palette 内 */
    fun isPaletteColor(color: Int): Boolean = color in palette
}
