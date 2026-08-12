package com.schedule.njfu.data

import org.junit.Assert.*
import org.junit.Test

class CourseMapperTest {

    @Test
    fun `colorFor is idempotent`() {
        assertEquals(CourseMapper.colorFor("高等数学"), CourseMapper.colorFor("高等数学"))
        assertEquals(CourseMapper.colorFor("大学英语"), CourseMapper.colorFor("大学英语"))
    }

    @Test
    fun `colorFor returns a palette color for any course name`() {
        val names = listOf(
            "高等数学", "大学英语", "数据结构", "体育", "毛概",
            "", "A", "线性代数",
            "这门课程的名字非常长以至于需要换行显示",
            "课程 1234-!@#",
        )
        for (name in names) {
            val color = CourseMapper.colorFor(name)
            assertTrue(
                "colorFor(\"$name\") = $color 不在 palette 中",
                CourseMapper.isPaletteColor(color),
            )
        }
    }
}
