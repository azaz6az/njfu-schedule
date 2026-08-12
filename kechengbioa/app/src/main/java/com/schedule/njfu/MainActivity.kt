package com.schedule.njfu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.schedule.njfu.data.AppDatabase
import com.schedule.njfu.ui.schedule.ScheduleScreen
import com.schedule.njfu.ui.schedule.ScheduleViewModel
import com.schedule.njfu.ui.theme.ScheduleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScheduleTheme {
                val vm: ScheduleViewModel = viewModel(
                    factory = ScheduleViewModel.Factory(AppDatabase.get(this))
                )
                ScheduleScreen(vm)
            }
        }
    }
}
