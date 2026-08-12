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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils
import java.time.LocalDate

private val DayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
private val GutterWidth = 30.dp
private val HeaderHeight = 34.dp
private val RowHeight = 48.dp
private val CourseGap = 3.dp
private val CellEven = 0xFFF5F6F9.toInt()
private val CellOdd = 0xFFFFFFFF.toInt()
private val TodayShade = 0xFFE8EFFF.toInt()

@Composable
fun ScheduleScreen(viewModel: ScheduleViewModel) {
    val courses by viewModel.courses.collectAsStateWithLifecycle()
    val selectedWeek by viewModel.selectedWeek.collectAsStateWithLifecycle()
    val currentWeek by viewModel.currentWeek.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<DialogState?>(null) }

    LaunchedEffect(Unit) { viewModel.initIfNeeded() }

    val cells = remember(selectedWeek, courses) {
        WeekGrid.cellsFor(courses, selectedWeek)
    }
    val todayDay = remember { LocalDate.now().dayOfWeek.value }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        WeekSwitcher(
            selectedWeek = selectedWeek,
            currentWeek = currentWeek,
            onPrev = { viewModel.selectWeek(selectedWeek - 1) },
            onNext = { viewModel.selectWeek(selectedWeek + 1) },
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
                    "暂无课程，点下方 ＋ 加课",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 120.dp),
                )
            }
        }
        Button(
            onClick = { dialog = DialogState(null, 1, selectedWeek) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text("＋ 加课")
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
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPrev, enabled = selectedWeek > 1) { Text("◀") }
        Text(
            if (currentWeek > 0) "第 $selectedWeek 周（当前：第 $currentWeek 周）"
            else "第 $selectedWeek 周",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        TextButton(onClick = onNext, enabled = selectedWeek < WeekUtils.MAX_WEEKS) { Text("▶") }
    }
}

@Composable
private fun WeekGridContent(
    cells: List<WeekGrid.Cell>,
    todayDay: Int,
    onCellClick: (Int) -> Unit,
    onCourseClick: (Course) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
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
                            else MaterialTheme.colorScheme.onSurface,
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
                // 网格：背景骨架 + 课程卡片叠加
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    Column {
                        repeat(WeekGrid.MAX_PERIODS) { r ->
                            Row(Modifier
                                .fillMaxWidth()
                                .height(RowHeight)
                            ) {
                                repeat(7) { c ->
                                    Box(
                                        Modifier
                                            .width(colWidth)
                                            .fillMaxHeight()
                                            .background(if (c + 1 == todayDay) Color(TodayShade)
                                                else if ((r + c) % 2 == 0) Color(CellEven) else Color(CellOdd))
                                            .clickable { onCellClick(c + 1) },
                                    )
                                }
                            }
                        }
                    }
                    cells.forEach { cell ->
                        CourseCard(
                            course = cell.course,
                            modifier = Modifier
                                .offset(
                                    x = colWidth * cell.col,
                                    y = RowHeight * cell.row,
                                )
                                .width(colWidth - CourseGap)
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
    Column(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(course.color))
            .padding(horizontal = 4.dp, vertical = 3.dp),
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
