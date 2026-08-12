package com.schedule.njfu.importer

import com.schedule.njfu.model.Course
import com.schedule.njfu.model.Exam

data class Credentials(val username: String, val password: String)

sealed class ImportResult {
    data class Success(val courseCount: Int, val examCount: Int) : ImportResult()
    data class Failure(val reason: String, val retryable: Boolean = false) : ImportResult()
}

interface SchoolAdapter {
    suspend fun login(credentials: Credentials): Result<Unit>
    suspend fun fetchSchedule(): Result<List<Course>>
    suspend fun fetchExams(): Result<List<Exam>>
}
