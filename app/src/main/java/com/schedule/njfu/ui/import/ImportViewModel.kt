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

    /**
     * WebView 登录完成后回传的会话 Cookie → 抓课表页 → 预览差异。
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
                showPreview(courses)
            }.onFailure { e ->
                state.value = UiState.Error("获取课表失败：${e.message}，可改用下方手动导入")
            }
        }
    }

    /**
     * 广西大学 WebView 登录完成后回传的会话 Cookie → 抓课表 + 考试 JSON → 预览。
     * @param xnm 学年度（如 "2025"），@param xqm 学季代码（3/12/16），由导入向导随学校一起选定。
     */
    fun gxuImportWithCookies(cookies: String, xnm: String, xqm: String) {
        if (cookies.isBlank()) {
            state.value = UiState.Error("未获取到登录会话，请重试或改用下方手动导入");
            return
        }
        viewModelScope.launch {
            state.value = UiState.Loading("正在获取课表…")
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
                state.value = UiState.Error("获取课表失败：${e.message}，可改用下方手动导入")
            }
        }
    }

    fun manualJson(text: String) {
        viewModelScope.launch {
            try {
                val courses = JsonImporter.import(text)
                showPreview(courses)
            } catch (e: Exception) {
                state.value = UiState.Error("JSON 解析失败：${e.message}")
            }
        }
    }

    fun manualIcs(text: String) {
        viewModelScope.launch {
            try {
                val courses = IcsImporter.parse(text)
                showPreview(courses)
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
                        showPreview(courses)
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
                            showPreview(courses)
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

    /** 解析 → 兜底周次 → 与现有课表对比 → 进入预览，等待用户确认 */
    private suspend fun showPreview(
        courses: List<Course>,
        exams: List<Exam> = emptyList(),
        examFailed: Boolean = false,
    ) {
        val (normalized, fixed) = WeekUtils.fixMissingWeeks(courses)
        if (normalized.isEmpty()) {
            state.value = UiState.Error("未解析到任何课程，请检查文件内容或教务系统是否改版")
            return
        }
        val existing = db.courseDao().getAll().map { it.toModel() }
        val diff = ScheduleRepository.diff(existing, normalized)
        pendingCourses = normalized
        pendingExams = exams
        state.value = UiState.Preview(diff, fixed, exams, examFailed)
    }

    /** 用户确认导入：以预览内容整体替换课表（含考试，若有） */
    fun confirmImport() {
        if (pendingCourses.isEmpty()) {
            state.value = UiState.Error("没有可导入的课程，请重新导入")
            return
        }
        viewModelScope.launch {
            val (normalized, fixed) = WeekUtils.fixMissingWeeks(pendingCourses)
            repo.replaceAll(normalized, pendingExams)
            pendingCourses = emptyList()
            pendingExams = emptyList()
            state.value = UiState.Done(normalized.size, fixed)
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
