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
import com.schedule.njfu.data.widgetTheme
import com.schedule.njfu.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 2x2 考试倒计时小组件（标准 RemoteViews）。
 * 点击前往「考试」Tab。
 */
class ExamCountdownWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ids.forEach { id -> updateWidget(context, manager, id) }
            } catch (t: Throwable) {
                DebugLog.write(context, "ExamCountdownWidget render FAILED", t)
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
        val themeKey = db.settingsDao().widgetTheme()
        val palette = WidgetTheme.paletteFor(themeKey, isNightMode(context))
        val today = LocalDate.now()
        val exam = WidgetData.nextExamCountdown(
            db.examDao().getAll().map { it.toModel() }, today,
        )

        val views = RemoteViews(context.packageName, R.layout.widget_exam)
        val launchExam = Intent(context, MainActivity::class.java).putExtra(EXTRA_START_TAB, "exam")
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context, 0, launchExam,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        // 主题
        views.setInt(R.id.widget_root, "setBackgroundResource", WidgetTheme.bgRes(themeKey, isNightMode(context)))
        views.setTextColor(R.id.exam_label, palette.textSecondary)
        views.setTextColor(R.id.exam_course, palette.textSecondary)

        if (exam == null) {
            views.setTextViewText(R.id.exam_label, "下一场考试")
            views.setTextViewText(R.id.exam_big, "暂无考试安排")
            views.setTextColor(R.id.exam_big, palette.textPrimary)
            views.setViewVisibility(R.id.exam_course, View.GONE)
            return views
        }

        val (e, days) = exam
        views.setTextViewText(R.id.exam_big, if (days <= 0) "今天" else "还有 ${days} 天")
        views.setTextColor(R.id.exam_big, if (days <= 0) palette.accent else palette.textPrimary)
        views.setViewVisibility(R.id.exam_course, View.VISIBLE)
        val formatted = runCatching {
            LocalDate.parse(e.date).format(DateTimeFormatter.ofPattern("MM月dd日"))
        }.getOrElse { e.date }
        views.setTextViewText(R.id.exam_course, "${e.name} · $formatted")
        return views
    }

    private fun isNightMode(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode
        return (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    companion object {
        private const val EXTRA_START_TAB = "start_tab"

        suspend fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, ExamCountdownWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            val provider = ExamCountdownWidgetProvider()
            ids.forEach { id -> provider.updateWidget(context, manager, id) }
        }
    }
}
