package com.schedule.njfu.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
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
            // 统一入口判空：连一个小组件实例都没有时立刻返回，
            // 不刷新视图、也不产生任何后续一次性调度开销。
            // 各 Provider 的 refreshAll 内部虽各自早退，这里先短路可省去 5 次无谓的实例探测。
            if (!hasAnyWidgetInstance(context)) return

            ScheduleWidgetProvider.refreshAll(context)
            WeekWidgetProvider.refreshAll(context)
            NextClassWidgetProvider.refreshAll(context)
            TodayWidgetProvider.refreshAll(context)
            ExamCountdownWidgetProvider.refreshAll(context)
        }

        /** 是否有任一小组件实例被添加到桌面 */
        private fun hasAnyWidgetInstance(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            val providers = listOf(
                ScheduleWidgetProvider::class.java,
                WeekWidgetProvider::class.java,
                NextClassWidgetProvider::class.java,
                TodayWidgetProvider::class.java,
                ExamCountdownWidgetProvider::class.java,
            )
            return providers.any { provider ->
                manager.getAppWidgetIds(ComponentName(context, provider)).isNotEmpty()
            }
        }
    }
}
