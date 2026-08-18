package com.schedule.njfu.ui.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 回归测试：课表网格中「第一个课程卡片点不动」。
 * 用真实坐标触摸注入（not 语义树 onClick，走真实 hit-test 路径），
 * 验证 offset 摆放的课程卡片都能命中点击。
 *
 * 原 bug 根因：offset 写在 TooltipBox 内部的 CourseCard 上，TooltipBox 的
 * 包装 Box 只按内容自然尺寸测量，全部堆叠在网格原点（= 第一张卡位置），
 * 后组合的 TooltipBox 手势检测器拦截了第一张卡的点击。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ScheduleGridClickTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun course(name: String, day: Int, start: Int, end: Int) =
        Course(name = name, dayOfWeek = day, startPeriod = start, endPeriod = end,
            weeks = WeekUtils.maskFor(1, 16), color = 0)

    @Test
    fun `every placed course card fires onCourseClick via real touch`() {
        val courses = listOf(
            course("高数", day = 1, start = 1, end = 2), // 第一个：周一 1-2 节（左上角）
            course("体育", day = 1, start = 3, end = 4), // 第一列，但位置更低
            course("英语", day = 2, start = 3, end = 4), // 其他列对照
        )
        val weekDates = (0..6).map { java.time.LocalDate.parse("2025-10-06").plusDays(it.toLong()) }
        val cells = WeekGrid.cellsFor(courses, week = 1, weekDates = weekDates, shifts = emptyMap())
        val clicked = mutableListOf<String>()
        val cellClicked = mutableListOf<Int>()

        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.size(400.dp, 700.dp)) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        WeekGridContent(
                            cells = cells,
                            rowHeight = 48.dp,
                            todayDay = 0,
                            highlightToday = false,
                            onCellClick = { cellClicked.add(it) },
                            onCourseClick = { clicked.add(it.name) },
                        )
                    }
                }
            }
        }

        // 逐个按真实位置触摸注入点击
        for (name in listOf("高数", "体育", "英语")) {
            composeRule.onNodeWithText(name).performTouchInput { click() }
            composeRule.waitForIdle()
        }

        assertEquals("每张卡（含左上角第一张）都必须可点击", listOf("高数", "体育", "英语"), clicked)
        // 上图左上角确实是 cells[0] 这张卡
        assertEquals(courses.size, cells.size)
        assertEquals("高数", cells[0].course.name)
        assertEquals(0, cells[0].row)
        assertEquals(0, cells[0].col)
        // 点卡不穿透到底层空格
        assertEquals(0, cellClicked.size)
    }
}