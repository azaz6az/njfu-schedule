package com.schedule.njfu.ui.import

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.schedule.njfu.importer.gxu.GxuParser
import java.time.LocalDate

private const val PREFS_NAME = "prefs"
private const val KEY_LAST_SCHOOL = "last_school"

/** 从 SharedPreferences 读取上次选择的学校；无效值/未存回退南林 */
private fun schoolFromPrefs(prefs: SharedPreferences): School {
    val name = prefs.getString(KEY_LAST_SCHOOL, null) ?: return School.NJFU
    return School.entries.firstOrNull { it.name == name } ?: School.NJFU
}

/** 学校选择的 rememberSaveable Saver：保存时同步写 SharedPreferences */
private class SchoolPrefsSaver(private val prefs: SharedPreferences) : Saver<School, String> {
    override fun SaverScope.save(value: School): String {
        prefs.edit().putString(KEY_LAST_SCHOOL, value.name).apply()
        return value.name
    }

    override fun restore(value: String): School =
        School.entries.firstOrNull { it.name == value } ?: School.NJFU
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
    val schoolSaver = remember { SchoolPrefsSaver(prefs) }
    var selectedSchool by rememberSaveable(stateSaver = schoolSaver) {
        mutableStateOf(schoolFromPrefs(prefs))
    }

    // 广大学期选择（xnm 学年度 / xqm 学季 3=秋第1、12=春第2、16=夏第3）
    var xnm by remember { mutableStateOf("") }
    var xqm by remember { mutableStateOf("") }
    var showSemesterDialog by remember { mutableStateOf(false) }

    val casLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val cookies = result.data?.getStringExtra(CasLoginActivity.EXTRA_COOKIES).orEmpty()
            when (selectedSchool) {
                School.GXU -> viewModel.gxuImportWithCookies(cookies, xnm, xqm)
                School.NJFU -> viewModel.autoImportWithCookies(cookies)
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

    // 选广西大学时按设置里的学期起始日推导学期；未配置/推导失败则强制手动选择
    LaunchedEffect(selectedSchool) {
        if (selectedSchool != School.GXU) return@LaunchedEffect
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    School.entries.forEach { school ->
                        FilterChip(
                            selected = selectedSchool == school,
                            onClick = { selectedSchool = school },
                            label = { Text(school.label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.import_auto_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (selectedSchool == School.GXU) {
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
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val intent = Intent(context, CasLoginActivity::class.java)
                        intent.putExtra(CasLoginActivity.EXTRA_START_URL, selectedSchool.loginUrl)
                        intent.putStringArrayListExtra(
                            CasLoginActivity.EXTRA_SUCCESS_HOST_PREFIXES,
                            ArrayList(selectedSchool.successHostPrefixes),
                        )
                        intent.putExtra(
                            CasLoginActivity.EXTRA_SUCCESS_COOKIE_MARKER,
                            selectedSchool.successCookieMarker,
                        )
                        intent.putStringArrayListExtra(
                            CasLoginActivity.EXTRA_SUCCESS_URL_BLACKLIST,
                            ArrayList(selectedSchool.successUrlBlacklist),
                        )
                        // 空串 = 系统默认移动 UA（广西大学手机版登录页）；南林传桌面 UA
                        intent.putExtra(
                            CasLoginActivity.EXTRA_USER_AGENT,
                            selectedSchool.userAgent ?: "",
                        )
                        casLauncher.launch(intent)
                    },
                    enabled = !loading && (selectedSchool != School.GXU || (xnm.isNotEmpty() && xqm.isNotEmpty())),
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
