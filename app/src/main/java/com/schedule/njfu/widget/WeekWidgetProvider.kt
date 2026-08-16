package com.schedule.njfu.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
 * 4x3 本周课表桌面小组件（标准 RemoteViews 实现，同 [ScheduleWidgetProvider] 的原因弃用 Glance）。
 * 周一到周日 7 列，每天最多 [MAX_PER_DAY] 门课，当日列表头高亮。
 */
class WeekWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
            } catch (t: Throwable) {
                DebugLog.write(context, "WeekWidget render FAILED", t)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
        manager.updateAppWidget(appWidgetId, buildViews(context))
    }

    private suspend fun buildViews(context: Context): RemoteViews {
        val db = AppDatabase.get(context)
        val start = db.settingsDao().semesterStart()
        val week = WeekUtils.currentWeek(start, LocalDate.now())
        val themeKey = db.settingsDao().widgetTheme()
        val palette = WidgetTheme.paletteFor(themeKey, isNightMode(context))
        val byDay = WidgetData.weekCoursesByDay(
            db.courseDao().getAll().map { it.toModel() },
            week,
            MAX_PER_DAY,
        )

        val views = RemoteViews(context.packageName, R.layout.widget_week)
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        // 主题渲染：根背景（圆角保留）+ 表头/文字色
        views.setInt(R.id.widget_root, "setBackgroundResource", WidgetTheme.bgRes(themeKey, isNightMode(context)))
        (1..7).forEach { d ->
            views.setTextColor(DAY_LABELS[d - 1], palette.textSecondary)
        }
        // 今日列表头高亮
        val today = LocalDate.now().dayOfWeek.value
        views.setTextColor(DAY_LABELS[today - 1], palette.accent)
        (1..7).forEach { day ->
            byDay[day].orEmpty().forEach { course ->
                views.addView(DAY_COLUMNS[day - 1], courseItem(context, course))
            }
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
            R.string.widget_week_course_line,
            course.startPeriod,
            course.endPeriod,
            course.name,
        ).trim()
        val item = RemoteViews(context.packageName, R.layout.widget_week_item)
        item.setTextViewText(R.id.week_course_text, text)
        item.setInt(R.id.week_item, "setBackgroundColor", CourseMapper.displayColor(rawColor))
        return item
    }

    companion object {
        private const val MAX_PER_DAY = 4

        private val DAY_LABELS = intArrayOf(
            R.id.day_label_1, R.id.day_label_2, R.id.day_label_3, R.id.day_label_4,
            R.id.day_label_5, R.id.day_label_6, R.id.day_label_7,
        )
        private val DAY_COLUMNS = intArrayOf(
            R.id.day_col_1, R.id.day_col_2, R.id.day_col_3, R.id.day_col_4,
            R.id.day_col_5, R.id.day_col_6, R.id.day_col_7,
        )

        /** 刷新全部已添加的实例（WorkManager 每日 / 打开 App / 设置页手动触发） */
        suspend fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, WeekWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            val provider = WeekWidgetProvider()
            ids.forEach { id -> provider.updateWidget(context, manager, id) }
        }
    }
}
