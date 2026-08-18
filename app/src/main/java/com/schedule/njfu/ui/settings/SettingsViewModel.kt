package com.schedule.njfu.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.schedule.njfu.data.AppDatabase
import com.schedule.njfu.data.ScheduleRepository
import com.schedule.njfu.data.SettingsEntity
import com.schedule.njfu.data.SettingsKeys
import com.schedule.njfu.data.credentials.CredentialStore
import com.schedule.njfu.data.defaultSemesterStart
import com.schedule.njfu.data.semesterStart
import com.schedule.njfu.R
import com.schedule.njfu.importer.ExcelImporter
import com.schedule.njfu.importer.IcsImporter
import com.schedule.njfu.importer.JsonImporter
import com.schedule.njfu.model.HolidayUtils
import com.schedule.njfu.reminder.ExamReminderScheduler
import com.schedule.njfu.reminder.ReminderScheduler
import com.schedule.njfu.util.DebugLog
import com.schedule.njfu.widget.ExamCountdownWidgetProvider
import com.schedule.njfu.widget.NextClassWidgetProvider
import com.schedule.njfu.widget.ScheduleWidgetProvider
import com.schedule.njfu.widget.TodayWidgetProvider
import com.schedule.njfu.widget.WeekWidgetProvider
import com.schedule.njfu.widget.WidgetTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class SettingsViewModel(private val db: AppDatabase, private val context: Context) : ViewModel() {

    val semesterStart = MutableStateFlow(defaultSemesterStart())
    val remindMinutes = MutableStateFlow(10)
    val examRemindEnabled = MutableStateFlow(true)
    val examRemindDays = MutableStateFlow(1)
    val username = MutableStateFlow("")
    val widgetTheme = MutableStateFlow(WidgetTheme.DEFAULT_KEY)
    val rowHeight = MutableStateFlow(SettingsKeys.DEFAULT_ROW_HEIGHT)

    private val repo = ScheduleRepository(db)

    fun load() {
        viewModelScope.launch {
            semesterStart.value = db.settingsDao().semesterStart()
            remindMinutes.value = db.settingsDao().get(SettingsKeys.REMIND_MINUTES)?.toIntOrNull() ?: 10
            examRemindEnabled.value = db.settingsDao().get(SettingsKeys.EXAM_REMIND_ENABLED) != "0"
            examRemindDays.value = db.settingsDao().get(SettingsKeys.EXAM_REMIND_DAYS)?.toIntOrNull() ?: 1
            username.value = CredentialStore(context).load()?.first ?: ""
            widgetTheme.value =
                db.settingsDao().get(SettingsKeys.WIDGET_THEME) ?: WidgetTheme.DEFAULT_KEY
            rowHeight.value =
                db.settingsDao().get(SettingsKeys.ROW_HEIGHT)?.toIntOrNull()
                    ?: SettingsKeys.DEFAULT_ROW_HEIGHT
        }
    }

    fun saveSemesterStart(date: LocalDate) {
        viewModelScope.launch {
            db.settingsDao().put(SettingsEntity(SettingsKeys.SEMESTER_START, date.toString()))
            semesterStart.value = date
        }
    }

    /** 读取调休映射（日期 → 按周几显示） */
    suspend fun loadShifts(): Map<LocalDate, Int> =
        HolidayUtils.parseShifts(db.settingsDao().get(SettingsKeys.HOLIDAY_SHIFTS))

    /** 保存调休映射并重排提醒（今日按映射星期上课） */
    fun saveShifts(shifts: Map<LocalDate, Int>) {
        viewModelScope.launch {
            db.settingsDao().put(
                SettingsEntity(SettingsKeys.HOLIDAY_SHIFTS, HolidayUtils.serializeShifts(shifts)),
            )
            ReminderScheduler.rescheduleToday(context)
        }
    }

    fun saveRemindMinutes(m: Int) {
        viewModelScope.launch {
            db.settingsDao().put(SettingsEntity(SettingsKeys.REMIND_MINUTES, m.toString()))
            remindMinutes.value = m
            ReminderScheduler.rescheduleToday(context)
        }
    }

    fun saveExamRemindEnabled(enabled: Boolean) {
        viewModelScope.launch {
            db.settingsDao().put(SettingsEntity(SettingsKeys.EXAM_REMIND_ENABLED, if (enabled) "1" else "0"))
            examRemindEnabled.value = enabled
            ExamReminderScheduler.rescheduleExams(context)
        }
    }

    fun saveExamRemindDays(days: Int) {
        viewModelScope.launch {
            db.settingsDao().put(SettingsEntity(SettingsKeys.EXAM_REMIND_DAYS, days.toString()))
            examRemindDays.value = days
            ExamReminderScheduler.rescheduleExams(context)
        }
    }

    /** 读取已保存的节次时间段；未保存/解析失败时返回默认 10 节 */
    suspend fun loadPeriodTimes(): List<Pair<Int, String>> {
        val raw = db.settingsDao().get(SettingsKeys.PERIOD_TIMES)
            ?: return ReminderScheduler.defaultPeriodTimes()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                o.getInt("p") to o.getString("t")
            }
        }.getOrElse { ReminderScheduler.defaultPeriodTimes() }
    }

    fun savePeriodTimes(times: List<Pair<Int, String>>) {
        viewModelScope.launch {
            val arr = JSONArray()
            times.forEach { (p, t) -> arr.put(JSONObject().put("p", p).put("t", t)) }
            db.settingsDao().put(SettingsEntity(SettingsKeys.PERIOD_TIMES, arr.toString()))
            // 节次时间影响今日提醒的触发时刻，改后需重排
            ReminderScheduler.rescheduleToday(context)
        }
    }

    /** 保存小组件主题并即时刷新全部已添加的小组件 */
    fun saveWidgetTheme(key: String) {
        viewModelScope.launch {
            db.settingsDao().put(SettingsEntity(SettingsKeys.WIDGET_THEME, key))
            widgetTheme.value = key
            ScheduleWidgetProvider.refreshAll(context)
            WeekWidgetProvider.refreshAll(context)
            NextClassWidgetProvider.refreshAll(context)
            TodayWidgetProvider.refreshAll(context)
            ExamCountdownWidgetProvider.refreshAll(context)
        }
    }

    /** 保存课表每节行高（dp）；课表页通过设置流响应式生效 */
    fun saveRowHeight(height: Int) {
        viewModelScope.launch {
            db.settingsDao().put(SettingsEntity(SettingsKeys.ROW_HEIGHT, height.toString()))
            rowHeight.value = height
        }
    }

    fun clearCourses() {
        viewModelScope.launch { db.courseDao().clear() }
    }

    /** 从 JSON 文本导入课程（替换现有数据），返回导入课程数 */
    suspend fun importFromJson(text: String): Int = withContext(Dispatchers.IO) {
        val courses = JsonImporter.import(text)
        require(courses.isNotEmpty()) { context.getString(R.string.import_error_no_courses) }
        repo.replaceAll(courses)
        courses.size
    }

    /** 从 ICS 文本导入课程（替换现有数据），返回导入课程数 */
    suspend fun importFromIcs(text: String): Int = withContext(Dispatchers.IO) {
        val courses = IcsImporter.parse(text, db.settingsDao().semesterStart())
        require(courses.isNotEmpty()) { context.getString(R.string.import_error_no_courses) }
        repo.replaceAll(courses)
        courses.size
    }

    /** 从 Excel 文件导入课程（替换现有数据），返回导入课程数 */
    suspend fun importFromExcel(uri: Uri): Int = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: error(context.getString(R.string.error_cannot_open_file))
        val courses = input.use { ExcelImporter.parse(it) }
        require(courses.isNotEmpty()) { context.getString(R.string.import_error_no_courses) }
        repo.replaceAll(courses)
        courses.size
    }

    /** 将课程与考试导出为 JSON 备份文件，返回课程数 */
    suspend fun exportBackup(uri: Uri): Int {
        val courses = db.courseDao().getAll().map { it.toModel() }
        val exams = db.examDao().getAll().map { it.toModel() }
        val json = JsonImporter.export(courses, exams)
        val out = context.contentResolver.openOutputStream(uri)
            ?: error(context.getString(R.string.error_cannot_create_file))
        out.use { it.write(json.toByteArray()) }
        return courses.size
    }

    /** 导出调试日志（App 启动记录、崩溃堆栈、小部件渲染失败原因），供问题排查 */
    suspend fun exportDebugLog(uri: Uri) {
        val log = DebugLog.read(context)
        require(log.isNotBlank()) { context.getString(R.string.error_no_debug_log) }
        val out = context.contentResolver.openOutputStream(uri)
            ?: error(context.getString(R.string.error_cannot_create_file))
        out.use { it.write(log.toByteArray(Charsets.UTF_8)) }
    }

    fun logout() {
        viewModelScope.launch {
            CredentialStore(context).clear()
            username.value = ""
        }
    }

    class Factory(private val db: AppDatabase, context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(db, appContext) as T
    }
}
