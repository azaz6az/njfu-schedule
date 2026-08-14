package com.schedule.njfu.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        refreshNow(applicationContext)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "widget_refresh", ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** 立即刷新全部已添加的小组件（设置页手动触发 / 打开 App 时） */
        suspend fun refreshNow(context: Context) {
            ScheduleWidgetProvider.refreshAll(context)
            WeekWidgetProvider.refreshAll(context)
            NextClassWidgetProvider.refreshAll(context)
            TodayWidgetProvider.refreshAll(context)
            ExamCountdownWidgetProvider.refreshAll(context)
        }
    }
}
