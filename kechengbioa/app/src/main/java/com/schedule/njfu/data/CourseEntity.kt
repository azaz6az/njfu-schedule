package com.schedule.njfu.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.schedule.njfu.model.Course

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val teacher: String,
    val location: String,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: Int,
    val color: Int,
    val source: String,
    val note: String,
) {
    fun toModel() = Course(id, name, teacher, location, dayOfWeek, startPeriod, endPeriod,
        weeks, color, source, note)
}

fun Course.toEntity() = CourseEntity(id, name, teacher, location, dayOfWeek,
    startPeriod, endPeriod, weeks, color, source, note)
