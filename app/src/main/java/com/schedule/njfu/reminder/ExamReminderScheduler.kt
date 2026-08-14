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
import com.schedule.njfu.model.Exam
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 考试当天提醒的 requestCode 偏移（与提前提醒区分，避免互相覆盖） */
private const val TODAY_OFFSET = 0x10000

class ExamReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val examId = intent.getLongExtra("exam_id", -1L)
        val examDate = intent.getStringExtra("exam_date")
        val title = intent.getStringExtra("title") ?: return
        val examName = intent.getStringExtra("exam_name") ?: return
        val location = intent.getStringExtra("location").orEmpty()

        // 兜底校验：考试仍存在且日期未变，避免删除/修改后遗留的闹钟误报
        val stillValid = examId >= 0 && examDate != null && runBlocking {
            AppDatabase.get(context).examDao().getAll()
                .any { it.id == examId && it.date == examDate }
        }
        if (!stillValid) return

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("exams", "考试提醒", NotificationManager.IMPORTANCE_HIGH),
        )
        val notification = NotificationCompat.Builder(context, "exams")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(if (location.isBlank()) examName else "$examName $location")
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        nm.notify("exam_$examId", examId.toInt(), notification)
    }
}

object ExamReminderScheduler {

    /** 重排全部考试的提醒；开关关闭时取消所有现存考试提醒 */
    suspend fun rescheduleExams(context: Context) {
        val db = AppDatabase.get(context)
        val exams = db.examDao().getAll().map { it.toModel() }
        val am = context.getSystemService(AlarmManager::class.java)
        val enabled = db.settingsDao().get(SettingsKeys.EXAM_REMIND_ENABLED) != "0"
        val days = db.settingsDao().get(SettingsKeys.EXAM_REMIND_DAYS)?.toIntOrNull() ?: 1

        exams.forEach { exam ->
            val examDate = runCatching { LocalDate.parse(exam.date) }.getOrNull()
            if (examDate == null) {
                cancel(context, am, exam)
                return@forEach
            }
            if (!enabled) {
                cancel(context, am, exam)
                return@forEach
            }
            // 提前 N 天 09:00：预告提醒
            schedule(
                context, am, exam,
                trigger = examDate.minusDays(days.toLong()).atTime(9, 0),
                requestOffset = 0,
                title = if (days == 1) "明天有考试" else "$days 天后有考试",
                examDate = examDate,
            )
            // 考试当天 08:00：临场提醒
            schedule(
                context, am, exam,
                trigger = examDate.atTime(8, 0),
                requestOffset = TODAY_OFFSET,
                title = "今天有考试",
                examDate = examDate,
            )
        }
    }

    private fun schedule(
        context: Context,
        am: AlarmManager,
        exam: Exam,
        trigger: LocalDateTime,
        requestOffset: Int,
        title: String,
        examDate: LocalDate,
    ) {
        val epoch = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val pi = pendingIntent(context, exam, requestOffset, title, examDate)
        if (epoch <= now) {
            // 触发时间已过：取消旧闹钟，避免 setExact 立即触发
            am.cancel(pi)
        } else {
            setExactSafely(am, epoch, pi)
        }
    }

    /** Android 12+ 需「闹钟与提醒」授权才能精确闹钟；未授权/抛 SecurityException 时静默跳过 */
    private fun setExactSafely(am: AlarmManager, trigger: Long, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) return
        runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi) }
    }

    private fun cancel(context: Context, am: AlarmManager, exam: Exam) {
        am.cancel(pendingIntent(context, exam, requestOffset = 0))
        am.cancel(pendingIntent(context, exam, requestOffset = TODAY_OFFSET))
    }

    private fun pendingIntent(
        context: Context,
        exam: Exam,
        requestOffset: Int,
        title: String = "",
        examDate: LocalDate? = null,
    ): PendingIntent {
        val intent = Intent(context, ExamReminderReceiver::class.java)
            .putExtra("exam_id", exam.id)
            .putExtra("exam_date", examDate?.toString() ?: "")
            .putExtra("title", title)
            .putExtra("exam_name", exam.name)
            .putExtra("location", exam.location)
        return PendingIntent.getBroadcast(
            context,
            exam.id.toInt() + requestOffset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
