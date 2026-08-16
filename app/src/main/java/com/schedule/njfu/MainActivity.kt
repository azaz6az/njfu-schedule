package com.schedule.njfu

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.schedule.njfu.data.AppDatabase
import com.schedule.njfu.ui.navigation.AppNav
import com.schedule.njfu.ui.theme.ScheduleTheme

class MainActivity : ComponentActivity() {

    /** Android 13+ 通知运行时权限：课前提醒依赖它，首次进入时请求 */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // 记录请求结果，供后续排查与设置页引导
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_NOTIFICATION_REQUESTED, true)
                .putBoolean(KEY_NOTIFICATION_GRANTED, granted)
                .apply()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        setContent {
            ScheduleTheme {
                AppNav(AppDatabase.get(this), initialTab = intent.getStringExtra("start_tab"))
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private companion object {
        const val PREFS_NAME = "prefs"
        const val KEY_NOTIFICATION_REQUESTED = "notification_permission_requested"
        const val KEY_NOTIFICATION_GRANTED = "notification_permission_granted"
    }
}
