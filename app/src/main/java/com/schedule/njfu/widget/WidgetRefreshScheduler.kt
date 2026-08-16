package com.schedule.njfu.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.schedule.njfu.data.AppDatabase
import com.schedule.njfu.data.semesterStart
import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils
import com.schedule.njfu.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * 4x1「下一节课」小组件的自适应刷新调度（唯一参与分钟级刷新的小组件）。
 *
 * 用一次性 AlarmManager(RTC) 闹钟 + BroadcastReceiver 思路：
 * 闹钟的 PendingIntent 直接指向 [NextClassWidgetProvider] 的 APPWIDGET_UPDATE 广播，
 * 触发后 Provider.onUpdate 更新视图并再次调用 [ensureScheduled]，形成自续调度，
 * 无需单独注册 Receiver。
 *
 * 刷新粒度约定：
 * - 处于「倒计时窗口」（上课前 60 分钟内 / 上课中 / 下课后 60 分钟内）→ 下一分钟整点；
 * - 否则 → 今天下一节课开始前 60 分钟；
 * - 今天没有更多课 → 次日 08:00。
 */
object WidgetRefreshScheduler {

    private const val REQUEST_CODE = 0x5A30   // 固定 requestCode，便于 cancel

    /** 倒计时窗口逐分钟档的触发间隔/容忍窗口：60 秒 */
    private const val COUNTDOWN_WINDOW_MS = 60_000L

    /**
     * 判定「倒计时逐分钟档」的时间余量阈值。
     * 逐分钟档的触发间隔约 60s；窗口外最近档至少 60min。取 2 分钟作为分界，
     * 既精确区分两档，又容忍 IO 线程取时的毫秒级抖动，避免边界误判成精确闹钟档。
     */
    private const val PER_MINUTE_SPAN_MS = 120_000L

    /**
     * 纯时间计算（可 JVM 单测）：下一次应刷新的时间点。
     * [periodTimes] 为 {节次: "HH:mm"}，缺省用 [WidgetData.periodTimeMap] 内置表。
     */
    fun nextRefreshAt(
        courses: List<Course>,
        week: Int,
        now: LocalDateTime,
        periodTimes: Map<Int, String>,
    ): LocalDateTime {
        val tables = WidgetData.periodTimeMap(periodTimes)
        val day = now.dayOfWeek.value
        val dayCourses = courses
            .filter { it.dayOfWeek == day && (WeekUtils.contains(it.weeks, week) || it.weeks == 0) }
        if (dayCourses.isEmpty()) return nextDayAt(now, 8, 0)

        // 每门课 (开始, endPeriod 结束+45min)
        val spans = dayCourses.mapNotNull { c ->
            val s = tables[c.startPeriod] ?: return@mapNotNull null
            val e = (tables[c.endPeriod] ?: s).plusMinutes(45)
            s to e
        }

        // 处于倒计时窗口内 → 下一分钟整点
        val nowTime = now.toLocalTime()
        val inWindow = spans.any { (s, e) ->
            !nowTime.isBefore(s.minusMinutes(60)) && nowTime.isBefore(e.plusMinutes(60))
        }
        if (inWindow) {
            return now.plusMinutes(1).withSecond(0).withNano(0)
        }

        // 不在窗口 → 今天下一节课开始前 60 分钟（仍晚于 now）
        val nextStartMinus60 = spans.mapNotNull { (s, _) ->
            val t = s.minusMinutes(60)
            if (t.isAfter(nowTime)) atTime(now, t) else null
        }.minOrNull()
        if (nextStartMinus60 != null) return nextStartMinus60

        // 今天已无更多课 → 次日 08:00
        return nextDayAt(now, 8, 0)
    }

    private fun atTime(day: LocalDateTime, time: LocalTime): LocalDateTime =
        day.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)

    private fun nextDayAt(now: LocalDateTime, h: Int, m: Int): LocalDateTime {
        val next = now.toLocalDate().plusDays(1)
        return next.atTime(h, m)
    }

    /**
     * 重新计算并设置下一次刷新闹钟。内部读库 + 起协程，可在主线程安全调用。
     * 非挂起函数：每次调用自行创建独立顶层协程作用域（SupervisorJob + Dispatchers.IO），
     * 不被调用方（如 BootReceiver / AppWidgetProvider.onUpdate）的生命周期绑定，
     * 即便调用方在 onReceive/onUpdate 结束后回收，调度计算仍会完整完成。
     * 仅当 4x1 实例存在时调度；实例不存在则取消挂起闹钟并返回。
     */
    fun ensureScheduled(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val manager = AppWidgetManager.getInstance(appContext)
                val ids = manager.getAppWidgetIds(
                    ComponentName(appContext, NextClassWidgetProvider::class.java),
                )
                if (ids.isEmpty()) {
                    // 无实例：停表/取消尚未触发的后续闹钟，避免空转逐分钟调度
                    cancelAll(appContext)
                    return@launch
                }
                val start = AppDatabase.get(appContext).settingsDao().semesterStart()
                val week = WeekUtils.currentWeek(start, LocalDate.now())
                val courses = AppDatabase.get(appContext).courseDao().getAll().map { it.toModel() }
                val periodRaw = AppDatabase.get(appContext).settingsDao().get(
                    com.schedule.njfu.data.SettingsKeys.PERIOD_TIMES,
                )
                val periodMap = WidgetData.parsePeriodTimesJson(periodRaw)

                val next = nextRefreshAt(courses, week, LocalDateTime.now(), periodMap)
                val triggerAt = System.currentTimeMillis() +
                    ChronoUnit.MILLIS.between(LocalDateTime.now(), next)
                setAlarm(appContext, triggerAt.coerceAtLeast(0), ids)
            } catch (t: Throwable) {
                DebugLog.write(appContext, "WidgetRefreshScheduler ensure FAILED", t)
            }
        }
    }

    /** 挂起/取消尚未触发的刷新闹钟 */
    fun cancelAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching {
            am.cancel(updatePendingIntent(context, intArrayOf()))
        }
    }

    private fun setAlarm(context: Context, triggerAtMillis: Long, instanceIds: IntArray) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = updatePendingIntent(context, instanceIds)
        runCatching {
            // 倒计时窗口内逐分钟档：nextRefreshAt 返回下一分钟整点，触发与 now 的间隔 ≤ 约 60s。
            // 这里用 setWindow(RTC_WAKEUP, trigger, 60_000)：允许 Doze 聚合、容忍 ≤1 分钟漂移，
            // 且无需 exact alarm 权限，属「允许的」窗口式闹钟。
            val inCountdownWindow = triggerAtMillis - System.currentTimeMillis() <= PER_MINUTE_SPAN_MS
            if (inCountdownWindow) {
                am.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMillis, COUNTDOWN_WINDOW_MS, pi)
            } else {
                // 窗口外常规档位：上课前 60 分钟 / 次日 08:00 / 数据变更后的一次性重排。
                val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    am.canScheduleExactAlarms()
                if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerAtMillis, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC, triggerAtMillis, pi)
            }
        }
    }

    /**
     * 指向 NextClassWidgetProvider 的 APPWIDGET_UPDATE 广播 PendingIntent。
     * 携带当前已添加的实例 id，触达后 Provider.onUpdate 据此刷新并再次 ensureScheduled。
     */
    private fun updatePendingIntent(context: Context, instanceIds: IntArray): PendingIntent {
        val intent = Intent(context, NextClassWidgetProvider::class.java)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, instanceIds)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
