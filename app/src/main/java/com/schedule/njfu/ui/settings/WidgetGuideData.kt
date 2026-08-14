package com.schedule.njfu.ui.settings

/** 单个厂商的小组件添加引导 */
data class VendorGuide(
    val name: String,
    val steps: List<String>,
)

/** 常见问题条目 */
data class FaqGuide(
    val question: String,
    val answer: String,
)

/**
 * 「桌面小组件添加引导」的纯数据源。
 *
 * 仅含中文文案，供 [WidgetGuideSection] 渲染与 `WidgetGuideDataTest` 校验；
 * 不读写任何设置、不依赖 ViewModel。
 */
object WidgetGuideData {

    /** 通用添加三步（不区分厂商） */
    val generalSteps: List<String> = listOf(
        "在桌面空白处长按，进入桌面编辑模式",
        "选择「小组件 / 窗口小工具 / 添加小部件」",
        "在列表里找到「今日课程」「下一节课」「考试倒计时」等，拖到桌面",
    )

    /** 7 家主流厂商的差异化引导 */
    val vendors: List<VendorGuide> = listOf(
        VendorGuide(
            name = "小米 / 澎湃 OS",
            steps = listOf(
                "桌面双指捏合 → 添加小部件；或桌面空白处长按 → 添加小部件",
                "搜索「课程表」即可找到我们的桌面小组件",
            ),
        ),
        VendorGuide(
            name = "华为 / 鸿蒙",
            steps = listOf(
                "桌面双指捏合 → 服务卡片 / 小工具；或长按桌面 → 更多服务卡片",
                "在卡片列表里搜索「课程表」",
            ),
        ),
        VendorGuide(
            name = "OPPO / ColorOS",
            steps = listOf(
                "长按桌面 → 添加卡片和小部件；或双指捏合 → 小部件",
                "搜索「课程表」即可找到我们的桌面小组件",
            ),
        ),
        VendorGuide(
            name = "vivo / OriginOS",
            steps = listOf(
                "长按桌面空白处 → 添加小部件 → 找到「课程表」",
                "OriginOS 可在变形器里调整小组件尺寸",
            ),
        ),
        VendorGuide(
            name = "荣耀 / MagicOS",
            steps = listOf(
                "长按桌面 → 添加小工具；或双指捏合 → 小工具",
                "搜索「课程表」即可找到我们的桌面小组件",
            ),
        ),
        VendorGuide(
            name = "三星 / One UI",
            steps = listOf(
                "长按桌面 → 小组件 → 找到「课程表」",
                "找到后长按拖动到桌面即可",
            ),
        ),
        VendorGuide(
            name = "原生 Android",
            steps = listOf(
                "长按桌面空白处 → 小组件",
                "在列表里找到「课程表」并拖到桌面",
            ),
        ),
    )

    /** 常见问题（至少 2 条） */
    val faqs: List<FaqGuide> = listOf(
        FaqGuide(
            question = "添加后不更新怎么办",
            answer = "打开一次 App 会自动刷新；也可以点一下小组件；或到设置页「小米设备优化」区块手动刷新。",
        ),
        FaqGuide(
            question = "找不到我们的小组件",
            answer = "在小组件列表顶部的搜索框搜「课程表」；有些系统会把小组件放在「全部」分类里。",
        ),
    )
}
