package com.schedule.njfu.ui.schedule

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.schedule.njfu.data.AppDatabase
import com.schedule.njfu.data.toEntity
import com.schedule.njfu.model.Exam
import com.schedule.njfu.reminder.ExamReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExamViewModel(private val db: AppDatabase, private val context: Context) : ViewModel() {
    val exams: StateFlow<List<Exam>> = db.examDao().observeAll()
        .map { list -> list.map { it.toModel() }.sortedBy { it.date } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addExam(exam: Exam) = viewModelScope.launch {
        db.examDao().upsert(exam.toEntity())
        ExamReminderScheduler.rescheduleExams(context)
    }

    fun deleteExam(id: Long) = viewModelScope.launch {
        db.examDao().deleteById(id)
        ExamReminderScheduler.rescheduleExams(context)
    }

    class Factory(private val db: AppDatabase, private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ExamViewModel(db, context) as T
    }
}
