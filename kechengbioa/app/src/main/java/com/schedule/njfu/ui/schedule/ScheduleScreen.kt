package com.schedule.njfu.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schedule.njfu.data.CourseMapper
import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils
import java.time.LocalDate

private val DayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
private val GutterWidth = 30.dp
private val HeaderHeight = 34.dp
private val RowHeight = 48.dp
private val CourseGap = 3.dp

@Composable
fun ScheduleScreen(viewModel: ScheduleViewModel) {
    val courses by viewModel.courses.collectAsStateWithLifecycle()
    val selectedWeek by viewModel.selectedWeek.collectAsStateWithLifecycle()
    val currentWeek by viewModel.currentWeek.collectAsStateWithLifecycle()
    val semesterStart by viewModel.semesterStart.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<DialogState?>(null) }

    LaunchedEffect(Unit) { viewModel.initIfNeeded() }

    val cells = remember(selectedWeek, courses) {
        WeekGrid.cellsFor(courses, selectedWeek)
    }
    val todayDay = remember { LocalDate.now().dayOfWeek.value }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            WeekSwitcher(
                selectedWeek = selectedWeek,
                currentWeek = currentWeek,
                semesterStart = semesterStart,
                onPrev = { viewModel.selectWeek(selectedWeek - 1) },
                onNext = { viewModel.selectWeek(selectedWeek + 1) },
                onBackToCurrent = { viewModel.selectWeek(currentWeek) },
            )
            Box(Modifier.fillMaxWidth()) {
                WeekGridContent(
                    cells = cells,
                    todayDay = todayDay,
                    onCellClick = { day -> dialog = DialogState(null, day, selectedWeek) },
                    onCourseClick = { c -> dialog = DialogState(c, c.dayOfWeek, selectedWeek) },
                )
                if (courses.isEmpty()) {
                    Text(
                        "暂无课程，点右下角 ＋ 加课",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = 120.dp),
                    )
                }
            }
            // 底部留白，避免 FAB 遮挡网格底部
            Spacer(Modifier.height(96.dp))
        }
        ExtendedFloatingActionButton(
            onClick = { dialog = DialogState(null, 1, selectedWeek) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("加课")
        }
    }

    dialog?.let { d ->
        CourseDialog(
            course = d.course,
            weekDay = d.weekDay,
            weekNumber = d.weekNumber,
            onSave = { c ->
                viewModel.addCourse(c)
                dialog = null
            },
            onDelete = d.course?.let { c ->
                if (c.id > 0) {
                    { id -> viewModel.deleteCourse(id); dialog = null }
                } else {
                    null
                }
            },
            onDismiss = { dialog = null },
        )
    }
}

private data class DialogState(
    val course: Course?,
    val weekDay: Int,
    val weekNumber: Int,
)

@Composable
private fun WeekSwitcher(
    selectedWeek: Int,
    currentWeek: Int,
    semesterStart: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onBackToCurrent: () -> Unit,
) {
    val monday = semesterStart.plusWeeks((selectedWeek - 1).toLong())
    val sunday = monday.plusDays(6)
    val rangeText = "${monday.monthValue}月${monday.dayOfMonth}日 – ${sunday.monthValue}月${sunday.dayOfMonth}日"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "第 $selectedWeek 周",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                rangeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (currentWeek > 0) {
            AssistChip(
                onClick = onBackToCurrent,
                enabled = selectedWeek != currentWeek,
                label = {
                    Text(if (selectedWeek == currentWeek) "本周" else "回到当前周")
                },
            )
        }
        Spacer(Modifier.width(8.dp))
        RoundArrowButton(onClick = onPrev, enabled = selectedWeek > 1, isPrev = true)
        Spacer(Modifier.width(8.dp))
        RoundArrowButton(onClick = onNext, enabled = selectedWeek < WeekUtils.MAX_WEEKS, isPrev = false)
    }
}

@Composable
private fun RoundArrowButton(onClick: () -> Unit, enabled: Boolean, isPrev: Boolean) {
    val icon = if (isPrev) Icons.AutoMirrored.Filled.KeyboardArrowLeft
    else Icons.AutoMirrored.Filled.KeyboardArrowRight
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.outline,
        ),
    ) {
        Icon(icon, contentDescription = if (isPrev) "上一周" else "下一周")
    }
}

@Composable
private fun WeekGridContent(
    cells: List<WeekGrid.Cell>,
    todayDay: Int,
    onCellClick: (Int) -> Unit,
    onCourseClick: (Course) -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        val gridWidth = maxWidth - GutterWidth
        val colWidth = gridWidth / 7
        Column {
            // 表头：一..日，今日高亮
            Row(Modifier.height(HeaderHeight)) {
                Spacer(Modifier.width(GutterWidth))
                repeat(7) { i ->
                    val day = i + 1
                    Box(
                        Modifier
                            .width(colWidth)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "周${DayLabels[i]}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (day == todayDay) FontWeight.Bold else FontWeight.Normal,
                            color = if (day == todayDay) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(Modifier.height(RowHeight * WeekGrid.MAX_PERIODS)) {
                // 节次行标签 1-10
                Column(Modifier.width(GutterWidth)) {
                    repeat(WeekGrid.MAX_PERIODS) { r ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(RowHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${r + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                // 纸感网格容器：背景为网格线色，surface 单元格留 1dp 间隙自然成线
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(1.dp),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        repeat(WeekGrid.MAX_PERIODS) { r ->
                            Row(Modifier
                                .fillMaxWidth()
                                .weight(1f)
                            ) {
                                repeat(7) { c ->
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .padding(1.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (c + 1 == todayDay) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surface
                                                }
                                            )
                                            .clickable { onCellClick(c + 1) },
                                    )
                                }
                            }
                        }
                    }
                    cells.forEach { cell ->
                        // 同格多门课并排：把列宽按数量均分
                        val slotWidth = colWidth / cell.overlapCount
                        CourseCard(
                            course = cell.course,
                            modifier = Modifier
                                .offset(
                                    x = colWidth * cell.col + slotWidth * cell.overlapIndex,
                                    y = RowHeight * cell.row,
                                )
                                .width(slotWidth - CourseGap)
                                .height(RowHeight * cell.rowSpan - CourseGap)
                                .clickable { onCourseClick(cell.course) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseCard(course: Course, modifier: Modifier) {
    val rawColor = if (course.color == 0) CourseMapper.colorFor(course.name) else course.color
    val bg = Color(CourseMapper.displayColor(rawColor))
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg),
    ) {
        // 左侧白色点缀条（纸感风格）
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.7f)),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 5.dp, vertical = 3.dp),
        ) {
            Text(
                course.name,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (course.location.isNotBlank()) {
                Text(
                    course.location,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${course.startPeriod}-${course.endPeriod}节",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 9.sp,
                maxLines = 1,
            )
        }
    }
}
