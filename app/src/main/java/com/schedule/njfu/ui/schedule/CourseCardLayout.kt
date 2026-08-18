package com.schedule.njfu.ui.schedule

import kotlin.math.ceil

/**
 * 课程卡片内课程名的排版预算（纯计算，便于单测）。
 *
 * 课表格子「横向很窄、纵向很高」：一行最多放 2 个汉字，但两节课的卡片有
 * 近 100dp 高。策略：课程名优先【完整换行显示】，空间不足时逐级缩小字号，
 * 地点 / 节次小字只在放得下时显示（优先保留地点，节次可由格子位置推断）。
 */
internal data class CourseNameLayout(
    /** 课程名字号（sp） */
    val fontSizeSp: Float,
    /** 课程名最大行数，末行放不下时省略号 */
    val maxLines: Int,
    /** 是否显示地点行 */
    val showLocation: Boolean,
    /** 是否显示节次行（如「1-2节」） */
    val showPeriod: Boolean,
)

internal const val COURSE_NAME_MAX_FONT_SP = 12f
internal const val COURSE_NAME_MIN_FONT_SP = 9f
/** 课程名行高系数（lineHeight = fontSize * 系数） */
internal const val COURSE_NAME_LINE_FACTOR = 1.4f
/** 小字（地点 / 节次）行高（dp） */
internal const val COURSE_META_LINE_HEIGHT_DP = 14f

/** 候选字号，从大到小尝试 */
private val FONT_CANDIDATES = listOf(12f, 11f, 10f, 9f)

/**
 * 计算课程名在 [textWidthDp] x [textHeightDp]（卡片内文字区域，已扣除白条与内边距）
 * 中的字号与行数。
 *
 * @param locationPresent 课程是否有地点；决定小字最多占几行（地点+节次=2 / 仅节次=1）
 * @param fontScale 系统字体缩放（LocalDensity.fontScale）。预算按 dp 计算而文字按 sp
 *   渲染：sp 文字的「实际占用」会随 fontScale 放大，因此先把 dp 预算除以 fontScale
 *   再排版，避免系统字体放大时文字超出预算、卡底行被裁。
 */
internal fun computeCourseNameLayout(
    name: String,
    locationPresent: Boolean,
    textWidthDp: Float,
    textHeightDp: Float,
    fontScale: Float = 1f,
): CourseNameLayout {
    require(textWidthDp > 0f) { "textWidthDp must be positive" }
    require(textHeightDp > 0f) { "textHeightDp must be positive" }
    require(fontScale > 0f) { "fontScale must be positive" }
    // 文字总宽度估算：CJK / 全角按 1 个字符宽，ASCII 按 0.55
    val effLen = name.sumOf { c -> if (c.code > 0x2E7F) 1.0 else 0.55 }
    val maxMeta = if (locationPresent) 2 else 1
    // dp 预算除以 fontScale：sp 文字实际占用的 dp 空间 = sp * fontScale
    val effWidth = textWidthDp / fontScale
    val effHeight = textHeightDp / fontScale
    for (f in FONT_CANDIDATES) {
        val charsPerLine = (effWidth / f).toInt().coerceAtLeast(1)
        val lines = ceil(effLen / charsPerLine).toInt().coerceAtLeast(1)
        val nameHeight = lines * f * COURSE_NAME_LINE_FACTOR
        // 同字号下优先保留更多小字行；地点优先于节次
        for (k in maxMeta downTo 0) {
            if (nameHeight + k * COURSE_META_LINE_HEIGHT_DP <= effHeight) {
                return CourseNameLayout(
                    fontSizeSp = f,
                    maxLines = lines,
                    showLocation = k >= 1 && locationPresent,
                    // 无地点时唯一的可留小字行就是节次行
                    showPeriod = if (locationPresent) k >= 2 else k >= 1,
                )
            }
        }
    }
    // 极小卡片（如多门课并排挤成窄条）：9sp 下按可用行数裁剪，末行省略号
    val maxLines = (effHeight / (COURSE_NAME_MIN_FONT_SP * COURSE_NAME_LINE_FACTOR))
        .toInt()
        .coerceAtLeast(1)
    return CourseNameLayout(
        fontSizeSp = COURSE_NAME_MIN_FONT_SP,
        maxLines = maxLines,
        showLocation = false,
        showPeriod = false,
    )
}
