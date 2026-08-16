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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            "解析到 ${diff.incomingSize} 门课程，与现有课表对比：",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text("• 新增 ${diff.added.size} 门", style = MaterialTheme.typography.bodyMedium)
        Text("• 删除 ${diff.removed.size} 门", style = MaterialTheme.typography.bodyMedium)
        Text("• 变更 ${diff.changed.size} 门", style = MaterialTheme.typography.bodyMedium)
        if (diff.unchanged.isNotEmpty()) {
            Text("• 不变 ${diff.unchanged.size} 门", style = MaterialTheme.typography.bodyMedium)
        }
        if (diff.conflicts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "⚠ 新数据中有 ${diff.conflicts.size} 处时间冲突（同一时间多门课），" +
                    "导入后同格课程将并排显示",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
fun ImportWizardScreen(viewModel: ImportViewModel, onDone: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
        Text("导入课程表", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "在应用内置浏览器登录教务系统即可自动导入；登录异常时可用教务导出的课表文件兜底。",
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
                Text("自动导入", style = MaterialTheme.typography.titleMedium)
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
                    "打开教务系统登录页，输入学号密码（如需验证码直接在页面内输入），登录成功后自动抓取本学期课表。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (selectedSchool == School.GXU) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val label = if (xnm.isNotEmpty() && xqm.isNotEmpty()) {
                            "将导入 ${GxuParser.semesterLabel(xnm, xqm)}"
                        } else {
                            "请选择学期"
                        }
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { showSemesterDialog = true }) { Text("修改") }
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
                ) { Text("登录教务系统并导入") }
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
                                "${s.fixedWeeks} 门课程周次信息缺失，将按全学期显示（可在课表里点开修正）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        if (s.examFailed) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "考试安排获取失败，本次仅导入课表",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.confirmImport() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(50),
                            ) { Text("确认导入") }
                            OutlinedButton(
                                onClick = { viewModel.cancelImport() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(50),
                            ) { Text("取消") }
                        }
                        Text(
                            "确认后将替换现有课表数据（建议先到设置页导出备份）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is ImportViewModel.UiState.Done -> Column {
                        val extra = if (s.fixedWeeks > 0) "（" + s.fixedWeeks + " 门周次信息缺失，已按全学期显示，可在课表里点开修正）" else ""
                        Text(
                            "成功导入 " + s.courseCount + " 门课程" + extra,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        TextButton(onClick = onDone) { Text("去课表看看") }
                    }
                    is ImportViewModel.UiState.Error -> Column {
                        Text(
                            s.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            "可检查网络后重试，或使用下方手动导入",
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
                Text("手动导入", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "教务系统网页端「学生个人课表」导出的课表文件；" +
                        "另支持 JSON（备份导出文件）、ICS（日历导出）和 Excel（xlsx）。",
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
                ) { Text("从教务导出的课表（.xls）导入") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showJsonDialog = true },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                ) { Text("从 JSON 导入") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showIcsDialog = true },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                ) { Text("从 ICS 导入") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        excelLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                ) { Text("从 Excel（xlsx）导入") }
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
            title = "从 JSON 导入",
            hint = "粘贴 JSON 备份内容（课程数组或完整备份对象）",
            onConfirm = { text ->
                viewModel.manualJson(text)
                showJsonDialog = false
            },
            onDismiss = { showJsonDialog = false },
        )
    }
    if (showIcsDialog) {
        PasteTextDialog(
            title = "从 ICS 导入",
            hint = "粘贴 ICS 日历内容（含 BEGIN:VEVENT 的文本）",
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
        "3" to "第 1 学期（秋）",
        "12" to "第 2 学期（春）",
        "16" to "第 3 学期（夏）",
    )
    var year by remember { mutableStateOf(currentXnm.ifEmpty { thisYear.toString() }) }
    var xqm by remember { mutableStateOf(currentXqm.ifEmpty { "3" }) }
    var yearOpen by remember { mutableStateOf(false) }
    var termOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择学期") },
        text = {
            Column {
                Text("学年", style = MaterialTheme.typography.labelMedium)
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
                Text("学期", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Box {
                    OutlinedButton(
                        onClick = { termOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(terms.firstOrNull { it.first == xqm }?.second ?: "请选择") }
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
            TextButton(onClick = { onConfirm(year, xqm) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
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
                label = { Text("内容") },
                placeholder = { Text(hint) },
                minLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
