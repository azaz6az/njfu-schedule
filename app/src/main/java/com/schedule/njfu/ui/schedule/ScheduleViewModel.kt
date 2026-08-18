package com.schedule.njfu.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.schedule.njfu.data.AppDatabase
import com.schedule.njfu.data.ScheduleRepository
import com.schedule.njfu.data.SettingsKeys
import com.schedule.njfu.data.semesterStart
import com.schedule.njfu.model.Course
import com.schedule.njfu.model.HolidayUtils
import com.schedule.njfu.model.WeekUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class ScheduleViewModel(private val db: AppDatabase) : ViewModel() {

    private val repo = ScheduleRepository(db)

    val courses: StateFlow<List<Course>> = repo.courses
        .map { list -> list.map { it.toModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentWeek = MutableStateFlow(0)
    val selectedWeek = MutableStateFlow(0)
    /** 初始为 null，避免异步加载完成前闪现错误的周差；加载完成后为学期起始日(周一) */
    val semesterStart = MutableStateFlow<LocalDate?>(null)
    /** 调休映射：日期 → 按周几显示（设置页维护） */
    val shifts = MutableStateFlow<Map<LocalDate, Int>>(emptyMap())
    /**
     * 课表每节行高（dp），设置页可调。
     * 用 Room 的 observeAll 流跟随设置变化，切回课表页时无需重新创建 ViewModel 即生效。
     */
    val rowHeight: StateFlow<Int> = db.settingsDao().observeAll()
        .map { rows ->
            rows.firstOrNull { it.key == SettingsKeys.ROW_HEIGHT }?.value?.toIntOrNull()
                ?: SettingsKeys.DEFAULT_ROW_HEIGHT
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsKeys.DEFAULT_ROW_HEIGHT)

    fun initIfNeeded() {
        viewModelScope.launch {
            refreshDayState()
        }
    }

    /**
     * 重读学期起始日并重算 currentWeek/today 相关状态。
     * 定时刷新（跨零点/跨周一）时调用，保证「本周」高亮与 isCurrentWeek 不失效。
     * 保持与现有状态结构兼容：只更新 semesterStart/currentWeek/shifts，selectedWeek 已确定时不动。
     */
    suspend fun refreshDayState() {
        val start = db.settingsDao().semesterStart()
        semesterStart.value = start
        shifts.value = HolidayUtils.parseShifts(db.settingsDao().get(SettingsKeys.HOLIDAY_SHIFTS))
        val week = WeekUtils.currentWeek(start, LocalDate.now())
        currentWeek.value = week
        // selectedWeek 只在首次初始化时从当前周落下，避免用户手动浏览周次时被刷新打断
        if (selectedWeek.value == 0) {
            selectedWeek.value = week
        }
    }

    fun selectWeek(w: Int) { selectedWeek.value = w.coerceAtLeast(1) }

    fun addCourse(course: Course) = viewModelScope.launch { repo.addCourse(course) }
    fun deleteCourse(id: Long) = viewModelScope.launch { repo.deleteCourse(id) }

    /** 长按拖拽换课：newDay/newStart 来自落点，内部再做一次 clamp 兜底后落库（weeks 等字段不变） */
    fun moveCourse(course: Course, newDay: Int, newStart: Int) = viewModelScope.launch {
        repo.updateCourse(computeMovedCourse(course, newDay, newStart))
    }

    class Factory(private val db: AppDatabase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ScheduleViewModel(db) as T
    }
}

/**
 * 计算「移动后的课程」（moveCourse 的 clamp 纯逻辑，内部函数便于单测）：
 * dayOfWeek clamp 到 1..7；newStart clamp 到 1..(MAX_PERIODS - rowSpan + 1)，
 * 保证 endPeriod = newStart + 原 rowSpan - 1 不超 12（且 end >= start）；
 * weeks 及其余字段一概不变。
 */
internal fun computeMovedCourse(course: Course, newDay: Int, newStart: Int): Course {
    val day = newDay.coerceIn(1, 7)
    val rowSpan = (course.endPeriod - course.startPeriod + 1).coerceIn(1, WeekGrid.MAX_PERIODS)
    val start = newStart.coerceIn(1, WeekGrid.MAX_PERIODS - rowSpan + 1)
    val end = (start + rowSpan - 1).coerceIn(1, WeekGrid.MAX_PERIODS)
    return course.copy(dayOfWeek = day, startPeriod = start, endPeriod = end)
}
