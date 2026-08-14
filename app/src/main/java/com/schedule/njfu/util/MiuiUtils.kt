package com.schedule.njfu.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * 小米/MIUI 适配：MIUI 的省电策略与后台限制会延迟 AlarmManager 提醒与
 * WorkManager 小组件刷新，需要引导用户授予「自启动」与「无限制省电」。
 */
object MiuiUtils {

    /** 是否 MIUI 系统（小米/红米设备） */
    fun isMiui(): Boolean {
        val manufacturer = Build.MANUFACTURER?.lowercase() ?: return false
        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi")) return true
        // 备用探测：系统属性 ro.miui.ui.version.name（部分定制 ROM）
        return runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val get = clazz.getMethod("get", String::class.java)
            !get.invoke(null, "ro.miui.ui.version.name").toString().isNullOrBlank()
        }.getOrDefault(false)
    }

    /** MIUI 自启动管理页（允许后台启动才能准时收到提醒/刷新小组件） */
    fun autostartSettingsIntent(): Intent {
        return Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** MIUI 电池/省电策略设置页 */
    fun batterySettingsIntent(): Intent {
        return runCatching {
            Intent().apply {
                component = ComponentName(
                    "com.miui.powercenter",
                    "com.miui.powercenter.PowerSettings",
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }.getOrNull() ?: Intent(Settings.ACTION_SETTINGS)
    }

    /** 通用应用详情设置（跳转失败时的兜底） */
    fun appDetailsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.parse("package:${context.packageName}"),
        )
}
