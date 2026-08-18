package com.schedule.njfu.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.schedule.njfu.MainActivity
import com.schedule.njfu.R
import com.schedule.njfu.data.AppDatabase
import com.schedule.njfu.data.SettingsKeys
import com.schedule.njfu.data.semesterStart
import com.schedule.njfu.model.Course
import com.schedule.njfu.model.HolidayUtils
import com.schedule.njfu.model.WeekUtils
import com.schedule.njfu.util.DebugLog
import com.schedule.njfu.widget.WidgetRefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 调休感知的核心判定（纯函数）：给定课程的「自然发生日期清单」[occurrenceDates] 与
 * 调休映射 [shifts]，返回其中【实际应响铃】的日期。逐日判定规则（与
 * [ReminderScheduler.scheduleDay] / [ReminderReceiver] 共用同一语义）：
 *  - 某次日期被映射为 0 → 当天放假，跳过该日闹钟（不放行任何课程）；
 *  - 某次日期被映射为 X(1..7) → 当天按「星期 X 的课表」上课：仅当 X == courseDayOfWeek
 *    时该课程在这天响铃（如周六补周一的课，周六要响周一的课程），否则该日被别的星期顶替；
 *  - 无映射 → 保持自然周上课逻辑（courseDayOfWeek == 当天自然星期时响铃）。
 * 不依赖真实时钟与系统状态，便于 JUnit4 单测；返回按输入顺序过滤后的日期。
 */
internal fun effectiveClassDates(
    occurrenceDates: List<LocalDate>,
    courseDayOfWeek: Int,
    shifts: Map<LocalDate, Int>,
): List<LocalDate> = occurrenceDates.filter { date ->
    HolidayUtils.shiftedDayOfWeek(date, shifts) == courseDayOfWeek
}

/**
 * 计算某节课程（[startTime] "HH:mm"）减去提前量 [minutesBefore] 分钟的触发 epoch（毫秒）。
 * 用于统一提醒触发时间的计算，便于纯函数单测。
 * @return 触发 epoch 毫秒；返回 null 表示节次时间非法（无法解析或越界）。
 */
internal fun computeTriggerEpoch(
    date: LocalDate,
    startTime: String,
    minutesBefore: Int,
): Long? {
    val time = runCatching { LocalTime.parse(startTime) }.getOrNull() ?: return null
    val trigger = date.atTime(time).minusMinutes(minutesBefore.toLong())
    return trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

class ReminderReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val courseId = intent.getLongExtra("course_id", -1L)
        if (courseId < 0) return
        // goAsync 保证在库校验/重排完成前广播不被提前回收
        val pending = goAsync()
        scope.launch {
            try {
                val db = AppDatabase.get(context)
                val today = LocalDate.now()
                val start = db.settingsDao().semesterStart()
                val week = WeekUtils.currentWeek(start, today)
                val shifts = HolidayUtils.parseShifts(
                    db.settingsDao().get(SettingsKeys.HOLIDAY_SHIFTS),
                )
                val am = context.getSystemService(AlarmManager::class.java)

                // 校验课程仍存在，且今天确实应上课（经调休映射判定：映射 0 放假不响，
                // 映射 X 当天按星期 X 的课表响，无映射按自然星期）+ 当前周有课
                val course = db.courseDao().getAll().map { it.toModel() }
                    .firstOrNull { it.id == courseId }
                val shouldClass = course != null &&
                    effectiveClassDates(listOf(today), course.dayOfWeek, shifts).isNotEmpty() &&
                    WeekUtils.contains(course.weeks, week)
                if (!shouldClass) {
                    // 课程已删除/日期已过/今天调休不上课：取消本次提醒，不弹通知
                    am.cancel(fireIntent(context, courseId))
                    return@launch
                }

                val courseName = course!!.name
                val location = course.location
                val nm = context.getSystemService(NotificationManager::class.java)
                val notification = NotificationCompat.Builder(context, "schedule")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(context.getString(R.string.notification_class_soon))
                    .setContentText("$courseName $location")
                    .setAutoCancel(true)
                    .setContentIntent(PendingIntent.getActivity(context, 0,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE))
                    .build()
                nm.notify((courseId and 0x7FFFFFFF).toInt(), notification)

                // 自续：为明天再排一次提醒，保证连续数天不打开 App 提醒也不断
                ReminderScheduler.rescheduleTomorrow(context)
            } finally {
                pending.finish()
            }
        }
    }

    /** 构造课程的 PendingIntent（与 [ReminderScheduler] 中 set/cancel 侧保持一致） */
    private fun fireIntent(context: Context, courseId: Long): PendingIntent {
        val requestCode = (courseId and 0x7FFFFFFF).toInt()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ReminderReceiver::class.java)
                .putExtra("course_id", courseId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

object ReminderScheduler {

    /**
     * 为指定周的课程安排某一天（默认今天）的提醒。
     * [date] 用于计算调休映射（[shifts]）与触发时间；[week] 为 [date] 所处周次。
     * 触发时间已过（trigger <= now）的课程被跳过，避免打开 App 时立即误报。
     */
    fun scheduleDay(
        context: Context,
        courses: List<Course>,
        week: Int,
        minutesBefore: Int,
        shifts: Map<LocalDate, Int> = emptyMap(),
        date: LocalDate = LocalDate.now(),
    ) {
        val am = context.getSystemService(AlarmManager::class.java)
        val times = loadPeriodTimes(context)
        val now = System.currentTimeMillis()
        // 调休：每门课经 effectiveClassDates 逐日判定是否应响——某日映射为 0（放假）当天全体
        // 跳过；映射为 X 当天按星期 X 的课表触发（如周六补周一的课，周六要响周一的课程）；
        // 无映射保持自然周上课逻辑（course.dayOfWeek 对应当天星期几）。
        courses
            .filter { course ->
                WeekUtils.contains(course.weeks, week) &&
                    effectiveClassDates(listOf(date), course.dayOfWeek, shifts).isNotEmpty()
            }
            .forEach { course ->
                val start = WeekUtils.startTimeOf(course.startPeriod, times)
                if (start.isBlank()) return@forEach
                val trigger = computeTriggerEpoch(date, start, minutesBefore) ?: return@forEach
                if (trigger <= now) return@forEach
                val pi = PendingIntent.getBroadcast(
                    context,
                    (course.id and 0x7FFFFFFF).toInt(),
                    Intent(context, ReminderReceiver::class.java)
                        .putExtra("course_id", course.id)
                        .putExtra("course_name", course.name)
                        .putExtra("location", course.location),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                setExactSafely(context, am, trigger, pi)
            }
    }

    /**
     * Android 12+ 需「闹钟与提醒」授权才能精确闹钟。
     * 未授权时不静默跳过，降级为 [AlarmManager.setWindow]（±60s 窗口）+ 记录日志；
     * 仍保留对 SecurityException 的兜底。
     */
    private fun setExactSafely(context: Context, am: AlarmManager, trigger: Long, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            DebugLog.write(
                context.applicationContext,
                "ReminderScheduler: no SCHEDULE_EXACT_ALARM, fallback to setWindow",
            )
            runCatching {
                am.setWindow(AlarmManager.RTC_WAKEUP, trigger, 60_000, pi)
            }
            return
        }
        runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi) }
    }

    fun loadPeriodTimes(context: Context): List<Pair<Int, String>> {
        val raw = runBlocking {
            AppDatabase.get(context).settingsDao().get(SettingsKeys.PERIOD_TIMES)
        } ?: return defaultPeriodTimes()
        return runCatching {
            // JSON 格式 [{"p":1,"t":"08:00"},...]（任务 14 设置页写入；当前可能为空则用默认）
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                o.getInt("p") to o.getString("t")
            }
        }.getOrElse { defaultPeriodTimes() }
    }

    fun defaultPeriodTimes(): List<Pair<Int, String>> = listOf(
        1 to "08:00", 2 to "09:00", 3 to "10:00", 4 to "11:00",
        5 to "14:00", 6 to "15:00", 7 to "16:00", 8 to "17:00",
        9 to "19:00", 10 to "20:00", 11 to "21:00", 12 to "22:00",
    )

    /**
     * App 启动/设置变更后调用：重排今日与明日提醒。
     * 明日用其自身日期计算周次与调休映射，保证跨日自续。
     */
    suspend fun rescheduleToday(context: Context) {
        val db = AppDatabase.get(context)
        val start = db.settingsDao().semesterStart()
        val minutes = db.settingsDao().get(SettingsKeys.REMIND_MINUTES)?.toIntOrNull() ?: 10
        val shifts = HolidayUtils.parseShifts(db.settingsDao().get(SettingsKeys.HOLIDAY_SHIFTS))
        val courses = db.courseDao().getAll().map { it.toModel() }
        val today = LocalDate.now()
        val todayWeek = WeekUtils.currentWeek(start, today)
        scheduleDay(context, courses, todayWeek, minutes, shifts, date = today)

        val tomorrow = today.plusDays(1)
        val tomorrowWeek = WeekUtils.currentWeek(start, tomorrow)
        scheduleDay(context, courses, tomorrowWeek, minutes, shifts, date = tomorrow)
    }

    /**
     * 为明天重排一次提醒（不弹通知）。供 [ReminderReceiver] 自续调用，
     * 也用于开机/启动时把明天一并排好。
     */
    suspend fun rescheduleTomorrow(context: Context) {
        val db = AppDatabase.get(context)
        val start = db.settingsDao().semesterStart()
        val minutes = db.settingsDao().get(SettingsKeys.REMIND_MINUTES)?.toIntOrNull() ?: 10
        val shifts = HolidayUtils.parseShifts(db.settingsDao().get(SettingsKeys.HOLIDAY_SHIFTS))
        val courses = db.courseDao().getAll().map { it.toModel() }
        val tomorrow = LocalDate.now().plusDays(1)
        val tomorrowWeek = WeekUtils.currentWeek(start, tomorrow)
        scheduleDay(context, courses, tomorrowWeek, minutes, shifts, date = tomorrow)
    }
}

class BootReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // goAsync 保证广播在后台重排完成后才结束，避免进程被提前回收
        val pending = goAsync()
        scope.launch {
            try {
                ReminderScheduler.rescheduleToday(context)
                ExamReminderScheduler.rescheduleExams(context)
                // 开机重排后补全小部件刷新调度（widget 模块，非挂起、Receiver 安全）
                WidgetRefreshScheduler.ensureScheduled(context)
            } finally {
                pending.finish()
            }
        }
    }
}
