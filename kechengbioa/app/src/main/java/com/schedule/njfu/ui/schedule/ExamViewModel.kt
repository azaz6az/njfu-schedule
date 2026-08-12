package com.schedule.njfu.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.schedule.njfu.data.AppDatabase
import com.schedule.njfu.data.toEntity
import com.schedule.njfu.model.Exam
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExamViewModel(private val db: AppDatabase) : ViewModel() {
    val exams: StateFlow<List<Exam>> = db.examDao().observeAll()
        .map { list -> list.map { it.toModel() }.sortedBy { it.date } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addExam(exam: Exam) = viewModelScope.launch { db.examDao().upsert(exam.toEntity()) }

    class Factory(private val db: AppDatabase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ExamViewModel(db) as T
    }
}
