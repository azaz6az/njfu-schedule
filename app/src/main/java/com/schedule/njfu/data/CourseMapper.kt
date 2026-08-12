package com.schedule.njfu.data

import com.schedule.njfu.model.Course

object CourseMapper {
    private val palette = listOf(0xFF3F51B5, 0xFF00897B, 0xFFF4511E, 0xFF6A1B9A,
        0xFFC62828, 0xFF2E7D32, 0xFFAD1457, 0xFF1565C0, 0xFFEF6C00, 0xFF00838F)
        .map { it.toInt() }

    fun colorFor(name: String): Int = palette[Math.floorMod(name.hashCode(), palette.size)]

    /** 供测试断言：colorFor 的结果必须落在 palette 内 */
    fun isPaletteColor(color: Int): Boolean = color in palette
}
