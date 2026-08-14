package com.schedule.njfu.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.schedule.njfu.MainActivity
import com.schedule.njfu.data.AppDatabase
import com.schedule.njfu.data.SettingsKeys
import com.schedule.njfu.data.semesterStart
import com.schedule.njfu.model.Course
import com.schedule.njfu.model.HolidayUtils
import com.schedule.njfu.model.WeekUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.ZoneId

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val courseId = intent.getLongExtra("course_id", -1L)
        val courseName = intent.getStringExtra("course_name") ?: return
        val location = intent.getStringExtra("location") ?: ""
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel("schedule", "课程提醒", NotificationManager.IMPORTANCE_HIGH)
        nm.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(context, "schedule")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("即将上课")
            .setContentText("$courseName $location")
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context, 0,
                Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .build()
        nm.notify(courseId.toInt(), notification)
    }
}

object ReminderScheduler {

    /** 为指定周的所有课程安排当天提醒；[shifts] 为调休映射（日期 → 按周几上课） */
    fun scheduleDay(
        context: Context,
        courses: List<Course>,
        week: Int,
        minutesBefore: Int,
        shifts: Map<LocalDate, Int> = emptyMap(),
    ) {
        val am = context.getSystemService(AlarmManager::class.java)
        val today = LocalDate.now()
        val times = loadPeriodTimes(context)
        // 调休：今天按映射星期上课（如周六补周一的课，则提醒周一的课程）；映射为 0 表示放假无课
        val effectiveDay = HolidayUtils.shiftedDayOfWeek(today, shifts)
        if (effectiveDay !in 1..7) return
        courses
            .filter { it.dayOfWeek == effectiveDay && WeekUtils.contains(it.weeks, week) }
            .forEach { course ->
                val start = WeekUtils.startTimeOf(course.startPeriod, times)
                if (start.isBlank()) return@forEach
                val hm = start.split(":")
                val trigger = today.atTime(hm[0].toInt(), hm[1].toInt())
                    .minusMinutes(minutesBefore.toLong())
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val pi = PendingIntent.getBroadcast(context, course.id.toInt(),
                    Intent(context, ReminderReceiver::class.java)
                        .putExtra("course_id", course.id)
                        .putExtra("course_name", course.name)
                        .putExtra("location", course.location),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                setExactSafely(am, trigger, pi)
            }
    }

    /** Android 12+ 需「闹钟与提醒」授权才能精确闹钟；未授权/抛 SecurityException 时静默跳过 */
    private fun setExactSafely(am: AlarmManager, trigger: Long, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) return
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

    /** App 启动/设置变更后调用：重排今日提醒 */
    suspend fun rescheduleToday(context: Context) {
        val db = AppDatabase.get(context)
        val start = db.settingsDao().semesterStart()
        val week = WeekUtils.currentWeek(start, LocalDate.now())
        val minutes = db.settingsDao().get(SettingsKeys.REMIND_MINUTES)?.toIntOrNull() ?: 10
        val shifts = HolidayUtils.parseShifts(db.settingsDao().get(SettingsKeys.HOLIDAY_SHIFTS))
        val courses = db.courseDao().getAll().map { it.toModel() }
        scheduleDay(context, courses, week, minutes, shifts)
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
            } finally {
                pending.finish()
            }
        }
    }
}
