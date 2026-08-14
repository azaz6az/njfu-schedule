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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.delay
import com.schedule.njfu.data.CourseMapper
import com.schedule.njfu.model.Course
import com.schedule.njfu.model.HolidayUtils
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
    val shifts by viewModel.shifts.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<DialogState?>(null) }
    var pendingAdd by remember { mutableStateOf<Course?>(null) }

    LaunchedEffect(Unit) { viewModel.initIfNeeded() }

    // 本周（selectedWeek）七天日期：调休映射依赖它
    val weekDates = remember(selectedWeek, semesterStart) {
        val monday = semesterStart.plusWeeks((selectedWeek - 1).toLong())
        (0..6).map { monday.plusDays(it.toLong()) }
    }
    val cells = remember(selectedWeek, courses, weekDates, shifts) {
        WeekGrid.cellsFor(courses, selectedWeek, weekDates, shifts)
    }
    // 今日日期：每分钟刷新一次，避免跨零点后高亮失效
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            today = LocalDate.now()
        }
    }
    val todayDay = today.dayOfWeek.value
    // 调休：今天被映射到其他星期时，高亮映射后的列（如周六按周一）；映射为 0（放假）不高亮
    val effectiveTodayDay = HolidayUtils.shiftedDayOfWeek(today, shifts)
    val isCurrentWeek = selectedWeek > 0 && selectedWeek == currentWeek
    val highlightDay = if (effectiveTodayDay in 1..7) effectiveTodayDay else 0

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
                weekDates = weekDates,
                shifts = shifts,
                onPrev = { viewModel.selectWeek(selectedWeek - 1) },
                onNext = { viewModel.selectWeek(selectedWeek + 1) },
                onBackToCurrent = { viewModel.selectWeek(currentWeek) },
            )
            Box(Modifier.fillMaxWidth()) {
                WeekGridContent(
                    cells = cells,
                    todayDay = highlightDay,
                    highlightToday = isCurrentWeek && highlightDay > 0,
                    onCellClick = { day ->
                        // 点击列加课：星期用该列日期映射后的星期（调休日按映射）
                        dialog = DialogState(
                            null,
                            WeekGrid.mappedDayForColumn(day - 1, weekDates, shifts),
                            selectedWeek,
                        )
                    },
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
            onClick = {
                // 默认落到「今天所在列」映射的星期（今天放假时用自然星期）
                val fabDay = WeekGrid.mappedDayForColumn(todayDay - 1, weekDates, shifts)
                    .let { if (it in 1..7) it else todayDay }
                dialog = DialogState(null, fabDay, selectedWeek)
            },
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
                // 冲突检测：与现有课程时间重叠时先确认再添加
                val conflicts = WeekUtils.findConflicts(courses, c)
                if (conflicts.isEmpty()) {
                    viewModel.addCourse(c)
                    dialog = null
                } else {
                    pendingAdd = c
                    dialog = null
                }
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
    pendingAdd?.let { c ->
        val conflicts = WeekUtils.findConflicts(courses, c)
        AlertDialog(
            onDismissRequest = { pendingAdd = null },
            title = { Text("课程时间冲突") },
            text = {
                Column {
                    Text("「${c.name}」与以下课程时间重叠（同一天同一时段有课）：")
                    Spacer(Modifier.height(8.dp))
                    conflicts.forEach { other ->
                        Text(
                            "• ${other.name}（周${DayLabels[other.dayOfWeek - 1]} " +
                                "${other.startPeriod}-${other.endPeriod}节）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "确认添加后两门课会在同一格并排显示。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addCourse(c)
                    pendingAdd = null
                }) { Text("仍然添加") }
            },
            dismissButton = {
                TextButton(onClick = { pendingAdd = null }) { Text("返回修改") }
            },
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
    weekDates: List<LocalDate>,
    shifts: Map<LocalDate, Int>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onBackToCurrent: () -> Unit,
) {
    val monday = semesterStart.plusWeeks((selectedWeek - 1).toLong())
    val sunday = monday.plusDays(6)
    val rangeText = if (monday.year == sunday.year) {
        "${monday.monthValue}月${monday.dayOfMonth}日 – ${sunday.monthValue}月${sunday.dayOfMonth}日"
    } else {
        "${monday.year}年${monday.monthValue}月${monday.dayOfMonth}日 – ${sunday.year}年${sunday.monthValue}月${sunday.dayOfMonth}日"
    }
    // 本周调休提示：如「周六按周一」
    val shiftHints = weekDates.mapNotNull { d ->
        val target = shifts[d]
        if (target != null) "${d.monthValue}月${d.dayOfMonth}日（${DayLabels[target - 1]}）按周${DayLabels[target - 1]}显示" else null
    }

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
                rangeText + if (shiftHints.isEmpty()) "" else " · " + shiftHints.joinToString("、"),
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
    highlightToday: Boolean,
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
                            fontWeight = if (highlightToday && day == todayDay) FontWeight.Bold else FontWeight.Normal,
                            color = if (highlightToday && day == todayDay) MaterialTheme.colorScheme.primary
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
                                                if (highlightToday && c + 1 == todayDay) {
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
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (course.location.isNotBlank()) {
                Text(
                    course.location,
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${course.startPeriod}-${course.endPeriod}节",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
    }
}