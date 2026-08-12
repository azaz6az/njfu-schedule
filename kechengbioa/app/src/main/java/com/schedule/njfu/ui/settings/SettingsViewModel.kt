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
import com.schedule.njfu.importer.ExcelImporter
import com.schedule.njfu.importer.IcsImporter
import com.schedule.njfu.importer.JsonImporter
import com.schedule.njfu.reminder.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class SettingsViewModel(private val db: AppDatabase, private val context: Context) : ViewModel() {

    val semesterStart = MutableStateFlow(defaultSemesterStart())
    val remindMinutes = MutableStateFlow(10)
    val username = MutableStateFlow("")

    private val repo = ScheduleRepository(db)

    fun load() {
        viewModelScope.launch {
            semesterStart.value = db.settingsDao().semesterStart()
            remindMinutes.value = db.settingsDao().get(SettingsKeys.REMIND_MINUTES)?.toIntOrNull() ?: 10
            username.value = CredentialStore(context).load()?.first ?: ""
        }
    }

    fun saveSemesterStart(date: LocalDate) {
        viewModelScope.launch {
            db.settingsDao().put(SettingsEntity(SettingsKeys.SEMESTER_START, date.toString()))
            semesterStart.value = date
        }
    }

    fun saveRemindMinutes(m: Int) {
        viewModelScope.launch {
            db.settingsDao().put(SettingsEntity(SettingsKeys.REMIND_MINUTES, m.toString()))
            remindMinutes.value = m
            ReminderScheduler.rescheduleToday(context)
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
        }
    }

    fun clearCourses() {
        viewModelScope.launch { db.courseDao().clear() }
    }

    /** 从 JSON 文本导入课程（替换现有数据），返回导入课程数 */
    suspend fun importFromJson(text: String): Int {
        val courses = JsonImporter.import(text)
        repo.replaceAll(courses)
        return courses.size
    }

    /** 从 ICS 文本导入课程（替换现有数据），返回导入课程数 */
    suspend fun importFromIcs(text: String): Int {
        val courses = IcsImporter.parse(text)
        repo.replaceAll(courses)
        return courses.size
    }

    /** 从 Excel 文件导入课程（替换现有数据），返回导入课程数 */
    suspend fun importFromExcel(uri: Uri): Int {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("无法打开文件")
        val courses = input.use { ExcelImporter.parse(it) }
        repo.replaceAll(courses)
        return courses.size
    }

    /** 将课程与考试导出为 JSON 备份文件，返回课程数 */
    suspend fun exportBackup(uri: Uri): Int {
        val courses = db.courseDao().getAll().map { it.toModel() }
        val exams = db.examDao().getAll().map { it.toModel() }
        val json = JsonImporter.export(courses, exams)
        val out = context.contentResolver.openOutputStream(uri)
            ?: error("无法创建文件")
        out.use { it.write(json.toByteArray()) }
        return courses.size
    }

    fun logout() {
        viewModelScope.launch {
            CredentialStore(context).clear()
            username.value = ""
        }
    }

    class Factory(private val db: AppDatabase, private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(db, context) as T
    }
}
