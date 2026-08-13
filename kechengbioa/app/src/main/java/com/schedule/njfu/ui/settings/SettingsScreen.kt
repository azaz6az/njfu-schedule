package com.schedule.njfu.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val semesterStart by viewModel.semesterStart.collectAsStateWithLifecycle()
    val remindMinutes by viewModel.remindMinutes.collectAsStateWithLifecycle()
    val username by viewModel.username.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showDateDialog by remember { mutableStateOf(false) }
    var showPeriodDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var periodTimes by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }

    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
                    ?: error("无法读取文件")
                val count = viewModel.importFromJson(text)
                Toast.makeText(context, "已导入 $count 门课程", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "JSON 导入失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    val icsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
                    ?: error("无法读取文件")
                val count = viewModel.importFromIcs(text)
                Toast.makeText(context, "已导入 $count 门课程", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "ICS 导入失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    val excelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            try {
                val count = viewModel.importFromExcel(uri)
                Toast.makeText(context, "已导入 $count 门课程", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Excel 导入失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            try {
                val count = viewModel.exportBackup(uri)
                Toast.makeText(context, "已导出 $count 门课程", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { SectionHeader("账号") }
        item { SettingRow("学号", username.ifBlank { "未登录" }) }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = { showLogoutConfirm = true },
                    enabled = username.isNotBlank(),
                    shape = RoundedCornerShape(50),
                ) { Text("退出登录") }
            }
        }

        item { SectionHeader("学期") }
        item { SettingRow("学期起始日期", semesterStart.toString()) { showDateDialog = true } }

        item { SectionHeader("节次时间段") }
        item {
            SettingRow("节次时间段", "点击设置") {
                scope.launch {
                    periodTimes = viewModel.loadPeriodTimes()
                    showPeriodDialog = true
                }
            }
        }

        item { SectionHeader("提醒") }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("提前提醒", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(5, 10, 15).forEachIndexed { index, minutes ->
                        SegmentedButton(
                            selected = remindMinutes == minutes,
                            onClick = { viewModel.saveRemindMinutes(minutes) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                        ) { Text("$minutes 分钟") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "课程开始前提醒上课。Android 12+ 需在系统设置中允许「闹钟与提醒」权限，" +
                        "Android 13+ 需授予通知权限，否则提醒可能不生效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { SectionHeader("数据") }
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { jsonLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                ) { Text("从 JSON 导入") }
                OutlinedButton(
                    onClick = { icsLauncher.launch(arrayOf("text/calendar")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                ) { Text("从 ICS 导入") }
                OutlinedButton(
                    onClick = {
                        excelLauncher.launch(
                            arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                ) { Text("从 Excel 导入") }
                OutlinedButton(
                    onClick = { exportLauncher.launch("schedule_backup.json") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                ) { Text("导出备份") }
                Button(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Text("清空课表数据") }
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("退出登录") },
            text = { Text("确定退出当前账号？已保存的课表数据不会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.logout()
                    showLogoutConfirm = false
                    Toast.makeText(context, "已退出登录", Toast.LENGTH_SHORT).show()
                }) { Text("退出") }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("取消") } },
        )
    }
    if (showDateDialog) {
        SemesterStartDialog(
            initial = semesterStart,
            onSave = {
                viewModel.saveSemesterStart(it)
                showDateDialog = false
            },
            onDismiss = { showDateDialog = false },
        )
    }
    if (showPeriodDialog) {
        PeriodSettingsDialog(
            times = periodTimes,
            onSave = {
                viewModel.savePeriodTimes(it)
                showPeriodDialog = false
            },
            onDismiss = { showPeriodDialog = false },
        )
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空课表数据") },
            text = { Text("将删除全部课程数据，此操作不可恢复。确定继续？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCourses()
                    showClearConfirm = false
                    Toast.makeText(context, "课表数据已清空", Toast.LENGTH_SHORT).show()
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(
        Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun SemesterStartDialog(
    initial: LocalDate,
    onSave: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial.toString()) }
    val date = remember(text) { runCatching { LocalDate.parse(text.trim()) }.getOrNull() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("学期起始日期") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("日期（yyyy-MM-dd）") },
                    singleLine = true,
                    isError = text.isNotBlank() && date == null,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "学期第一周的周一，用于计算当前教学周",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { date?.let(onSave) }, enabled = date != null) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
