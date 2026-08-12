package com.schedule.njfu.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings") fun observeAll(): Flow<List<SettingsEntity>>
    @Query("SELECT value FROM settings WHERE `key` = :key") suspend fun get(key: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(entity: SettingsEntity)
}
