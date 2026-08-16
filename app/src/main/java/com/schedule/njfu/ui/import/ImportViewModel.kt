package com.schedule.njfu.ui.import

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.schedule.njfu.data.AppDatabase
import com.schedule.njfu.data.ImportDiff
import com.schedule.njfu.data.ScheduleRepository
import com.schedule.njfu.data.SettingsKeys
import com.schedule.njfu.R
import com.schedule.njfu.importer.ExcelImporter
import com.schedule.njfu.importer.IcsImporter
import com.schedule.njfu.importer.JsonImporter
import com.schedule.njfu.importer.NjfuXlsImporter
import com.schedule.njfu.importer.gxu.GxuAdapter
import com.schedule.njfu.importer.njfu.NjfuAdapter
import com.schedule.njfu.model.Course
import com.schedule.njfu.model.Exam
import com.schedule.njfu.model.WeekUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImportViewModel(private val db: AppDatabase, private val context: Context) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data class Loading(val stage: String) : UiState
        /** 解析成功后的预览：展示与现有课表的差异，确认后才落库 */
        data class Preview(
            val diff: ImportDiff,
            val fixedWeeks: Int = 0,
            /** 考试安排（可能为空列表）；[examFailed] 为 true 表示考试抓取失败、本次仅导入课表 */
            val exams: List<Exam> = emptyList(),
            val examFailed: Boolean = false,
        ) : UiState
        /** [fixedWeeks] > 0 表示有课程周次无法解析、已按全学期显示 */
        data class Done(val courseCount: Int, val fixedWeeks: Int = 0) : UiState
        data class Error(val message: String) : UiState
    }

    val state = MutableStateFlow<UiState>(UiState.Idle)

    private val repo = ScheduleRepository(db)

    /** 最近一次预览的原始课程（确认导入时写入） */
    private var pendingCourses: List<Course> = emptyList()

    /** 最近一次预览的考试（确认导入时随课表一起写入；为空则不动考试） */
    private var pendingExams: List<Exam> = emptyList()

    /** 确认导入进行中：写库未完成前禁止再次触发，防止双击并发执行两次 replaceAll */
    val isImporting = MutableStateFlow(false)

    /**
     * WebView 登录完成后回传的会话 Cookie → 抓课表页 → 预览差异。
     * 登录本身在 [CasLoginActivity] 的 WebView 内完成（教务系统拒绝非浏览器客户端）。
     */
    fun autoImportWithCookies(cookies: String) {
        if (cookies.isBlank()) {
            state.value = UiState.Error(context.getString(R.string.import_error_no_session));
            return
        }
        viewModelScope.launch {
            state.value = UiState.Loading(context.getString(R.string.import_loading_fetching))
            val result = withContext(Dispatchers.IO) {
                NjfuAdapter().fetchScheduleWithCookies(cookies)
            }
            result.onSuccess { courses ->
                showPreview(courses)
            }.onFailure { e ->
                state.value = UiState.Error(context.getString(R.string.import_error_fetch_failed, e.message))
            }
        }
    }

    /**
     * 广西大学 WebView 登录完成后回传的会话 Cookie → 抓课表 + 考试 JSON → 预览。
     * @param xnm 学年度（如 "2025"），@param xqm 学季代码（3/12/16），由导入向导随学校一起选定。
     */
    fun gxuImportWithCookies(cookies: String, xnm: String, xqm: String) {
        if (cookies.isBlank()) {
            state.value = UiState.Error(context.getString(R.string.import_error_no_session));
            return
        }
        viewModelScope.launch {
            state.value = UiState.Loading(context.getString(R.string.import_loading_fetching))
            val adapter = GxuAdapter()
            val result = withContext(Dispatchers.IO) {
                adapter.fetchScheduleWithCookies(cookies, xnm, xqm)
            }
            result.onSuccess { courses ->
                // 考试单独抓取，失败不阻断课表导入（仅提示）
                var exams = emptyList<Exam>()
                var examFailed = false
                val examResult = withContext(Dispatchers.IO) {
                    adapter.fetchExamsWithCookies(cookies, xnm, xqm)
                }
                examResult.onSuccess { exams = it }.onFailure { examFailed = true }
                showPreview(courses, exams, examFailed)
            }.onFailure { e ->
                state.value = UiState.Error(context.getString(R.string.import_error_fetch_failed, e.message))
            }
        }
    }

    fun manualJson(text: String) {
        viewModelScope.launch {
            try {
                val courses = withContext(Dispatchers.IO) { JsonImporter.import(text) }
                showPreview(courses)
            } catch (e: Exception) {
                state.value = UiState.Error(context.getString(R.string.import_error_json, e.message))
            }
        }
    }

    fun manualIcs(text: String) {
        viewModelScope.launch {
            try {
                val start = configuredSemesterStart()
                val courses = withContext(Dispatchers.IO) { IcsImporter.parse(text, start) }
                showPreview(courses)
            } catch (e: Exception) {
                state.value = UiState.Error(context.getString(R.string.import_error_ics, e.message))
            }
        }
    }

    fun manualExcel(uri: Uri) {
        viewModelScope.launch {
            try {
                val input = context.contentResolver.openInputStream(uri)
                if (input != null) {
                    input.use {
                        val courses = withContext(Dispatchers.IO) { ExcelImporter.parse(it) }
                        showPreview(courses)
                    }
                } else {
                    state.value = UiState.Error(context.getString(R.string.error_cannot_open_file))
                }
            } catch (e: Exception) {
                state.value = UiState.Error(context.getString(R.string.import_error_excel, e.message))
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
                        val courses = withContext(Dispatchers.IO) { NjfuXlsImporter.parse(it) }
                        if (courses.isEmpty()) {
                            state.value = UiState.Error(context.getString(R.string.import_error_no_courses_xls))
                        } else {
                            showPreview(courses)
                        }
                    }
                } else {
                    state.value = UiState.Error(context.getString(R.string.error_cannot_open_file))
                }
            } catch (e: Exception) {
                state.value = UiState.Error(context.getString(R.string.import_error_schedule, e.message))
            }
        }
    }

    /**
     * 解析 → 兜底周次 → 与现有课表对比 → 进入预览，等待用户确认。
     * DB 读取与 diff（CPU）在 Dispatchers.Default 执行，提交回主线程后更新状态。
     */
    private suspend fun showPreview(
        courses: List<Course>,
        exams: List<Exam> = emptyList(),
        examFailed: Boolean = false,
    ) {
        val (normalized, fixed) = withContext(Dispatchers.Default) { WeekUtils.fixMissingWeeks(courses) }
        if (normalized.isEmpty()) {
            state.value = UiState.Error(context.getString(R.string.import_error_no_courses_any))
            return
        }
        val existing = withContext(Dispatchers.Default) { db.courseDao().getAll().map { it.toModel() } }
        val diff = withContext(Dispatchers.Default) { ScheduleRepository.diff(existing, normalized) }
        pendingCourses = normalized
        pendingExams = exams
        state.value = UiState.Preview(diff, fixed, exams, examFailed)
    }

    /** 用户确认导入：以预览内容整体替换课表（含考试，若有）。防重入：进入即清空 pending，杜绝双击并发 */
    fun confirmImport() {
        if (isImporting.value) return
        // 读-清原子化：先取出本次要入库的内容并清空，再 launch 写库。
        // 即使写库未完成，后续 confirmImport 也无 pendingCourses 可用，天然防双击重入。
        val courses = pendingCourses
        val exams = pendingExams
        pendingCourses = emptyList()
        pendingExams = emptyList()
        if (courses.isEmpty()) {
            state.value = UiState.Error(context.getString(R.string.import_error_nothing_to_import))
            return
        }
        isImporting.value = true
        viewModelScope.launch {
            try {
                val (normalized, fixed) = withContext(Dispatchers.Default) { WeekUtils.fixMissingWeeks(courses) }
                withContext(Dispatchers.Default) { repo.replaceAll(normalized, exams) }
                state.value = UiState.Done(normalized.size, fixed)
            } finally {
                isImporting.value = false
            }
        }
    }

    fun cancelImport() {
        pendingCourses = emptyList()
        pendingExams = emptyList()
        state.value = UiState.Idle
    }

    fun reset() { state.value = UiState.Idle }

    /**
     * 设置页配置的学期起始日（ISO 日期）；未配置或格式非法返回 null。
     * 供广西大学导入向导推导 xnm/xqm，null 时用户需手动选择学期。
     */
    suspend fun configuredSemesterStart(): java.time.LocalDate? {
        val raw = db.settingsDao().get(SettingsKeys.SEMESTER_START) ?: return null
        return runCatching { java.time.LocalDate.parse(raw) }.getOrNull()
    }

    class Factory(private val db: AppDatabase, private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ImportViewModel(db, context) as T
    }
}
