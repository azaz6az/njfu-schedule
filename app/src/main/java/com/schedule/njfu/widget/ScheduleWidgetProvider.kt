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
import com.schedule.njfu.data.CourseMapper
import com.schedule.njfu.data.semesterStart
import com.schedule.njfu.data.widgetTheme
import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils
import com.schedule.njfu.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 2x2 今日课程桌面小组件（标准 RemoteViews 实现）。
 * 说明：本项目此前用 Glance 渲染，在 Android 16 / 澎湃 OS 3.0 上触发
 * LeftCompositionCancellationException 导致「载入窗口小部件时出现问题」，
 * 故改为 Android 标准 RemoteViews，彻底规避兼容问题。
 */
class ScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
            } catch (t: Throwable) {
                DebugLog.write(context, "ScheduleWidget render FAILED", t)
            } finally {
                pending.finish()
            }
        }
    }

    /** 构建并推送单个实例的视图 */
    private suspend fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
        manager.updateAppWidget(appWidgetId, buildViews(context))
    }

    private suspend fun buildViews(context: Context): RemoteViews {
        val db = AppDatabase.get(context)
        val start = db.settingsDao().semesterStart()
        val week = WeekUtils.currentWeek(start, LocalDate.now())
        val themeKey = db.settingsDao().widgetTheme()
        val palette = WidgetTheme.paletteFor(themeKey, isNightMode(context))
        val courses = WidgetData.todayCourses(
            db.courseDao().getAll().map { it.toModel() },
            week,
            LocalDate.now(),
        )

        val views = RemoteViews(context.packageName, R.layout.widget_schedule)
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        // 主题渲染
        views.setInt(R.id.widget_root, "setBackgroundResource", WidgetTheme.bgRes(themeKey, isNightMode(context)))
        views.setTextColor(R.id.widget_title, palette.textPrimary)
        views.setTextColor(R.id.header_text, palette.textSecondary)
        views.setTextColor(R.id.empty_text, palette.textSecondary)
        val today = LocalDate.now()
        views.setTextViewText(
            R.id.header_text,
            context.getString(
                R.string.widget_courses_header,
                context.getString(WidgetData.dayNameRes(today.dayOfWeek.value)),
                week,
                courses.size,
            ),
        )
        if (courses.isEmpty()) {
            views.setViewVisibility(R.id.empty_text, View.VISIBLE)
            return views
        }
        views.setViewVisibility(R.id.empty_text, View.GONE)
        courses.forEach { course ->
            views.addView(R.id.course_container, courseItem(context, course))
        }
        return views
    }

    private fun isNightMode(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode
        return (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun courseItem(context: Context, course: Course): RemoteViews {
        val rawColor = if (course.color == 0) CourseMapper.colorFor(course.name) else course.color
        val text = context.getString(
            R.string.widget_course_line,
            course.startPeriod,
            course.endPeriod,
            course.name,
            course.location,
        ).trim()
        val item = RemoteViews(context.packageName, R.layout.widget_course_item)
        item.setTextViewText(R.id.course_text, text)
        item.setInt(R.id.course_item, "setBackgroundColor", CourseMapper.displayColor(rawColor))
        return item
    }

    companion object {
        /** 刷新全部已添加的实例（WorkManager 每日 / 打开 App / 设置页手动触发） */
        suspend fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, ScheduleWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            val provider = ScheduleWidgetProvider()
            ids.forEach { id -> provider.updateWidget(context, manager, id) }
        }
    }
}
