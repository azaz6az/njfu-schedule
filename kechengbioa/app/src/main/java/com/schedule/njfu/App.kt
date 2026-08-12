package com.schedule.njfu

import android.app.Application
import com.schedule.njfu.widget.WidgetRefreshWorker

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        WidgetRefreshWorker.schedule(this)
    }
}
