package com.schedule.njfu

import android.app.Application
import com.schedule.njfu.reminder.ReminderScheduler
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
        WidgetRefreshWorker.schedule(this)
        applicationScope.launch { ReminderScheduler.rescheduleToday(this@App) }
    }
}
