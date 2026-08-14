package com.schedule.njfu.ui.import

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ImportWizardScreen(viewModel: ImportViewModel, onDone: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loading = state is ImportViewModel.UiState.Loading
    val context = LocalContext.current

    var showJsonDialog by remember { mutableStateOf(false) }
    var showIcsDialog by remember { mutableStateOf(false) }

    val casLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val cookies = result.data?.getStringExtra(CasLoginActivity.EXTRA_COOKIES).orEmpty()
            viewModel.autoImportWithCookies(cookies)
        }
    }

    val excelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.manualExcel(uri) }

    // 教务系统导出的「学生个人课表.xls」（老式 .xls）
    val xlsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.manualNjfuXls(uri) }

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
                Spacer(Modifier.height(4.dp))
                Text(
                    "打开教务系统登录页，输入学号密码（如需验证码直接在页面内输入），登录成功后自动抓取本学期课表。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { casLauncher.launch(Intent(context, CasLoginActivity::class.java)) },
                    enabled = !loading,
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
                    "推荐：教务系统网页端「学生个人课表」导出 .xls 文件后导入；" +
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
