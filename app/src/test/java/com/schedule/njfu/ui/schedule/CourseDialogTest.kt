package com.schedule.njfu.ui.schedule

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI 测试：在 JVM 上用 Robolectric 运行（无需模拟器/真机）。
 * 覆盖 CourseDialog 表单填写与保存回调、校验失败提示。
 * 使用空 Application 避免 App.onCreate 中的 WorkManager 初始化（本测试不涉及）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CourseDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 按输入框 label 定位可输入节点（不依赖节点遍历顺序） */
    private fun ComposeContentTestRule.field(label: String) =
        onNode(hasSetTextAction() and hasAnyDescendant(hasText(label)), useUnmergedTree = true)

    @Test
    fun `filling form and saving emits course with parsed fields`() {
        var saved: Course? = null
        composeRule.setContent {
            CourseDialog(
                course = null,
                weekDay = 1,
                weekNumber = 3,
                onSave = { saved = it },
                onDelete = null,
                onDismiss = {},
            )
        }
        composeRule.field("课程名称").performTextReplacement("高等数学")
        composeRule.field("教师").performTextReplacement("张老师")
        composeRule.field("地点").performTextReplacement("教A101")
        composeRule.field("开始节").performTextReplacement("2")
        composeRule.field("结束节").performTextReplacement("4")

        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitForIdle()

        assertNotNull(saved)
        assertEquals("高等数学", saved!!.name)
        assertEquals("张老师", saved!!.teacher)
        assertEquals("教A101", saved!!.location)
        assertEquals(1, saved!!.dayOfWeek)       // 默认本周一
        assertEquals(2, saved!!.startPeriod)
        assertEquals(4, saved!!.endPeriod)
        // 周次默认 = 当前周（3）
        assertTrue(WeekUtils.contains(saved!!.weeks, 3))
        assertEquals("manual", saved!!.source)
    }

    @Test
    fun `invalid period shows error and does not save`() {
        var saved: Course? = null
        composeRule.setContent {
            CourseDialog(
                course = null,
                weekDay = 1,
                weekNumber = 1,
                onSave = { saved = it },
                onDelete = null,
                onDismiss = {},
            )
        }
        composeRule.field("课程名称").performTextReplacement("体育")
        composeRule.field("开始节").performTextReplacement("5")
        composeRule.field("结束节").performTextReplacement("3")  // 结束节 < 开始节 → 非法

        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitForIdle()

        assertEquals(null, saved)
        composeRule.onNodeWithText("结束节需在 5..12 之间", substring = true).assertExists()
    }
}
