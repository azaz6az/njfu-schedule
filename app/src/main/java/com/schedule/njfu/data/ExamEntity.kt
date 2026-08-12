package com.schedule.njfu.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.schedule.njfu.model.Exam

@Entity(tableName = "exams")
data class ExamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val date: String,            // ISO yyyy-MM-dd
    val location: String,
    val note: String,
) {
    fun toModel() = Exam(id, name, date, location, note)
}

fun Exam.toEntity() = ExamEntity(id, name, date, location, note)
