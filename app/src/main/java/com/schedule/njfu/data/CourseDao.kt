package com.schedule.njfu.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY dayOfWeek, startPeriod")
    fun observeAll(): Flow<List<CourseEntity>>
    @Query("SELECT * FROM courses")
    suspend fun getAll(): List<CourseEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(course: CourseEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(courses: List<CourseEntity>)
    @Update suspend fun update(course: CourseEntity)
    @Query("DELETE FROM courses") suspend fun clear()
    @Query("DELETE FROM courses WHERE id = :id") suspend fun deleteById(id: Long)
}
