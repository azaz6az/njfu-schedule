package com.schedule.njfu.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams")
    fun observeAll(): Flow<List<ExamEntity>>
    @Query("SELECT * FROM exams")
    suspend fun getAll(): List<ExamEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(exam: ExamEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(exams: List<ExamEntity>)
    @Query("DELETE FROM exams") suspend fun clear()
    @Query("DELETE FROM exams WHERE id = :id") suspend fun deleteById(id: Long)
}
