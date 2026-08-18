package com.schedule.njfu.ui.schedule

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import com.schedule.njfu.share.shareCurrentWeekImage
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.schedule.njfu.R
import com.schedule.njfu.data.CourseMapper
import com.schedule.njfu.model.Course
import com.schedule.njfu.model.HolidayUtils
import com.schedule.njfu.model.WeekUtils
import com.schedule.njfu.ui.weekdayName
import java.time.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val GutterWidth = 30.dp
private val HeaderHeight = 34.dp
private val CourseGap = 3.dp
/** 课程卡片左侧白色点缀条宽度 */
private val CourseBarWidth = 3.dp
/** 课程卡片文字区水平内边距（左右各 5dp） */
private val CourseTextPaddingH = 10.dp
/** 课程卡片文字区垂直内边距（上下各 3dp） */
private val CourseTextPaddingV = 6.dp

/**
 * 长按拖拽「未动作」判定阈值（px）：拖拽累计位移小于该值视为长按后未移动，
 * 松手直接回弹、不弹确认框（处理“长按不动”场景，避免因卡片中心参照导致误弹窗）。
 */
private const val DragNoMoveThresholdPx = 8f

/** 拖拽落点计算结果：目标列（0 基，网格列而非自然星期）与新节次区间 */
internal data class DragDropTarget(
    val col: Int,
    val newStart: Int,
    val endPeriod: Int,
)

/**
 * 拖拽落点纯函数：由拖拽累计位移计算目标列/行，并推出新开始节与结束节（已 clamp）。
 *
 * @param dragOffset 拖拽累计位移（px，相对卡片原始位置；与 Modifier.offset 施加的位移一致）
 * @param cardStartX/cardStartY 卡片原始左上角在网格坐标系中的位置（px）
 * @param cardWidth/cardHeight 卡片绘制尺寸（px）——落点以「卡片中心」为参照，
 *        这样卡片被拖到哪儿，落点就是哪儿，跟手且符合直觉
 * @param colWidth/rowHeight 每列宽 / 每行高（px）
 * @param originalStart/originalEnd 课程原开始/结束节（决定 rowSpan）
 * @return 有效落点（col clamp 0..6、newStart 使 endPeriod = newStart + rowSpan - 1 不超 12 且 end >= start）；
 *         卡片中心拖出网格区域（x<0 / y<0 / x≥7*colWidth / y≥12*rowHeight）时返回 null 表示无效落点。
 *
 * 注意：这里不输出 newDay——列到星期的转换依赖调休映射数据（weekDates/shifts），
 * 由调用方用 [WeekGrid.mappedDayForColumn] 完成（放假日列会映射为 0，调用方视为无效落点）。
 */
internal fun computeDragDropTarget(
    dragOffset: Offset,
    cardStartX: Float,
    cardStartY: Float,
    cardWidth: Float,
    cardHeight: Float,
    colWidth: Float,
    rowHeight: Float,
    originalStart: Int,
    originalEnd: Int,
): DragDropTarget? {
    val gridWidth = 7 * colWidth
    val gridHeight = WeekGrid.MAX_PERIODS * rowHeight
    val centerX = cardStartX + dragOffset.x + cardWidth / 2f
    val centerY = cardStartY + dragOffset.y + cardHeight / 2f
    // 卡片中心必须在网格区域内（右/下边缘为开区间，恰好贴边视为越界）
    if (centerX < 0f || centerY < 0f || centerX >= gridWidth || centerY >= gridHeight) return null
    val col = floor(centerX / colWidth).toInt().coerceIn(0, 6)
    val row = floor(centerY / rowHeight).toInt().coerceIn(0, WeekGrid.MAX_PERIODS - 1)
    val rowSpan = (originalEnd - originalStart + 1).coerceIn(1, WeekGrid.MAX_PERIODS)
    // 保证 end = newStart + rowSpan - 1 ≤ 12：开始节上限为 12 - rowSpan + 1（rowSpan ≤ 12 时上限 ≥ 1）
    val start = (row + 1).coerceIn(1, WeekGrid.MAX_PERIODS - rowSpan + 1)
    val end = start + rowSpan - 1
    return DragDropTarget(col, start, end)
}

/** Course 序列化（@Serializable），用于旋转时跨 recomposition 保存弹窗状态 */
private val CourseJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

/** [DialogState] 的 rememberSaveable Saver：course 以 JSON 字符串保存（null 存空串），weekDay/weekNumber 原样保存 */
private val DialogStateSaver = listSaver<DialogState?, Any>(
    save = { d ->
        if (d == null) {
            // null 表示当前无弹窗：不保存任何数据，重建后回落到初始值 null
            emptyList()
        } else {
            listOf(
                d.course?.let { CourseJson.encodeToString(it) } ?: "",
                d.weekDay,
                d.weekNumber,
            )
        }
    },
    restore = { l ->
        val courseJson = l.getOrNull(0) as? String ?: ""
        DialogState(
            course = courseJson
                .takeIf { it.isNotEmpty() }
                ?.let { runCatching { CourseJson.decodeFromString<Course>(it) }.getOrNull() },
            weekDay = (l.getOrNull(1) as? Int) ?: 1,
            weekNumber = (l.getOrNull(2) as? Int) ?: 1,
        )
    },
)

/** [Course]（可为 null）的 rememberSaveable Saver */
private val CourseSaver = listSaver<Course?, Any>(
    save = { c -> if (c == null) emptyList() else listOf(CourseJson.encodeToString(c)) },
    restore = { l ->
        (l.getOrNull(0) as? String)
            ?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { CourseJson.decodeFromString<Course>(it) }.getOrNull() }
    },
)

@Composable
fun ScheduleScreen(viewModel: ScheduleViewModel) {
    val courses by viewModel.courses.collectAsStateWithLifecycle()
    val selectedWeek by viewModel.selectedWeek.collectAsStateWithLifecycle()
    val currentWeek by viewModel.currentWeek.collectAsStateWithLifecycle()
    val semesterStart by viewModel.semesterStart.collectAsStateWithLifecycle()
    val shifts by viewModel.shifts.collectAsStateWithLifecycle()
    // 每节行高（dp），设置页可调；响应式跟随设置变化
    val rowHeight by viewModel.rowHeight.collectAsStateWithLifecycle()
    var dialog by rememberSaveable(stateSaver = DialogStateSaver) { mutableStateOf<DialogState?>(null) }
    var pendingAdd by rememberSaveable(stateSaver = CourseSaver) { mutableStateOf<Course?>(null) }
    // 分享成图：context 用于分享面板与失败提示；scope 承载 IO 渲染协程
    val context = LocalContext.current
    val shareScope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.initIfNeeded() }

    // 本周（selectedWeek）七天日期：调休映射依赖它；学期起始日未加载完成时为 null
    val weekDates = remember(selectedWeek, semesterStart) {
        semesterStart?.let { start ->
            val monday = start.plusWeeks((selectedWeek - 1).toLong())
            (0..6).map { monday.plusDays(it.toLong()) }
        }
    }
    val cells = remember(selectedWeek, courses, weekDates, shifts) {
        if (weekDates == null) emptyList()
        else WeekGrid.cellsFor(courses, selectedWeek, weekDates, shifts)
    }
    // 今日日期：每分钟刷新一次；检测到跨天（日期变化）时重读学期起始日并重算当前周，
    // 避免停留在课表页跨过周一零点后「本周」高亮与 isCurrentWeek 失效。
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            val now = LocalDate.now()
            if (now != today) {
                today = now
                viewModel.refreshDayState()
            }
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
            if (semesterStart == null) {
                // 首帧加载中：不渲染周格，避免以错误的日期区间闪现
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }
            } else {
                WeekSwitcher(
                    selectedWeek = selectedWeek,
                    currentWeek = currentWeek,
                    semesterStart = semesterStart,
                    weekDates = weekDates ?: emptyList(),
                    shifts = shifts,
                    onPrev = { viewModel.selectWeek(selectedWeek - 1) },
                    onNext = { viewModel.selectWeek(selectedWeek + 1) },
                    onBackToCurrent = { viewModel.selectWeek(currentWeek) },
                    onShare = {
                        val start = semesterStart
                        if (start != null && courses.isNotEmpty()) {
                            // 分享当前周课表为图片；失败提示切回主线程 Toast
                            shareScope.launch {
                                shareCurrentWeekImage(
                                    context = context,
                                    courses = courses,
                                    week = selectedWeek,
                                    semesterStart = start,
                                    rowHeightDp = rowHeight,
                                    shifts = shifts,
                                    onError = { msg ->
                                        // onError 可能在 IO 线程回调：经主线程协程弹 Toast
                                        shareScope.launch {
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                )
                            }
                        }
                    },
                )
                Box(Modifier.fillMaxWidth()) {
                    WeekGridContent(
                        cells = cells,
                        rowHeight = rowHeight.dp,
                        todayDay = highlightDay,
                        highlightToday = isCurrentWeek && highlightDay > 0,
                        weekDates = weekDates ?: emptyList(),
                        shifts = shifts,
                        courses = courses,
                        onMoveCourse = { c, day, start -> viewModel.moveCourse(c, day, start) },
                        onCellClick = { day ->
                            // 点击列加课：星期用该列日期映射后的星期（调休日按映射）
                            dialog = DialogState(
                                null,
                                WeekGrid.mappedDayForColumn(day - 1, weekDates ?: emptyList(), shifts),
                                selectedWeek,
                            )
                        },
                        onCourseClick = { c -> dialog = DialogState(c, c.dayOfWeek, selectedWeek) },
                    )
                    if (courses.isEmpty()) {
                        Text(
                            stringResource(R.string.schedule_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(top = 120.dp),
                        )
                    }
                }
            }
            // 底部留白，避免 FAB 遮挡网格底部
            Spacer(Modifier.height(96.dp))
        }
        ExtendedFloatingActionButton(
            onClick = {
                // 默认落到「今天所在列」映射的星期（今天放假时用自然星期）
                val fabDay = WeekGrid.mappedDayForColumn(todayDay - 1, weekDates ?: emptyList(), shifts)
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
            Text(stringResource(R.string.schedule_add_course))
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
            title = { Text(stringResource(R.string.schedule_conflict_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.schedule_conflict_message, c.name))
                    Spacer(Modifier.height(8.dp))
                    conflicts.forEach { other ->
                        Text(
                            stringResource(
                                R.string.schedule_conflict_item,
                                other.name,
                                weekdayName(other.dayOfWeek),
                                other.startPeriod,
                                other.endPeriod,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.schedule_conflict_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addCourse(c)
                    pendingAdd = null
                }) { Text(stringResource(R.string.schedule_conflict_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingAdd = null }) { Text(stringResource(R.string.schedule_conflict_cancel)) }
            },
        )
    }
}

private data class DialogState(
    val course: Course?,
    val weekDay: Int,
    val weekNumber: Int,
)

/** 长按拖拽换课的待确认移动：course 为原课程，newDay/newStart/endPeriod 为落点计算结果 */
private data class PendingMove(
    val course: Course,
    val newDay: Int,
    val newStart: Int,
    val endPeriod: Int,
)

@Composable
private fun WeekSwitcher(
    selectedWeek: Int,
    currentWeek: Int,
    semesterStart: LocalDate?,
    weekDates: List<LocalDate>,
    shifts: Map<LocalDate, Int>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onBackToCurrent: () -> Unit,
    onShare: () -> Unit,
) {
    // 日期区间：学期起始日未加载完成（null）时兜底不显示
    val rangeText = semesterStart?.let { start ->
        val monday = start.plusWeeks((selectedWeek - 1).toLong())
        val sunday = monday.plusDays(6)
        if (monday.year == sunday.year) {
            stringResource(
                R.string.schedule_week_range_same_year,
                monday.monthValue,
                monday.dayOfMonth,
                sunday.monthValue,
                sunday.dayOfMonth,
            )
        } else {
            stringResource(
                R.string.schedule_week_range_cross_year,
                monday.year,
                monday.monthValue,
                monday.dayOfMonth,
                sunday.year,
                sunday.monthValue,
                sunday.dayOfMonth,
            )
        }
    } ?: ""
    // 本周调休提示：如「周六按周一」
    val shiftHints = weekDates.mapNotNull { d ->
        val target = shifts[d]
        if (target != null) stringResource(
            R.string.holiday_shift_line,
            d.monthValue,
            d.dayOfMonth,
            weekdayName(target),
        ) else null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.schedule_week_title, selectedWeek),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (rangeText.isNotEmpty()) {
                Text(
                    rangeText + if (shiftHints.isEmpty()) "" else " · " + shiftHints.joinToString("、"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (currentWeek > 0) {
            AssistChip(
                onClick = onBackToCurrent,
                enabled = selectedWeek != currentWeek,
                label = {
                    Text(
                        if (selectedWeek == currentWeek) stringResource(R.string.schedule_this_week)
                        else stringResource(R.string.schedule_back_to_current_week),
                    )
                },
            )
        }
        Spacer(Modifier.width(8.dp))
        RoundArrowButton(onClick = onPrev, enabled = selectedWeek > 1, isPrev = true)
        Spacer(Modifier.width(8.dp))
        RoundArrowButton(onClick = onNext, enabled = selectedWeek < WeekUtils.MAX_WEEKS, isPrev = false)
        Spacer(Modifier.width(4.dp))
        // 分享当前周课表成图（Agent C 的 share 模块，入口统一放顶栏）
        FilledIconButton(
            onClick = onShare,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(
                Icons.Filled.Share,
                contentDescription = stringResource(R.string.share_chooser_title),
            )
        }
    }
}

@Composable
private fun RoundArrowButton(onClick: () -> Unit, enabled: Boolean, isPrev: Boolean) {
    val icon = if (isPrev) Icons.AutoMirrored.Filled.KeyboardArrowLeft
    else Icons.AutoMirrored.Filled.KeyboardArrowRight
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        // 48dp 可点击区域（Material 触控目标规范）；图标 24dp 由 FilledIconButton 默认 contentPadding 居中
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.outline,
        ),
    ) {
        Icon(
            icon,
            contentDescription = if (isPrev) stringResource(R.string.schedule_prev_week)
            else stringResource(R.string.schedule_next_week),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WeekGridContent(
    cells: List<WeekGrid.Cell>,
    rowHeight: Dp,
    todayDay: Int,
    highlightToday: Boolean,
    onCellClick: (Int) -> Unit,
    onCourseClick: (Course) -> Unit,
    weekDates: List<LocalDate> = emptyList(),
    shifts: Map<LocalDate, Int> = emptyMap(),
    courses: List<Course> = emptyList(),
    onMoveCourse: (Course, Int, Int) -> Unit = { _, _, _ -> },
) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        val gridWidth = maxWidth - GutterWidth
        val colWidth = gridWidth / 7
        val density = LocalDensity.current
        val colW = with(density) { colWidth.toPx() }
        val rowH = with(density) { rowHeight.toPx() }
        val gapPx = with(density) { CourseGap.toPx() }
        // 拖拽换课状态（本网格局部）：
        //   moveConfirm —— 待用户确认的移动；moveConflict —— 移动后冲突确认；
        //   dragResetToken —— 每次确认/取消后自增，强制所有卡片清空拖拽位移（回弹/归位）。
        var dragResetToken by remember { mutableStateOf(0) }
        var moveConfirm by remember { mutableStateOf<PendingMove?>(null) }
        var moveConflict by remember { mutableStateOf<PendingMove?>(null) }

        fun resetMove() {
            moveConfirm = null
            moveConflict = null
            dragResetToken++
        }
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
                            stringResource(R.string.schedule_day_header, weekdayName(i + 1)),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (highlightToday && day == todayDay) FontWeight.Bold else FontWeight.Normal,
                            color = if (highlightToday && day == todayDay) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(Modifier.height(rowHeight * WeekGrid.MAX_PERIODS)) {
                // 节次行标签 1-10
                Column(Modifier.width(GutterWidth)) {
                    repeat(WeekGrid.MAX_PERIODS) { r ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(rowHeight),
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
                        // 拖拽换课：offset/尺寸保留在「容器 modifier」上——这是修复过坐标堆叠 bug 的
                        // 正确形态（offset 一旦进入子卡片内部，所有卡片会堆叠在网格原点）。
                        // 已移除 TooltipBox 包装：其长按 tooltip 检测与拖拽长按手势
                        // （detectDragGesturesAfterLongPress）会竞争抢同一段长按手势，按方案取舍直接
                        // 去掉 tooltip，视觉与单击行为（单击=编辑）不受影响。
                        val slotW = colW / cell.overlapCount
                        val cardWidthPx = slotW - gapPx
                        val cardHeightPx = rowH * cell.rowSpan - gapPx
                        val startXPx = colW * cell.col + slotW * cell.overlapIndex
                        val startYPx = rowH * cell.row
                        // 拖拽位移（px）：dragResetToken 变化时重建为 null → 回弹/归位
                        var dragBy by remember(cell.course.id, dragResetToken) { mutableStateOf<Offset?>(null) }
                        val dragging = dragBy != null

                        Box(
                            Modifier
                                .offset {
                                    val d = dragBy ?: Offset.Zero
                                    IntOffset(
                                        (startXPx + d.x).roundToInt(),
                                        (startYPx + d.y).roundToInt(),
                                    )
                                }
                                .width(slotWidth - CourseGap)
                                .height(rowHeight * cell.rowSpan - CourseGap)
                                .zIndex(if (dragging) 1f else 0f),
                        ) {
                            CourseCard(
                                course = cell.course,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { onCourseClick(cell.course) }
                                    .pointerInput(cell.course.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { dragBy = Offset.Zero },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragBy = (dragBy ?: Offset.Zero) + dragAmount
                                            },
                                            onDragEnd = {
                                                val offset = dragBy ?: return@detectDragGesturesAfterLongPress
                                                // 长按后几乎没有位移（未动作）：回弹，不弹确认框
                                                if (offset.getDistance() < DragNoMoveThresholdPx) {
                                                    dragBy = null
                                                    return@detectDragGesturesAfterLongPress
                                                }
                                                val target = computeDragDropTarget(
                                                    dragOffset = offset,
                                                    cardStartX = startXPx,
                                                    cardStartY = startYPx,
                                                    cardWidth = cardWidthPx,
                                                    cardHeight = cardHeightPx,
                                                    colWidth = colW,
                                                    rowHeight = rowH,
                                                    originalStart = cell.course.startPeriod,
                                                    originalEnd = cell.course.endPeriod,
                                                )
                                                if (target == null) {
                                                    // 拖出网格：回弹原位
                                                    dragBy = null
                                                    return@detectDragGesturesAfterLongPress
                                                }
                                                val newDay = WeekGrid.mappedDayForColumn(target.col, weekDates, shifts)
                                                if (newDay !in 1..7) {
                                                    // 落点是放假列（调休映射为 0）：无效落点，回弹
                                                    dragBy = null
                                                    return@detectDragGesturesAfterLongPress
                                                }
                                                if (newDay == cell.course.dayOfWeek &&
                                                    target.newStart == cell.course.startPeriod
                                                ) {
                                                    // 落回原格：回弹，不弹确认框
                                                    dragBy = null
                                                    return@detectDragGesturesAfterLongPress
                                                }
                                                // 有效落点：卡片停在落点，弹「移动到 周X N-M节？」确认框
                                                moveConfirm = PendingMove(
                                                    course = cell.course,
                                                    newDay = newDay,
                                                    newStart = target.newStart,
                                                    endPeriod = target.endPeriod,
                                                )
                                            },
                                            onDragCancel = { dragBy = null },
                                        )
                                    },
                            )
                        }
                    }
                }
            }
        }

        // —— 移动确认对话框：取消则回弹原位，确认则先查冲突（复用 pendingAdd 那套冲突确认流程的写法） ——
        moveConfirm?.let { m ->
            AlertDialog(
                onDismissRequest = { resetMove() },
                title = { Text(stringResource(R.string.drag_move_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.drag_move_message,
                            m.course.name,
                            weekdayName(m.newDay),
                            m.newStart,
                            m.endPeriod,
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val candidate = m.course.copy(
                            dayOfWeek = m.newDay,
                            startPeriod = m.newStart,
                            endPeriod = m.endPeriod,
                        )
                        if (WeekUtils.findConflicts(courses, candidate).isEmpty()) {
                            onMoveCourse(m.course, m.newDay, m.newStart)
                            resetMove()
                        } else {
                            // 有冲突：转入冲突确认对话框（卡片仍停在落点）
                            moveConfirm = null
                            moveConflict = m
                        }
                    }) { Text(stringResource(R.string.drag_move_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { resetMove() }) { Text(stringResource(R.string.drag_move_cancel)) }
                },
            )
        }

        // —— 移动后冲突确认对话框 ——
        moveConflict?.let { m ->
            val candidate = m.course.copy(
                dayOfWeek = m.newDay,
                startPeriod = m.newStart,
                endPeriod = m.endPeriod,
            )
            val conflicts = WeekUtils.findConflicts(courses, candidate)
            AlertDialog(
                onDismissRequest = { resetMove() },
                title = { Text(stringResource(R.string.drag_move_conflict_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.drag_move_conflict_message, m.course.name))
                        Spacer(Modifier.height(8.dp))
                        conflicts.forEach { other ->
                            Text(
                                stringResource(
                                    R.string.drag_move_conflict_item,
                                    other.name,
                                    weekdayName(other.dayOfWeek),
                                    other.startPeriod,
                                    other.endPeriod,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.drag_move_conflict_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        onMoveCourse(m.course, m.newDay, m.newStart)
                        resetMove()
                    }) { Text(stringResource(R.string.drag_move_conflict_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { resetMove() }) { Text(stringResource(R.string.drag_move_conflict_back)) }
                },
            )
        }
    }
}

@Composable
private fun CourseCard(course: Course, modifier: Modifier) {
    val rawColor = if (course.color == 0) CourseMapper.colorFor(course.name) else course.color
    val bg = Color(CourseMapper.displayColor(rawColor))
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg),
    ) {
        // 文字区域：扣除左侧白条与内边距（coerceAtLeast 兜底多门课并排的极窄卡片）
        val textWidth = (maxWidth - CourseBarWidth - CourseTextPaddingH).coerceAtLeast(4.dp)
        val textHeight = (maxHeight - CourseTextPaddingV).coerceAtLeast(4.dp)
        // 课程名排版：优先完整换行显示，空间不足降字号，小字行按需取舍；
        // 预算按 dp、文字按 sp 渲染，需用 fontScale 折算预算，避免系统字体放大时卡底被裁
        val fontScale = LocalDensity.current.fontScale
        val layout = remember(course.name, course.location, maxWidth, maxHeight, fontScale) {
            computeCourseNameLayout(
                name = course.name,
                locationPresent = course.location.isNotBlank(),
                textWidthDp = textWidth.value,
                textHeightDp = textHeight.value,
                fontScale = fontScale,
            )
        }
        Row(Modifier.fillMaxSize()) {
            // 左侧白色点缀条（纸感风格）
            Box(
                Modifier
                    .width(CourseBarWidth)
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
                    fontSize = layout.fontSizeSp.sp,
                    lineHeight = (layout.fontSizeSp * COURSE_NAME_LINE_FACTOR).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = layout.maxLines,
                    overflow = TextOverflow.Ellipsis,
                )
                if (layout.showLocation && course.location.isNotBlank()) {
                    Text(
                        course.location,
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 10.sp,
                        lineHeight = COURSE_META_LINE_HEIGHT_DP.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (layout.showPeriod) {
                    Text(
                        stringResource(R.string.schedule_period_line, course.startPeriod, course.endPeriod),
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 10.sp,
                        lineHeight = COURSE_META_LINE_HEIGHT_DP.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}