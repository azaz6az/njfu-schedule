package com.schedule.njfu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.schedule.njfu.data.AppDatabase
import com.schedule.njfu.ui.navigation.AppNav
import com.schedule.njfu.ui.theme.ScheduleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScheduleTheme {
                AppNav(AppDatabase.get(this))
            }
        }
    }
}
