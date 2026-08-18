package com.schedule.njfu.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schedule.njfu.R
import com.schedule.njfu.model.WeekUtils
import com.schedule.njfu.ui.weekdayName

/** 课程名列宽（文字区，超出省略） */
private val NameColumnWidth = 112.dp
/** 课程名与周格之间的间距 */
private val NameGap = 8.dp
/** 课程名列总宽：周号表头与展开详情的缩进都按此对齐 */
private val NameTotalWidth = NameColumnWidth + NameGap
/** 每周格子的总占宽（含左右间距） */
private val CellSize = 24.dp
/** 周格之间的间隙 */
private val CellGap = 2.dp
/** 周格高度 */
private val CellHeight = 18.dp

/**
 * 「周次」页签：整学期（1..WeekUtils.MAX_WEEKS）课程分布热力图。
 * 一行一门课：左侧课程名 + 右侧按周分格（有课填课程色，无课留浅色）；
 * 顶行周号 1 标注起始、其后每 5 周标一次；整表可横向/纵向滚动；
 * 点击行展开/收起详情（星期、节次、地点、教师、周次跨度）。
 */
@Composable
fun WeekHeatmapScreen(viewModel: WeekHeatmapViewModel) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    // 展开/收起（手风琴式）：同一时间只展开一门课，再次点击收起
    var expandedId by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.heatmap_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        if (rows.isEmpty()) {
            // 空状态：暂无课程
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.heatmap_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            WeekHeatmapTable(
                rows = rows,
                expandedId = expandedId,
                onToggle = { id -> expandedId = if (expandedId == id) null else id },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@Composable
private fun WeekHeatmapTable(
    rows: List<HeatmapRow>,
    expandedId: Long?,
    onToggle: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState()),
    ) {
        // 顶行周号：第 1 周标注起始，其后每 5 周标一次（1、5、10…），避免拥挤
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(NameTotalWidth))
            repeat(WeekUtils.MAX_WEEKS) { i ->
                val week = i + 1
                Box(
                    Modifier
                        .width(CellSize)
                        .padding(horizontal = CellGap / 2),
                    contentAlignment = Alignment.Center,
                ) {
                    if (week == 1 || week % 5 == 0) {
                        Text(
                            "$week",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Spacer(Modifier.height(6.dp))
        rows.forEach { row ->
            HeatmapRowItem(
                row = row,
                expanded = expandedId == row.id,
                onToggle = onToggle,
            )
            // 行间留白
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun HeatmapRowItem(
    row: HeatmapRow,
    expanded: Boolean,
    onToggle: (Long) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onToggle(row.id) }
                .padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                row.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .width(NameColumnWidth)
                    .padding(end = NameGap),
            )
            // 每周格子：有课填课程色，无课填浅色（纸感 surfaceVariant）
            repeat(WeekUtils.MAX_WEEKS) { i ->
                val week = i + 1
                Box(
                    Modifier
                        .width(CellSize)
                        .height(CellHeight)
                        .padding(horizontal = CellGap / 2)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (row.hasClass(week)) Color(row.color)
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                )
            }
            Spacer(Modifier.width(8.dp))
            // 行尾周次跨度小结（如 1-16周 / 全学期）
            Text(
                heatmapSpanText(row.weeksMask),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (expanded) {
            HeatmapDetail(
                row = row,
                modifier = Modifier.padding(start = NameTotalWidth, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun HeatmapDetail(row: HeatmapRow, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            stringResource(
                R.string.heatmap_detail_schedule,
                weekdayName(row.dayOfWeek),
                row.startPeriod,
                row.endPeriod,
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        if (row.location.isNotBlank()) {
            Text(
                stringResource(R.string.heatmap_detail_location, row.location),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (row.teacher.isNotBlank()) {
            Text(
                stringResource(R.string.heatmap_detail_teacher, row.teacher),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(R.string.heatmap_detail_weeks, heatmapSpanText(row.weeksMask)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 周次跨度小结文案：整学期 →「全学期」，否则按连续区间拼装（如 1-5、7-11周） */
@Composable
private fun heatmapSpanText(mask: Int): String {
    val fullMask = WeekUtils.maskFor(1, WeekUtils.MAX_WEEKS)
    if (mask == fullMask) return stringResource(R.string.heatmap_span_full)
    return weekRanges(mask).map { r ->
        if (r.first == r.last) stringResource(R.string.heatmap_week_single, r.first)
        else stringResource(R.string.heatmap_week_range, r.first, r.last)
    }.joinToString("、")
}