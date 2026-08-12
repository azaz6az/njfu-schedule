package com.schedule.njfu.ui.import

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.schedule.njfu.data.AppDatabase
import com.schedule.njfu.data.ScheduleRepository
import com.schedule.njfu.data.credentials.CredentialStore
import com.schedule.njfu.importer.Credentials
import com.schedule.njfu.importer.ExcelImporter
import com.schedule.njfu.importer.IcsImporter
import com.schedule.njfu.importer.JsonImporter
import com.schedule.njfu.importer.njfu.NjfuAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ImportViewModel(private val db: AppDatabase, private val context: Context) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data class Loading(val stage: String) : UiState
        data class Done(val courseCount: Int) : UiState
        data class Error(val message: String) : UiState
    }

    val state = MutableStateFlow<UiState>(UiState.Idle)
    /** 已保存的学号（用于"已登录"提示），空串表示未登录 */
    val loggedInUsername = MutableStateFlow("")

    private val repo = ScheduleRepository(db)

    init {
        viewModelScope.launch {
            loggedInUsername.value = CredentialStore(context).load()?.first ?: ""
        }
    }

    fun autoImport(username: String, password: String) {
        viewModelScope.launch {
            state.value = UiState.Loading("正在登录教务系统…")
            val adapter = NjfuAdapter()
            val loginResult = adapter.login(Credentials(username, password))
            if (loginResult.isFailure) {
                val msg = loginResult.exceptionOrNull()?.message ?: "登录失败"
                state.value = UiState.Error(when {
                    msg.contains("用户名或密码") -> "学号或密码错误，请检查后重试"
                    msg.contains("验证码") -> "教务系统要求验证码，请稍后重试或使用手动导入"
                    else -> "登录失败：$msg"
                })
                return@launch
            }
            state.value = UiState.Loading("正在获取课表…")
            adapter.fetchSchedule().onSuccess { courses ->
                val merged = ScheduleRepository.merge(courses, emptyList())
                repo.replaceAll(merged)
                CredentialStore(context).save(username, password)
                loggedInUsername.value = username
                state.value = UiState.Done(merged.size)
            }.onFailure { e ->
                state.value = UiState.Error("获取课表失败：${e.message}")
            }
        }
    }

    fun manualJson(text: String) {
        viewModelScope.launch {
            try {
                val courses = JsonImporter.import(text)
                repo.replaceAll(courses)
                state.value = UiState.Done(courses.size)
            } catch (e: Exception) {
                state.value = UiState.Error("JSON 解析失败：${e.message}")
            }
        }
    }

    fun manualIcs(text: String) {
        viewModelScope.launch {
            try {
                val courses = IcsImporter.parse(text)
                repo.replaceAll(courses)
                state.value = UiState.Done(courses.size)
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
                        repo.replaceAll(courses)
                        state.value = UiState.Done(courses.size)
                    }
                } else {
                    state.value = UiState.Error("无法打开文件")
                }
            } catch (e: Exception) {
                state.value = UiState.Error("Excel 解析失败：${e.message}")
            }
        }
    }

    fun reset() { state.value = UiState.Idle }

    class Factory(private val db: AppDatabase, private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ImportViewModel(db, context) as T
    }
}
