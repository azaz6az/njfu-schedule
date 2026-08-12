package com.schedule.njfu.data

import com.schedule.njfu.model.Course
import com.schedule.njfu.model.Exam

class ScheduleRepository(
    private val db: AppDatabase,
) {
    val courses = db.courseDao().observeAll()

    suspend fun replaceAll(courses: List<Course>, exams: List<Exam> = emptyList()) {
        db.courseDao().clear()
        db.courseDao().upsertAll(courses.map { it.toEntity() })
        if (exams.isNotEmpty()) {
            db.examDao().clear()
            db.examDao().upsertAll(exams.map { it.toEntity() })
        }
    }

    suspend fun addCourse(course: Course) = db.courseDao().upsert(course.toEntity())
    suspend fun deleteCourse(id: Long) = db.courseDao().deleteById(id)

    companion object {
        /** 自动导入去重：name+day+startPeriod 相同视为重复 */
        fun merge(auto: List<Course>, manual: List<Course>): List<Course> {
            val seen = hashSetOf<Triple<String, Int, Int>>()
            val result = mutableListOf<Course>()
            (manual + auto).forEach { c ->
                val key = Triple(c.name, c.dayOfWeek, c.startPeriod)
                if (seen.add(key)) result += c
            }
            return result
        }
    }
}
