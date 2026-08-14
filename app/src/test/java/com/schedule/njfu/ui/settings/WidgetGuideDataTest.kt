package com.schedule.njfu.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetGuideDataTest {

    private val placeholders = listOf("待定", "TODO", "xxx", "TBD", "占位")

    private fun String.hasPlaceholder(): Boolean =
        placeholders.any { this.contains(it, ignoreCase = true) }

    @Test
    fun `generalSteps has at least three non blank entries`() {
        assertTrue("通用步骤应至少 3 条", WidgetGuideData.generalSteps.size >= 3)
        assertTrue(WidgetGuideData.generalSteps.all { it.isNotBlank() })
    }

    @Test
    fun `generalSteps has no placeholder text`() {
        assertTrue(WidgetGuideData.generalSteps.none { it.hasPlaceholder() })
    }

    @Test
    fun `vendors contains exactly the seven expected brands`() {
        val expected = listOf(
            "小米", "华为", "OPPO", "vivo", "荣耀", "三星", "原生",
        )
        assertEquals("厂商数量应为 7", 7, WidgetGuideData.vendors.size)
        expected.forEach { brand ->
            assertTrue(
                "应包含厂商：$brand",
                WidgetGuideData.vendors.any { it.name.contains(brand) },
            )
        }
    }

    @Test
    fun `vendors have non empty names and steps`() {
        WidgetGuideData.vendors.forEach { vendor ->
            assertTrue("厂商名称非空", vendor.name.isNotBlank())
            assertTrue("厂商[${vendor.name}]应有步骤", vendor.steps.isNotEmpty())
            assertTrue(
                "厂商[${vendor.name}]的步骤均非空",
                vendor.steps.all { it.isNotBlank() },
            )
        }
    }

    @Test
    fun `vendors have no placeholder text`() {
        WidgetGuideData.vendors.forEach { vendor ->
            assertFalse(
                "厂商[${vendor.name}]的名称含占位符",
                vendor.name.hasPlaceholder(),
            )
            assertTrue(
                "厂商[${vendor.name}]的步骤含占位符",
                vendor.steps.none { it.hasPlaceholder() },
            )
        }
    }

    @Test
    fun `faqs has at least two non empty entries`() {
        assertTrue("常见问题应至少 2 条", WidgetGuideData.faqs.size >= 2)
        WidgetGuideData.faqs.forEach { faq ->
            assertTrue("问题非空", faq.question.isNotBlank())
            assertTrue("答案非空", faq.answer.isNotBlank())
        }
    }

    @Test
    fun `faqs have no placeholder text`() {
        WidgetGuideData.faqs.forEach { faq ->
            assertFalse("问题含占位符：${faq.question}", faq.question.hasPlaceholder())
            assertFalse("答案含占位符：${faq.answer}", faq.answer.hasPlaceholder())
        }
    }

    @Test
    fun `no empty strings anywhere`() {
        val allTexts = buildList {
            addAll(WidgetGuideData.generalSteps)
            WidgetGuideData.vendors.forEach { v ->
                add(v.name)
                addAll(v.steps)
            }
            WidgetGuideData.faqs.forEach { f ->
                add(f.question)
                add(f.answer)
            }
        }
        assertTrue(allTexts.none { it.isBlank() })
    }
}
