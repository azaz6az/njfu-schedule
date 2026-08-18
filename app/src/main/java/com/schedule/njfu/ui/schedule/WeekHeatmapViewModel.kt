package com.schedule.njfu.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.schedule.njfu.data.AppDatabase
import com.schedule.njfu.data.CourseMapper
import com.schedule.njfu.data.ScheduleRepository
import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 周次热力图的一行：一门课程横向按周分格。
 * @param id 课程 id（展开/收起状态记忆用）
 * @param color 与课表卡片一致的最终展示色（ARGB，已过 displayColor 兜底）
 * @param weeksMask 兜底后的周次掩码（weeks == 0 视为全学期，与 WeekGrid.cellsFor 语义一致）
 * @param weekFlags 每周是否有课的布尔数组，下标 i 对应第 i+1 周（1..MAX_WEEKS）
 * @param ranges 连续周区间（升序），用于行尾与展开详情的「周次跨度」小结
 */
internal data class HeatmapRow(
    val id: Long,
    val name: String,
    val color: Int,
    val weeksMask: Int,
    val weekFlags: BooleanArray,
    val ranges: List<IntRange>,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val location: String,
    val teacher: String,
) {
    /** 第 [week] 周（1..MAX_WEEKS）是否有课 */
    fun hasClass(week: Int): Boolean = WeekUtils.contains(weeksMask, week)
}

/** weeks 非法（== 0 缺失，或 < 0 负数脏数据）→ 整学期掩码，防止课程「该出现却不出现在热力图」 */
internal fun effectiveWeeksMask(course: Course): Int =
    if (course.weeks > 0) course.weeks else WeekUtils.maskFor(1, WeekUtils.MAX_WEEKS)

/** 展示色：与课表卡片一致 —— 颜色为 0 时按课名取色板色，再统一过 displayColor 兜底（防浅色白字不可读） */
internal fun heatmapColor(course: Course): Int =
    CourseMapper.displayColor(if (course.color == 0) CourseMapper.colorFor(course.name) else course.color)

/**
 * 由周次掩码解析连续周区间（升序）。
 * 例：1-5 周 + 7-11 周 → [1..5, 7..11]；空掩码 → 空列表。
 */
internal fun weekRanges(mask: Int): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var start = -1
    for (w in 1..WeekUtils.MAX_WEEKS) {
        if (WeekUtils.contains(mask, w)) {
            if (start < 0) start = w
        } else if (start >= 0) {
            ranges += start..(w - 1)
            start = -1
        }
    }
    if (start >= 0) ranges += start..WeekUtils.MAX_WEEKS
    return ranges
}

/** courses → 热力行数据（行 = 一门课程；顺序与输入一致，即库序按 day/startPeriod） */
internal fun buildHeatmapRows(courses: List<Course>): List<HeatmapRow> = courses.map { c ->
    val mask = effectiveWeeksMask(c)
    HeatmapRow(
        id = c.id,
        name = c.name,
        color = heatmapColor(c),
        weeksMask = mask,
        weekFlags = BooleanArray(WeekUtils.MAX_WEEKS) { WeekUtils.contains(mask, it + 1) },
        ranges = weekRanges(mask),
        dayOfWeek = c.dayOfWeek,
        startPeriod = c.startPeriod,
        endPeriod = c.endPeriod,
        location = c.location,
        teacher = c.teacher,
    )
}

/**
 * 「周次」页签 ViewModel：订阅课程表，映射为热力行数据。
 * 工厂模式与 ScheduleViewModel.Factory 一致，由 AppNav 在 NavHost 内以 db 构造。
 */
class WeekHeatmapViewModel(private val db: AppDatabase) : ViewModel() {

    private val repo = ScheduleRepository(db)

    /** 热力行数据：课程库变化时自动刷新（Screen 内 collectAsStateWithLifecycle 订阅） */
    internal val rows: StateFlow<List<HeatmapRow>> = repo.courses
        .map { list -> list.map { it.toModel() } }
        .map { buildHeatmapRows(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    class Factory(private val db: AppDatabase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WeekHeatmapViewModel(db) as T
    }
}