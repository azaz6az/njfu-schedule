package com.schedule.njfu

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.schedule.njfu.reminder.ExamReminderScheduler
import com.schedule.njfu.reminder.ReminderScheduler
import com.schedule.njfu.util.DebugLog
import com.schedule.njfu.widget.WidgetRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {

    /** 应用级协程作用域，替代 GlobalScope，随进程存续 */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 崩溃兜底：记录未捕获异常到调试日志（含小部件渲染崩溃），保留默认终止行为
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            DebugLog.write(this, "UNCAUGHT on ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        DebugLog.write(
            this,
            "APP START v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}) " +
                "sdk=${Build.VERSION.SDK_INT} device=${Build.MANUFACTURER} ${Build.MODEL}",
        )
        WidgetRefreshWorker.schedule(this)
        createNotificationChannels()
        applicationScope.launch {
            ReminderScheduler.rescheduleToday(this@App)
            ExamReminderScheduler.rescheduleExams(this@App)
        }
    }

    /** 统一创建提醒渠道，避免各 Receiver 重复创建 */
    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        val schedule = NotificationChannel(
            "schedule",
            getString(R.string.notification_channel_schedule),
            NotificationManager.IMPORTANCE_HIGH,
        )
        val exam = NotificationChannel(
            "exams",
            getString(R.string.notification_channel_exams),
            NotificationManager.IMPORTANCE_HIGH,
        )
        nm.createNotificationChannel(schedule)
        nm.createNotificationChannel(exam)
    }
}
