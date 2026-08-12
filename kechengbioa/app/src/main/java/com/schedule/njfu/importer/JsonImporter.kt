package com.schedule.njfu.importer

import com.schedule.njfu.model.Course
import com.schedule.njfu.model.Exam
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class BackupFile(
    val version: Int = 1,
    val courses: List<Course> = emptyList(),
    val exams: List<Exam> = emptyList(),
)

object JsonImporter {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun export(courses: List<Course>, exams: List<Exam> = emptyList()): String =
        json.encodeToString(BackupFile.serializer(), BackupFile(courses = courses, exams = exams))

    /**
     * 兼容两种输入格式：完整备份对象（[export] 产出），或旧版裸课程数组（如 "[]"）。
     */
    fun import(text: String): List<Course> {
        val trimmed = text.trim()
        return if (trimmed.startsWith("[")) {
            json.decodeFromString(ListSerializer(Course.serializer()), trimmed)
        } else {
            json.decodeFromString(BackupFile.serializer(), trimmed).courses
        }
    }

    fun importWithExams(text: String): BackupFile =
        json.decodeFromString(BackupFile.serializer(), text)
}
