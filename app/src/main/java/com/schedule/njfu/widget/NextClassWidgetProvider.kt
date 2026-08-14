package com.schedule.njfu.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.schedule.njfu.MainActivity
import com.schedule.njfu.R
import com.schedule.njfu.data.AppDatabase
import com.schedule.njfu.data.semesterStart
import com.schedule.njfu.data.widgetTheme
import com.schedule.njfu.model.WeekUtils
import com.schedule.njfu.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 4x1 下一节课小组件（标准 RemoteViews）。
 * 倒计时状态机见 [WidgetData.nextClassState]；主题色见 [WidgetTheme]。
 * 参与分钟级自适应刷新（[WidgetRefreshScheduler]）。
 */
class NextClassWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ids.forEach { id -> updateWidget(context, manager, id) }
                WidgetRefreshScheduler.ensureScheduled(context)
            } catch (t: Throwable) {
                DebugLog.write(context, "NextClassWidget render FAILED", t)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        WidgetRefreshScheduler.ensureScheduled(context)
    }

    override fun onDisabled(context: Context) {
        WidgetRefreshScheduler.cancelAll(context)
    }

    private suspend fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
        manager.updateAppWidget(appWidgetId, buildViews(context))
    }

    private suspend fun buildViews(context: Context): RemoteViews {
        val db = AppDatabase.get(context)
        val start = db.settingsDao().semesterStart()
        val week = WeekUtils.currentWeek(start, LocalDate.now())
        val themeKey = db.settingsDao().widgetTheme()
        val isNight = isNightMode(context)
        val palette = WidgetTheme.paletteFor(themeKey, isNight)
        val courses = db.courseDao().getAll().map { it.toModel() }
        val periodRaw = db.settingsDao().get(com.schedule.njfu.data.SettingsKeys.PERIOD_TIMES)
        val state = WidgetData.nextClassState(
            courses, week, LocalDateTime.now(), WidgetData.parsePeriodTimesJson(periodRaw),
        )

        val views = RemoteViews(context.packageName, R.layout.widget_next)
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        // 主题
        views.setInt(R.id.widget_root, "setBackgroundResource", WidgetTheme.bgRes(themeKey, isNight))
        views.setTextColor(R.id.next_subtitle, palette.textSecondary)
        views.setTextColor(R.id.next_course_line, palette.textSecondary)

        views.setTextViewText(R.id.next_subtitle, "下一节课 · 第${state.week}周 周${state.today}")

        when (state.phase) {
            WidgetData.NextClassPhase.BEFORE -> {
                views.setTextViewText(R.id.next_big, "距上课 ${state.minutes} 分钟")
                views.setTextColor(R.id.next_big, palette.accent)
            }
            WidgetData.NextClassPhase.IN_PROGRESS -> {
                views.setTextViewText(R.id.next_big, "上课中 · 距下课 ${state.minutes} 分钟")
                views.setTextColor(R.id.next_big, palette.textPrimary)
            }
            WidgetData.NextClassPhase.AFTER -> {
                views.setTextViewText(R.id.next_big, "今天课已上完")
                views.setTextColor(R.id.next_big, palette.textPrimary)
            }
            WidgetData.NextClassPhase.NO_CLASS_TODAY -> {
                views.setTextViewText(R.id.next_big, "今日无课")
                views.setTextColor(R.id.next_big, palette.textPrimary)
            }
        }

        val course = state.course
        if (course == null) {
            views.setViewVisibility(R.id.next_course_line, View.GONE)
        } else {
            views.setViewVisibility(R.id.next_course_line, View.VISIBLE)
            views.setTextViewText(
                R.id.next_course_line,
                if (course.location.isBlank()) course.name
                else "${course.name} · ${course.location}",
            )
        }
        return views
    }

    /** 跟随系统深色模式 */
    private fun isNightMode(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode
        return (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    companion object {
        suspend fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, NextClassWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            val provider = NextClassWidgetProvider()
            ids.forEach { id -> provider.updateWidget(context, manager, id) }
            WidgetRefreshScheduler.ensureScheduled(context)
        }
    }
}
