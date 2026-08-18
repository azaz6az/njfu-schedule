package com.schedule.njfu.ui.import

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schedule.njfu.R
import com.schedule.njfu.data.ImportDiff
import com.schedule.njfu.importer.School
import com.schedule.njfu.importer.ZfJwglxtConfig
import com.schedule.njfu.importer.gxu.GxuParser
import java.time.LocalDate

private const val PREFS_NAME = "prefs"
private const val KEY_LAST_SCHOOL = "last_school"
private const val KEY_CUSTOM_JW_URL = "custom_jw_url"
private const val CUSTOM_MARKER = "__CUSTOM__"

/**
 * 导入页的学校选择：内置学校（[School] 枚举）或自定义正方教务（用户填登录页 URL）。
 * 自定义用于覆盖未预置但使用正方 jwglxt 新版教务的高校（如佛山大学等），
 * 是实现「适配很多学校」的万能兜底。
 */
private sealed interface SchoolChoice {
    data class BuiltIn(val school: School) : SchoolChoice

    data object Custom : SchoolChoice
}

/** 从 SharedPreferences 读取上次选择的学校；无效值/未存回退南林 */
private fun schoolChoiceFromPrefs(prefs: SharedPreferences): SchoolChoice {
    val name = prefs.getString(KEY_LAST_SCHOOL, null) ?: return SchoolChoice.BuiltIn(School.NJFU)
    if (name == CUSTOM_MARKER) return SchoolChoice.Custom
    return SchoolChoice.BuiltIn(School.entries.firstOrNull { it.name == name } ?: School.NJFU)
}

/** 学校选择的 rememberSaveable Saver：保存时同步写 SharedPreferences */
private class SchoolChoicePrefsSaver(private val prefs: SharedPreferences) : Saver<SchoolChoice, String> {
    override fun SaverScope.save(value: SchoolChoice): String {
        val stored = when (value) {
            is SchoolChoice.BuiltIn -> value.school.name
            SchoolChoice.Custom -> CUSTOM_MARKER
        }
        prefs.edit().putString(KEY_LAST_SCHOOL, stored).apply()
        return stored
    }

    override fun restore(value: String): SchoolChoice = schoolChoiceFromPrefs(prefs)
}

/** 导入差异摘要：新增/删除/变更/不变 + 冲突警告 */
@Composable
private fun ImportDiffSummary(diff: ImportDiff) {
    Column {
        Text(
            stringResource(R.string.import_diff_summary, diff.incomingSize),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.import_diff_added, diff.added.size), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.import_diff_removed, diff.removed.size), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.import_diff_changed, diff.changed.size), style = MaterialTheme.typography.bodyMedium)
        if (diff.unchanged.isNotEmpty()) {
            Text(stringResource(R.string.import_diff_unchanged, diff.unchanged.size), style = MaterialTheme.typography.bodyMedium)
        }
        if (diff.conflicts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.import_diff_conflict, diff.conflicts.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportWizardScreen(viewModel: ImportViewModel, onDone: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val loading = state is ImportViewModel.UiState.Loading
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var showJsonDialog by remember { mutableStateOf(false) }
    var showIcsDialog by remember { mutableStateOf(false) }

    // 学校选择：跨配置变更/进程重建由 SharedPreferences 兜底恢复
    val schoolSaver = remember { SchoolChoicePrefsSaver(prefs) }
    var selectedChoice by rememberSaveable(stateSaver = schoolSaver) {
        mutableStateOf(schoolChoiceFromPrefs(prefs))
    }

    // 自定义正方教务地址（登录页 URL），保存在 SharedPreferences 供下次使用
    var customJwUrl by remember { mutableStateOf(prefs.getString(KEY_CUSTOM_JW_URL, null)) }
    var showCustomUrlDialog by remember { mutableStateOf(false) }

    /** 当前选择的教务配置：内置正方学校取其配置；自定义取用户填写并解析出的配置 */
    val zfConfig: ZfJwglxtConfig? = when (val c = selectedChoice) {
        is SchoolChoice.BuiltIn -> c.school.zfJwglxt
        SchoolChoice.Custom -> customJwUrl?.let { ZfJwglxtConfig.fromLoginUrl(it) }
    }

    // 正方学校学期选择（xnm 学年度 / xqm 学季 3=秋第1、12=春第2、16=夏第3）
    var xnm by remember { mutableStateOf("") }
    var xqm by remember { mutableStateOf("") }
    var showSemesterDialog by remember { mutableStateOf(false) }

    val casLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val cookies = result.data?.getStringExtra(CasLoginActivity.EXTRA_COOKIES).orEmpty()
            val cfg = zfConfig
            if (cfg != null) {
                viewModel.zfImportWithCookies(cookies, cfg, xnm, xqm)
            } else {
                viewModel.autoImportWithCookies(cookies)
            }
        }
    }

    val excelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.manualExcel(uri) }

    // 教务系统导出的「学生个人课表.xls」（老式 .xls）
    val xlsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.manualNjfuXls(uri) }

    // 选正方学校（广西大学/广东海洋大学/自定义）时按设置里的学期起始日推导学期；
    // 未配置/推导失败则强制手动选择
    LaunchedEffect(selectedChoice, zfConfig) {
        if (zfConfig == null) return@LaunchedEffect
        val start = viewModel.configuredSemesterStart()
        val semester = GxuParser.deriveSemester(start)
        if (semester != null) {
            xnm = semester.first
            xqm = semester.second
        } else {
            showSemesterDialog = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.import_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.import_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        // ---- 自动导入 ----
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.import_auto), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    School.entries.forEach { school ->
                        FilterChip(
                            selected = selectedChoice == SchoolChoice.BuiltIn(school),
                            onClick = { selectedChoice = SchoolChoice.BuiltIn(school) },
                            label = { Text(school.label) },
                        )
                    }
                    // 自定义正方教务：未预置的正方学校（如佛山大学）填登录页地址即可导入
                    FilterChip(
                        selected = selectedChoice == SchoolChoice.Custom,
                        onClick = {
                            selectedChoice = SchoolChoice.Custom
                            if (customJwUrl.isNullOrBlank()) showCustomUrlDialog = true
                        },
                        label = { Text(stringResource(R.string.import_custom_url_title)) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.import_auto_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (zfConfig != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val label = if (xnm.isNotEmpty() && xqm.isNotEmpty()) {
                            stringResource(R.string.import_will_import, GxuParser.semesterLabel(xnm, xqm))
                        } else {
                            stringResource(R.string.import_select_semester)
                        }
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { showSemesterDialog = true }) { Text(stringResource(R.string.import_modify)) }
                    }
                    if (selectedChoice == SchoolChoice.Custom) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (customJwUrl.isNullOrBlank()) stringResource(R.string.import_custom_url_not_set)
                                else customJwUrl!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { showCustomUrlDialog = true }) {
                                Text(stringResource(R.string.import_custom_url_edit))
                            }
                        }
                        if (customJwUrl.isNullOrBlank() || ZfJwglxtConfig.fromLoginUrl(customJwUrl!!) == null) {
                            Text(
                                stringResource(R.string.import_custom_url_required_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val cfg = zfConfig
                        val intent = Intent(context, CasLoginActivity::class.java)
                        if (cfg != null) {
                            // 正方 jwglxt：登录页/成功判定/会话标记/UA 全部由配置推导
                            intent.putExtra(CasLoginActivity.EXTRA_START_URL, cfg.loginUrl)
                            intent.putStringArrayListExtra(
                                CasLoginActivity.EXTRA_SUCCESS_HOST_PREFIXES,
                                arrayListOf(cfg.baseUrl),
                            )
                            intent.putExtra(CasLoginActivity.EXTRA_SUCCESS_COOKIE_MARKER, "JSESSIONID")
                            intent.putStringArrayListExtra(
                                CasLoginActivity.EXTRA_SUCCESS_URL_BLACKLIST,
                                arrayListOf("login_slogin"),
                            )
                            // 空串 = 系统默认移动 UA（正方 jwglxt 手机版自适应登录页）
                            intent.putExtra(CasLoginActivity.EXTRA_USER_AGENT, "")
                        } else {
                            val school = (selectedChoice as SchoolChoice.BuiltIn).school
                            intent.putExtra(CasLoginActivity.EXTRA_START_URL, school.loginUrl)
                            intent.putStringArrayListExtra(
                                CasLoginActivity.EXTRA_SUCCESS_HOST_PREFIXES,
                                ArrayList(school.successHostPrefixes),
                            )
                            intent.putExtra(
                                CasLoginActivity.EXTRA_SUCCESS_COOKIE_MARKER,
                                school.successCookieMarker,
                            )
                            intent.putStringArrayListExtra(
                                CasLoginActivity.EXTRA_SUCCESS_URL_BLACKLIST,
                                ArrayList(school.successUrlBlacklist),
                            )
                            // 空串 = 系统默认移动 UA；南林 CAS 走桌面 UA（由学校枚举指定）
                            intent.putExtra(
                                CasLoginActivity.EXTRA_USER_AGENT,
                                school.userAgent ?: "",
                            )
                        }
                        casLauncher.launch(intent)
                    },
                    enabled = !loading
                        && (zfConfig == null || (xnm.isNotEmpty() && xqm.isNotEmpty()))
                        && (selectedChoice is SchoolChoice.BuiltIn || zfConfig != null),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                ) { Text(stringResource(R.string.import_login_and_import)) }
                Spacer(Modifier.height(12.dp))
                when (val s = state) {
                    is ImportViewModel.UiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(s.stage, style = MaterialTheme.typography.bodyMedium)
                    }
                    is ImportViewModel.UiState.Preview -> Column {
                        ImportDiffSummary(s.diff)
                        if (s.fixedWeeks > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.import_fixed_weeks, s.fixedWeeks),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        if (s.examFailed) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.import_exam_failed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.confirmImport() },
                                // 写库进行中禁用，防止双击并发执行两次 replaceAll
                                enabled = !isImporting,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(50),
                            ) {
                                Text(
                                    if (isImporting) stringResource(R.string.import_importing)
                                    else stringResource(R.string.import_confirm),
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.cancelImport() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(50),
                            ) { Text(stringResource(R.string.action_cancel)) }
                        }
                        Text(
                            stringResource(R.string.import_confirm_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is ImportViewModel.UiState.Done -> Column {
                        val extra = if (s.fixedWeeks > 0) {
                            stringResource(R.string.import_success_fixed_weeks_suffix, s.fixedWeeks)
                        } else {
                            ""
                        }
                        Text(
                            stringResource(R.string.import_success, s.courseCount) + extra,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        TextButton(onClick = onDone) { Text(stringResource(R.string.import_goto_schedule)) }
                    }
                    is ImportViewModel.UiState.Error -> Column {
                        Text(
                            s.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            stringResource(R.string.import_error_suggestion),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ImportViewModel.UiState.Idle -> Unit
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- 手动导入 ----
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.import_manual), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.import_manual_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        xlsLauncher.launch(arrayOf("application/vnd.ms-excel", "*/*"))
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                ) { Text(stringResource(R.string.import_from_xls)) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showJsonDialog = true },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                ) { Text(stringResource(R.string.import_from_json)) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showIcsDialog = true },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                ) { Text(stringResource(R.string.import_from_ics)) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        excelLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                ) { Text(stringResource(R.string.import_from_excel_xlsx)) }
            }
        }
    }

    if (showCustomUrlDialog) {
        CustomJwUrlDialog(
            current = customJwUrl.orEmpty(),
            onConfirm = { text ->
                val trimmed = text.trim()
                if (ZfJwglxtConfig.fromLoginUrl(trimmed) != null) {
                    customJwUrl = trimmed
                    prefs.edit().putString(KEY_CUSTOM_JW_URL, trimmed).apply()
                    showCustomUrlDialog = false
                }
            },
            onDismiss = { showCustomUrlDialog = false },
        )
    }
    if (showSemesterDialog) {
        SemesterDialog(
            currentXnm = xnm,
            currentXqm = xqm,
            onConfirm = { newXnm, newXqm ->
                xnm = newXnm
                xqm = newXqm
                showSemesterDialog = false
            },
            onDismiss = { showSemesterDialog = false },
        )
    }
    if (showJsonDialog) {
        PasteTextDialog(
            title = stringResource(R.string.import_from_json),
            hint = stringResource(R.string.import_paste_json_hint),
            onConfirm = { text ->
                viewModel.manualJson(text)
                showJsonDialog = false
            },
            onDismiss = { showJsonDialog = false },
        )
    }
    if (showIcsDialog) {
        PasteTextDialog(
            title = stringResource(R.string.import_from_ics),
            hint = stringResource(R.string.import_paste_ics_hint),
            onConfirm = { text ->
                viewModel.manualIcs(text)
                showIcsDialog = false
            },
            onDismiss = { showIcsDialog = false },
        )
    }
}

/**
 * 广西大学学期选择对话框：学年（当前年前后各 2 年）+ 学期（秋第1/春第2/夏第3）。
 * 确认后回传 xnm/xqm。
 */
@Composable
private fun SemesterDialog(
    currentXnm: String,
    currentXqm: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val thisYear = LocalDate.now().year
    val years = (thisYear - 2..thisYear + 2).toList()
    val terms = listOf(
        "3" to stringResource(R.string.import_term_1),
        "12" to stringResource(R.string.import_term_2),
        "16" to stringResource(R.string.import_term_3),
    )
    var year by remember { mutableStateOf(currentXnm.ifEmpty { thisYear.toString() }) }
    var xqm by remember { mutableStateOf(currentXqm.ifEmpty { "3" }) }
    var yearOpen by remember { mutableStateOf(false) }
    var termOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_semester_title)) },
        text = {
            Column {
                Text(stringResource(R.string.import_semester_year), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Box {
                    OutlinedButton(
                        onClick = { yearOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("$year - ${year.toInt() + 1}") }
                    DropdownMenu(expanded = yearOpen, onDismissRequest = { yearOpen = false }) {
                        years.forEach { y ->
                            DropdownMenuItem(
                                text = { Text("$y - ${y + 1}") },
                                onClick = { year = y.toString(); yearOpen = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.import_semester_term), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Box {
                    OutlinedButton(
                        onClick = { termOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            terms.firstOrNull { it.first == xqm }?.second
                                ?: stringResource(R.string.import_term_placeholder),
                        )
                    }
                    DropdownMenu(expanded = termOpen, onDismissRequest = { termOpen = false }) {
                        terms.forEach { (code, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { xqm = code; termOpen = false },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(year, xqm) }) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * 自定义正方教务学校：用户粘贴教务系统登录页地址（教学管理信息服务平台，
 * 支持 /jwglxt/xtgl/login_slogin.html 或根路径 /xtgl/login_slogin.html 两种部署）。
 * 确认前本地校验格式，校验通过才保存，避免无效地址进入登录流程。
 */
@Composable
private fun CustomJwUrlDialog(
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(current) }
    val valid = ZfJwglxtConfig.fromLoginUrl(text.trim()) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_custom_url_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.import_custom_url_label)) },
                    placeholder = { Text(stringResource(R.string.import_custom_url_hint)) },
                    minLines = 2,
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (text.isNotBlank() && !valid) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.import_custom_url_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = valid) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun PasteTextDialog(
    title: String,
    hint: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.import_paste_content_label)) },
                placeholder = { Text(hint) },
                minLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.import_paste_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
