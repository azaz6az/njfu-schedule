package com.schedule.njfu.ui.settings

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * WidgetGuideData 现在承载字符串资源 id；本测试通过 Robolectric 上下文取串后按原值断言，
 * 校验步骤条数、厂商清单、占位符与空串等不变量。
 * 使用空 Application 避免 App.onCreate 中的 WorkManager 初始化（本测试不涉及）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WidgetGuideDataTest {

    private val ctx: Context = RuntimeEnvironment.getApplication()

    private fun String.hasPlaceholder(): Boolean = placeholders.any { this.contains(it, ignoreCase = true) }

    private val placeholders = listOf("待定", "TODO", "xxx", "TBD", "占位")

    private val resStrings = object {
        val generalSteps = WidgetGuideData.generalSteps.map { ctx.getString(it) }
        val vendors = WidgetGuideData.vendors.map { v ->
            ctx.getString(v.nameRes) to v.stepsRes.map { ctx.getString(it) }
        }
        val faqs = WidgetGuideData.faqs.map { f ->
            ctx.getString(f.questionRes) to ctx.getString(f.answerRes)
        }
    }

    @Test
    fun `generalSteps has at least three non blank entries`() {
        assertTrue("通用步骤应至少 3 条", resStrings.generalSteps.size >= 3)
        assertTrue(resStrings.generalSteps.all { it.isNotBlank() })
    }

    @Test
    fun `generalSteps has no placeholder text`() {
        assertTrue(resStrings.generalSteps.none { it.hasPlaceholder() })
    }

    @Test
    fun `vendors contains exactly the seven expected brands`() {
        val expected = listOf(
            "小米", "华为", "OPPO", "vivo", "荣耀", "三星", "原生",
        )
        assertEquals("厂商数量应为 7", 7, resStrings.vendors.size)
        expected.forEach { brand ->
            assertTrue(
                "应包含厂商：$brand",
                resStrings.vendors.any { it.first.contains(brand) },
            )
        }
    }

    @Test
    fun `vendors have non empty names and steps`() {
        resStrings.vendors.forEach { (name, steps) ->
            assertTrue("厂商名称非空", name.isNotBlank())
            assertTrue("厂商[$name]应有步骤", steps.isNotEmpty())
            assertTrue(
                "厂商[$name]的步骤均非空",
                steps.all { it.isNotBlank() },
            )
        }
    }

    @Test
    fun `vendors have no placeholder text`() {
        resStrings.vendors.forEach { (name, steps) ->
            assertFalse(
                "厂商[$name]的名称含占位符",
                name.hasPlaceholder(),
            )
            assertTrue(
                "厂商[$name]的步骤含占位符",
                steps.none { it.hasPlaceholder() },
            )
        }
    }

    @Test
    fun `faqs has at least two non empty entries`() {
        assertTrue("常见问题应至少 2 条", resStrings.faqs.size >= 2)
        resStrings.faqs.forEach { (question, answer) ->
            assertTrue("问题非空", question.isNotBlank())
            assertTrue("答案非空", answer.isNotBlank())
        }
    }

    @Test
    fun `faqs have no placeholder text`() {
        resStrings.faqs.forEach { (question, answer) ->
            assertFalse("问题含占位符：$question", question.hasPlaceholder())
            assertFalse("答案含占位符：$answer", answer.hasPlaceholder())
        }
    }

    @Test
    fun `no empty strings anywhere`() {
        val allTexts = buildList {
            addAll(resStrings.generalSteps)
            resStrings.vendors.forEach { (name, steps) ->
                add(name)
                addAll(steps)
            }
            resStrings.faqs.forEach { (question, answer) ->
                add(question)
                add(answer)
            }
        }
        assertTrue(allTexts.none { it.isBlank() })
    }
}