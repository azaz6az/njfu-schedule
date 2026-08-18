package com.schedule.njfu.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schedule.njfu.R
import com.schedule.njfu.ui.weekdayName
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
    val widgetTheme by viewModel.widgetTheme.collectAsStateWithLifecycle()
    val rowHeight by viewModel.rowHeight.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var notificationsEnabled by remember { mutableStateOf(false) }
    var canScheduleExact by remember { mutableStateOf(true) }
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
                    ?: error(context.getString(R.string.error_cannot_read_file))
                val count = viewModel.importFromJson(text)
                Toast.makeText(context, context.getString(R.string.settings_imported_count, count), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.settings_import_json_failed, e.message), Toast.LENGTH_LONG).show()
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
                    ?: error(context.getString(R.string.error_cannot_read_file))
                val count = viewModel.importFromIcs(text)
                Toast.makeText(context, context.getString(R.string.settings_imported_count, count), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.settings_import_ics_failed, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }
    val excelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            try {
                val count = viewModel.importFromExcel(uri)
                Toast.makeText(context, context.getString(R.string.settings_imported_count, count), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.settings_import_excel_failed, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            try {
                val count = viewModel.exportBackup(uri)
                Toast.makeText(context, context.getString(R.string.settings_exported_count, count), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.settings_export_failed, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }
    val debugLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) scope.launch {
            try {
                viewModel.exportDebugLog(uri)
                Toast.makeText(context, context.getString(R.string.settings_debug_log_exported), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.settings_debug_log_failed, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    // 进入页面时（及每次回到前台时，例如从系统设置返回）重新检查通知与精确闹钟授权状态
    fun refreshPermissionFlags() {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        canScheduleExact =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(android.app.AlarmManager::class.java)
                    .canScheduleExactAlarms()
            } else true
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val owner = lifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissionFlags()
        }
        owner.lifecycle.addObserver(observer)
        refreshPermissionFlags()
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { SectionHeader(stringResource(R.string.settings_account)) }
        item {
            SettingRow(
                stringResource(R.string.settings_student_id),
                username.ifBlank { context.getString(R.string.settings_not_logged_in) },
            )
        }
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
                ) { Text(stringResource(R.string.settings_logout)) }
            }
        }

        item { SectionHeader(stringResource(R.string.settings_semester)) }
        item {
            SettingRow(
                stringResource(R.string.settings_semester_start),
                semesterStart.toString(),
            ) { showDateDialog = true }
        }
        item {
            SettingRow(
                stringResource(R.string.settings_shift_settings),
                stringResource(R.string.settings_shift_settings_subtitle),
            ) {
                scope.launch {
                    holidayShifts = viewModel.loadShifts()
                    showHolidayDialog = true
                }
            }
        }

        item { SectionHeader(stringResource(R.string.settings_schedule_display)) }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.settings_grid_row_height), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(48, 56, 64).forEachIndexed { index, h ->
                        SegmentedButton(
                            selected = rowHeight == h,
                            onClick = { viewModel.saveRowHeight(h) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                        ) { Text(stringResource(R.string.settings_row_height_value, h)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_grid_row_height_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { SectionHeader(stringResource(R.string.settings_period_times)) }
        item {
            SettingRow(
                stringResource(R.string.settings_period_times),
                stringResource(R.string.settings_period_times_hint),
            ) {
                scope.launch {
                    periodTimes = viewModel.loadPeriodTimes()
                    showPeriodDialog = true
                }
            }
        }

        item { SectionHeader(stringResource(R.string.settings_remind)) }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                // 通知权限未开启时引导
                if (!notificationsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionBanner(
                        message = stringResource(R.string.settings_remind_notification_permission),
                        buttonText = stringResource(R.string.settings_permission_enable),
                        onClick = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            runCatching { context.startActivity(intent) }
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                }
                // 精确闹钟（Android 12+）未授权时引导
                if (!canScheduleExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PermissionBanner(
                        message = stringResource(R.string.settings_exact_alarm_permission),
                        buttonText = stringResource(R.string.settings_permission_go_settings),
                        onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(Uri.parse("package:${context.packageName}"))
                            runCatching { context.startActivity(intent) }
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Text(stringResource(R.string.settings_remind_before), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(5, 10, 15).forEachIndexed { index, minutes ->
                        SegmentedButton(
                            selected = remindMinutes == minutes,
                            onClick = { viewModel.saveRemindMinutes(minutes) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                        ) { Text(stringResource(R.string.settings_minutes, minutes)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_remind_desc),
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
                        Text(stringResource(R.string.settings_exam_remind), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.settings_exam_remind_desc),
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
                            stringResource(R.string.settings_exam_advance),
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
                                ) {
                                    Text(
                                        if (days == 1) stringResource(R.string.settings_days_one)
                                        else stringResource(R.string.settings_days, days),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.settings_widgets)) }
        item { WidgetThemeSection(selected = widgetTheme, onSelect = viewModel::saveWidgetTheme) }
        item { Spacer(Modifier.height(8.dp)) }
        item { WidgetGuideSection() }
        item { Spacer(Modifier.height(8.dp)) }

        if (MiuiUtils.isMiui()) {
            item { SectionHeader(stringResource(R.string.settings_miui)) }
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.settings_miui_desc),
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
                    ) { Text(stringResource(R.string.settings_miui_autostart)) }
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
                    ) { Text(stringResource(R.string.settings_miui_battery)) }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                WidgetRefreshWorker.refreshNow(context.applicationContext)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.settings_widget_refreshed),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50),
                    ) { Text(stringResource(R.string.settings_widget_refresh_now)) }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.settings_data)) }
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
                ) { Text(stringResource(R.string.import_from_json)) }
                OutlinedButton(
                    onClick = { icsLauncher.launch(arrayOf("text/calendar")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                ) { Text(stringResource(R.string.import_from_ics)) }
                OutlinedButton(
                    onClick = {
                        excelLauncher.launch(
                            arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                ) { Text(stringResource(R.string.settings_import_excel)) }
                OutlinedButton(
                    onClick = { exportLauncher.launch("schedule_backup.json") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                ) { Text(stringResource(R.string.settings_export_backup)) }
                OutlinedButton(
                    onClick = { debugLogLauncher.launch("debug_log.txt") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                ) { Text(stringResource(R.string.settings_export_debug_log)) }
                Button(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Text(stringResource(R.string.settings_clear_data)) }
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(stringResource(R.string.settings_logout)) },
            text = { Text(stringResource(R.string.settings_logout_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.logout()
                    showLogoutConfirm = false
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_logged_out),
                        Toast.LENGTH_SHORT,
                    ).show()
                }) { Text(stringResource(R.string.settings_logout_confirm_button)) }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text(stringResource(R.string.action_cancel)) } },
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
            title = { Text(stringResource(R.string.settings_clear_data)) },
            text = { Text(stringResource(R.string.settings_clear_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCourses()
                    showClearConfirm = false
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_data_cleared),
                        Toast.LENGTH_SHORT,
                    ).show()
                }) { Text(stringResource(R.string.settings_clear_confirm_button)) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text(stringResource(R.string.action_cancel)) } },
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

/** 权限引导横幅：提醒区块内，未授权时展示提示文案 + 跳转系统设置的按钮 */
@Composable
private fun PermissionBanner(
    message: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
        ) { Text(buttonText) }
    }
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
        title = { Text(stringResource(R.string.settings_semester_start)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.input_date_label)) },
                    singleLine = true,
                    isError = text.isNotBlank() && date == null,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_semester_start_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { date?.let(onSave) }, enabled = date != null) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

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
        title = { Text(stringResource(R.string.settings_shift_settings)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.settings_shift_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (shifts.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_shift_empty),
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
                                stringResource(
                                    R.string.holiday_shift_line,
                                    d.monthValue,
                                    d.dayOfMonth,
                                    weekdayName(day),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { shifts = shifts - d }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text(stringResource(R.string.input_date_label)) },
                    singleLine = true,
                    isError = dateText.isNotBlank() && date == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_shift_by_week), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                        (1..7).forEachIndexed { index, day ->
                            SegmentedButton(
                                selected = targetDay == day,
                                onClick = { targetDay = day },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 7),
                            ) { Text(weekdayName(day)) }
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
                ) { Text(stringResource(R.string.settings_shift_add_mapping)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(shifts) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
