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
 * 4x2 今日课程小组件（标准 RemoteViews）：表头 + 最多 4 行课程 + 考试倒计时行。
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ids.forEach { id -> updateWidget(context, manager, id) }
            } catch (t: Throwable) {
                DebugLog.write(context, "TodayWidget render FAILED", t)
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
        val today = LocalDate.now()
        val day = today.dayOfWeek.value
        val courses = WidgetData.todayCourses(
            db.courseDao().getAll().map { it.toModel() }, week, today,
        )
        val exams = db.examDao().getAll().map { it.toModel() }
        val exam = WidgetData.nextExamCountdown(exams, today)

        val views = RemoteViews(context.packageName, R.layout.widget_today)
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        // 主题
        views.setInt(R.id.widget_root, "setBackgroundResource", WidgetTheme.bgRes(themeKey, isNightMode(context)))
        views.setTextColor(R.id.today_header, palette.textPrimary)
        views.setTextColor(R.id.exam_line, palette.accent)

        views.setTextViewText(
            R.id.today_header,
            "周${WidgetData.dayNameCn(day)} · 第${week}周 · ${courses.size}节课",
        )

        courses.take(MAX_ROWS).forEach { course ->
            views.addView(R.id.today_course_container, courseItem(context, course))
        }
        if (courses.isEmpty()) {
            val empty = RemoteViews(context.packageName, R.layout.widget_today_item)
            empty.setTextViewText(R.id.today_item_text, "今天无课")
            empty.setInt(R.id.today_item, "setBackgroundColor", palette.card)
            empty.setTextColor(R.id.today_item_text, palette.textSecondary)
            views.addView(R.id.today_course_container, empty)
        }

        if (exam != null) {
            val (e, days) = exam
            val countdown = if (days <= 0) "今天" else "还有 ${days} 天"
            views.setViewVisibility(R.id.exam_line, View.VISIBLE)
            views.setTextViewText(
                R.id.exam_line,
                "考试倒计时：${e.name} · $countdown",
            )
        } else {
            views.setViewVisibility(R.id.exam_line, View.GONE)
        }
        return views
    }

    private fun courseItem(context: Context, course: Course): RemoteViews {
        val rawColor = if (course.color == 0) CourseMapper.colorFor(course.name) else course.color
        val text = "${course.startPeriod}-${course.endPeriod}节 ${course.name} ${course.location}".trim()
        val item = RemoteViews(context.packageName, R.layout.widget_today_item)
        item.setTextViewText(R.id.today_item_text, text)
        item.setInt(R.id.today_item, "setBackgroundColor", CourseMapper.displayColor(rawColor))
        return item
    }

    private fun isNightMode(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode
        return (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    companion object {
        private const val MAX_ROWS = 4

        suspend fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, TodayWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            val provider = TodayWidgetProvider()
            ids.forEach { id -> provider.updateWidget(context, manager, id) }
        }
    }
}
