package com.schedule.njfu.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.schedule.njfu.widget.WidgetData

/**
 * 星期中文名共用工具（数字星期 → 资源 id / Compose 取串）。
 * 1=一 .. 7=日；越界返回 0 / 空串，语义与 [WidgetData.dayNameRes] 一致。
 */
fun weekdayNameRes(day: Int): Int = WidgetData.dayNameRes(day)

/** Compose 环境取星期中文名（如「一」）；越界返回空串。 */
@Composable
fun weekdayName(day: Int): String {
    val res = weekdayNameRes(day)
    return if (res == 0) "" else stringResource(res)
}