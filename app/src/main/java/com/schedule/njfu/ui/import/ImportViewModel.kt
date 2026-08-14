package com.schedule.njfu.ui.import

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.schedule.njfu.data.AppDatabase
import com.schedule.njfu.data.ScheduleRepository
import com.schedule.njfu.importer.ExcelImporter
import com.schedule.njfu.importer.IcsImporter
import com.schedule.njfu.importer.JsonImporter
import com.schedule.njfu.importer.NjfuXlsImporter
import com.schedule.njfu.importer.njfu.NjfuAdapter
import com.schedule.njfu.model.WeekUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImportViewModel(private val db: AppDatabase, private val context: Context) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data class Loading(val stage: String) : UiState
        /** [fixedWeeks] > 0 表示有课程周次无法解析、已按全学期显示 */
        data class Done(val courseCount: Int, val fixedWeeks: Int = 0) : UiState
        data class Error(val message: String) : UiState
    }

    val state = MutableStateFlow<UiState>(UiState.Idle)

    private val repo = ScheduleRepository(db)

    /**
     * WebView 登录完成后回传的会话 Cookie → 抓课表页 → 入库。
     * 登录本身在 [CasLoginActivity] 的 WebView 内完成（教务系统拒绝非浏览器客户端）。
     */
    fun autoImportWithCookies(cookies: String) {
        if (cookies.isBlank()) {
            state.value = UiState.Error("未获取到登录会话，请重试或改用下方手动导入");
            return
        }
        viewModelScope.launch {
            state.value = UiState.Loading("正在获取课表…")
            val result = withContext(Dispatchers.IO) {
                NjfuAdapter().fetchScheduleWithCookies(cookies)
            }
            result.onSuccess { courses ->
                replaceWithWarnings(courses)
            }.onFailure { e ->
                state.value = UiState.Error("获取课表失败：${e.message}，可改用下方手动导入")
            }
        }
    }

    fun manualJson(text: String) {
        viewModelScope.launch {
            try {
                val courses = JsonImporter.import(text)
                replaceWithWarnings(courses)
            } catch (e: Exception) {
                state.value = UiState.Error("JSON 解析失败：${e.message}")
            }
        }
    }

    fun manualIcs(text: String) {
        viewModelScope.launch {
            try {
                val courses = IcsImporter.parse(text)
                replaceWithWarnings(courses)
            } catch (e: Exception) {
                state.value = UiState.Error("ICS 解析失败：${e.message}")
            }
        }
    }

    fun manualExcel(uri: Uri) {
        viewModelScope.launch {
            try {
                val input = context.contentResolver.openInputStream(uri)
                if (input != null) {
                    input.use {
                        val courses = ExcelImporter.parse(it)
                        replaceWithWarnings(courses)
                    }
                } else {
                    state.value = UiState.Error("无法打开文件")
                }
            } catch (e: Exception) {
                state.value = UiState.Error("Excel 解析失败：${e.message}")
            }
        }
    }

    /** 导入教务系统「学生个人课表」导出的 .xls（老式格式，首选手动导入方式） */
    fun manualNjfuXls(uri: Uri) {
        viewModelScope.launch {
            try {
                val input = context.contentResolver.openInputStream(uri)
                if (input != null) {
                    input.use {
                        val courses = NjfuXlsImporter.parse(it)
                        if (courses.isEmpty()) {
                            state.value = UiState.Error("未解析到课程，请确认是教务系统导出的「学生个人课表.xls」")
                        } else {
                            replaceWithWarnings(courses)
                        }
                    }
                } else {
                    state.value = UiState.Error("无法打开文件")
                }
            } catch (e: Exception) {
                state.value = UiState.Error("课表解析失败：${e.message}")
            }
        }
    }

    /** 兜底：周次掩码为 0（解析失败）的课程按全学期显示，避免“课进了库却永远不显示” */
    private suspend fun replaceWithWarnings(courses: List<com.schedule.njfu.model.Course>) {
        val (normalized, fixed) = WeekUtils.fixMissingWeeks(courses)
        repo.replaceAll(normalized)
        state.value = UiState.Done(normalized.size, fixed)
    }

    fun reset() { state.value = UiState.Idle }

    class Factory(private val db: AppDatabase, private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ImportViewModel(db, context) as T
    }
}
