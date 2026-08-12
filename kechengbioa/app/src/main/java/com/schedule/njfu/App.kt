package com.schedule.njfu

import android.app.Application
import com.schedule.njfu.reminder.ReminderScheduler
import com.schedule.njfu.widget.WidgetRefreshWorker
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        WidgetRefreshWorker.schedule(this)
        GlobalScope.launch { ReminderScheduler.rescheduleToday(this@App) }
    }
}
