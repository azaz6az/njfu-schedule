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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import com.schedule.njfu.util.MiuiUtils
import com.schedule.njfu.widget.WidgetRefreshWorker
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val semesterStart by viewModel.semesterStart.collectAsStateWithLifecycle()
    val remindMinutes by viewModel.remindMinutes.collectAsStateWithLifecycle()
    val examRemindEnabled by viewModel.examRemindEnabled.collectAsStateWithLifecycle()
    val examRemindDays by viewModel.examRemindDays.collectAsStateWithLifecycle()
    val username by viewModel.username.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showDateDialog by remember { mutableStateOf(false) }
    var showPeriodDialog by remember { mutableStateOf(false) }
    var showHolidayDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var holidayShifts by remember { mutableStateOf<Map<LocalDate, Int>>(emptyMap()) }
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
    val debugLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) scope.launch {
            try {
                viewModel.exportDebugLog(uri)
                Toast.makeText(context, "调试日志已导出", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "日志导出失败：${e.message}", Toast.LENGTH_LONG).show()
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
        item {
            SettingRow("调休设置", "节假日调休日映射") {
                scope.launch {
                    holidayShifts = viewModel.loadShifts()
                    showHolidayDialog = true
                }
            }
        }

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
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("考试提醒", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "考试前提醒，默认提前 1 天与考试当天各提醒一次",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = examRemindEnabled,
                        onCheckedChange = { viewModel.saveExamRemindEnabled(it) },
                    )
                }
                if (examRemindEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "提前",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                            listOf(1, 2, 3, 7).forEachIndexed { index, days ->
                                SegmentedButton(
                                    selected = examRemindDays == days,
                                    onClick = { viewModel.saveExamRemindDays(days) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 4),
                                ) { Text(if (days == 1) "1 天" else "$days 天") }
                            }
                        }
                    }
                }
            }
        }

        item { SectionHeader("桌面小组件") }
        item { WidgetThemeSection() }
        item { Spacer(Modifier.height(8.dp)) }
        item { WidgetGuideSection() }
        item { Spacer(Modifier.height(8.dp)) }

        if (MiuiUtils.isMiui()) {
            item { SectionHeader("小米设备优化") }
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "MIUI 的后台限制可能延迟课前提醒与桌面小组件刷新，" +
                            "建议开启「自启动」并将省电策略设为「无限制」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(MiuiUtils.autostartSettingsIntent())
                            }.onFailure {
                                context.startActivity(MiuiUtils.appDetailsIntent(context))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50),
                    ) { Text("开启自启动权限") }
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(MiuiUtils.batterySettingsIntent())
                            }.onFailure {
                                context.startActivity(MiuiUtils.appDetailsIntent(context))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50),
                    ) { Text("允许后台运行（省电策略）") }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                WidgetRefreshWorker.refreshNow(context.applicationContext)
                                Toast.makeText(context, "小组件已刷新", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50),
                    ) { Text("立即刷新小组件") }
                }
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
                OutlinedButton(
                    onClick = { debugLogLauncher.launch("debug_log.txt") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                ) { Text("导出调试日志（排查问题用）") }
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
    if (showHolidayDialog) {
        HolidayShiftsDialog(
            initial = holidayShifts,
            onSave = {
                viewModel.saveShifts(it)
                holidayShifts = it
                showHolidayDialog = false
            },
            onDismiss = { showHolidayDialog = false },
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

private val ShiftDayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

/** 调休设置：把「某天」映射为「按周几显示」，课表与提醒同时生效 */
@Composable
private fun HolidayShiftsDialog(
    initial: Map<LocalDate, Int>,
    onSave: (Map<LocalDate, Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    var shifts by remember { mutableStateOf(initial) }
    var dateText by remember { mutableStateOf("") }
    var targetDay by remember { mutableStateOf(1) }
    val date = remember(dateText) { runCatching { LocalDate.parse(dateText.trim()) }.getOrNull() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调休设置") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "节假日调休后，有些周末需要按工作日上课（如国庆后周六补周一的课）。" +
                        "添加「日期 → 按周几显示」的映射，课表与课前提醒都会按映射生效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (shifts.isEmpty()) {
                    Text(
                        "暂无调休映射",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    shifts.toSortedMap().forEach { (d, day) ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${d.monthValue}月${d.dayOfMonth}日（${ShiftDayLabels[day - 1]}）按周${ShiftDayLabels[day - 1]}显示",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { shifts = shifts - d }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除")
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("日期（yyyy-MM-dd）") },
                    singleLine = true,
                    isError = dateText.isNotBlank() && date == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("按周", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                        (1..7).forEachIndexed { index, day ->
                            SegmentedButton(
                                selected = targetDay == day,
                                onClick = { targetDay = day },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 7),
                            ) { Text(ShiftDayLabels[day - 1]) }
                        }
                    }
                }
                TextButton(
                    onClick = {
                        if (date != null) {
                            shifts = shifts + (date to targetDay)
                            dateText = ""
                        }
                    },
                    enabled = date != null,
                ) { Text("添加映射") }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(shifts) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
