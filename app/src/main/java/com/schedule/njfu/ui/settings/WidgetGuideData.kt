package com.schedule.njfu.ui.settings

import androidx.annotation.StringRes
import com.schedule.njfu.R

/** 单个厂商的小组件添加引导（文案以字符串资源 id 承载，渲染时经 stringResource 取串） */
data class VendorGuide(
    @StringRes val nameRes: Int,
    @StringRes val stepsRes: List<Int>,
)

/** 常见问题条目（文案以字符串资源 id 承载） */
data class FaqGuide(
    @StringRes val questionRes: Int,
    @StringRes val answerRes: Int,
)

/**
 * 「桌面小组件添加引导」的纯数据源。
 *
 * 仅承载文案的字符串资源 id，供 [WidgetGuideSection] 渲染与 `WidgetGuideDataTest` 校验；
 * 不读写任何设置、不依赖 ViewModel。
 */
object WidgetGuideData {

    /** 通用添加三步（不区分厂商） */
    val generalSteps: List<Int> = listOf(
        R.string.widget_guide_step_1,
        R.string.widget_guide_step_2,
        R.string.widget_guide_step_3,
    )

    /** 7 家主流厂商的差异化引导 */
    val vendors: List<VendorGuide> = listOf(
        VendorGuide(
            nameRes = R.string.widget_guide_vendor_xiaomi,
            stepsRes = listOf(
                R.string.widget_guide_vendor_xiaomi_step_1,
                R.string.widget_guide_vendor_xiaomi_step_2,
            ),
        ),
        VendorGuide(
            nameRes = R.string.widget_guide_vendor_huawei,
            stepsRes = listOf(
                R.string.widget_guide_vendor_huawei_step_1,
                R.string.widget_guide_vendor_huawei_step_2,
            ),
        ),
        VendorGuide(
            nameRes = R.string.widget_guide_vendor_oppo,
            stepsRes = listOf(
                R.string.widget_guide_vendor_oppo_step_1,
                R.string.widget_guide_vendor_oppo_step_2,
            ),
        ),
        VendorGuide(
            nameRes = R.string.widget_guide_vendor_vivo,
            stepsRes = listOf(
                R.string.widget_guide_vendor_vivo_step_1,
                R.string.widget_guide_vendor_vivo_step_2,
            ),
        ),
        VendorGuide(
            nameRes = R.string.widget_guide_vendor_honor,
            stepsRes = listOf(
                R.string.widget_guide_vendor_honor_step_1,
                R.string.widget_guide_vendor_honor_step_2,
            ),
        ),
        VendorGuide(
            nameRes = R.string.widget_guide_vendor_samsung,
            stepsRes = listOf(
                R.string.widget_guide_vendor_samsung_step_1,
                R.string.widget_guide_vendor_samsung_step_2,
            ),
        ),
        VendorGuide(
            nameRes = R.string.widget_guide_vendor_aosp,
            stepsRes = listOf(
                R.string.widget_guide_vendor_aosp_step_1,
                R.string.widget_guide_vendor_aosp_step_2,
            ),
        ),
    )

    /** 常见问题（至少 2 条） */
    val faqs: List<FaqGuide> = listOf(
        FaqGuide(
            questionRes = R.string.widget_guide_faq_1_q,
            answerRes = R.string.widget_guide_faq_1_a,
        ),
        FaqGuide(
            questionRes = R.string.widget_guide_faq_2_q,
            answerRes = R.string.widget_guide_faq_2_a,
        ),
    )
}