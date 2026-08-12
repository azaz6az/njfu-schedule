package com.schedule.njfu.ui.import

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ImportWizardScreen(viewModel: ImportViewModel, onDone: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loggedInUsername by viewModel.loggedInUsername.collectAsStateWithLifecycle()
    val loading = state is ImportViewModel.UiState.Loading

    var studentId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showJsonDialog by remember { mutableStateOf(false) }
    var showIcsDialog by remember { mutableStateOf(false) }

    val excelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.manualExcel(uri) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("导入课程表", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "通过教务系统账号自动同步课表；登录异常时可用手动导入兜底。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (loggedInUsername.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "已登录：学号 $loggedInUsername，可重新导入",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(16.dp))

        // ---- 自动导入 ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("自动导入", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "使用学号密码登录南林教务系统（CAS），自动导入当前学期课表",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = studentId,
                    onValueChange = { studentId = it },
                    label = { Text("学号") },
                    singleLine = true,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    enabled = !loading,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(if (passwordVisible) "隐藏" else "显示")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.autoImport(studentId.trim(), password) },
                    enabled = !loading && studentId.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("开始导入") }
                Spacer(Modifier.height(12.dp))
                when (val s = state) {
                    is ImportViewModel.UiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(s.stage, style = MaterialTheme.typography.bodyMedium)
                    }
                    is ImportViewModel.UiState.Done -> Column {
                        Text(
                            "成功导入 ${s.courseCount} 门课程",
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
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("手动导入", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "教务系统改版或遇到验证码时，可使用手动导入兜底。" +
                        "支持 JSON（备份导出文件）、ICS（日历导出）和 Excel（xlsx）格式。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showJsonDialog = true },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("从 JSON 导入") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showIcsDialog = true },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("从 ICS 导入") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        excelLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("从 Excel 导入") }
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
