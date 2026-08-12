package com.schedule.njfu.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.schedule.njfu.data.AppDatabase
import com.schedule.njfu.data.ScheduleRepository
import com.schedule.njfu.data.semesterStart
import com.schedule.njfu.model.Course
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

    fun initIfNeeded() {
        if (selectedWeek.value == 0) {
            viewModelScope.launch {
                val start = db.settingsDao().semesterStart()
                val week = WeekUtils.currentWeek(start, LocalDate.now())
                currentWeek.value = week
                selectedWeek.value = week
            }
        }
    }

    fun selectWeek(w: Int) { selectedWeek.value = w.coerceAtLeast(1) }

    fun addCourse(course: Course) = viewModelScope.launch { repo.addCourse(course) }
    fun deleteCourse(id: Long) = viewModelScope.launch { repo.deleteCourse(id) }

    class Factory(private val db: AppDatabase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ScheduleViewModel(db) as T
    }
}
