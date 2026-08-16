package com.schedule.njfu.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// 迁移策略：当前 version = 1，尚无任何 schema 迁移（MIGRATION_1_2 ...）。
// 未来任何 schema 变更都【必须先编写 Migration 并递增 version】，绝不能再靠
// fallbackToDestructiveMigration 兜底（它会清空用户的课程/考试数据）。
// （exportSchema 标志由构建代理统一开启，此处不开以免重复改动。）
@Database(entities = [CourseEntity::class, ExamEntity::class, SettingsEntity::class],
          version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun examDao(): ExamDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext,
                    AppDatabase::class.java, "schedule.db")
                    // 注意：该 destructive fallback 只是 version=1 阶段的临时手段，
                    // 见文件头注释——一旦引入第一个 MIGRATION 就必须移除它。
                    .fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
