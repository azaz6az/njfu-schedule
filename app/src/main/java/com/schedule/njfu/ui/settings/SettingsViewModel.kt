package com.schedule.njfu.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.schedule.njfu.data.AppDatabase
import com.schedule.njfu.data.SettingsEntity
import com.schedule.njfu.data.SettingsKeys
import com.schedule.njfu.data.credentials.CredentialStore
import com.schedule.njfu.data.defaultSemesterStart
import com.schedule.njfu.data.semesterStart
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
