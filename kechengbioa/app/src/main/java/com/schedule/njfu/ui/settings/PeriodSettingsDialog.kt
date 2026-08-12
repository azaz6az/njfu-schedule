package com.schedule.njfu.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.schedule.njfu.reminder.ReminderScheduler

/** HH:mm（如 08:00、21:30） */
private val TimePattern = Regex("^([01]?\\d|2[0-3]):[0-5]\\d$")

@Composable
fun PeriodSettingsDialog(
    times: List<Pair<Int, String>>,
    onSave: (List<Pair<Int, String>>) -> Unit,
    onDismiss: () -> Unit,
) {
    // 固定按默认 10 节展示；未保存过的节次回退默认时间
    val initial = remember(times) {
        ReminderScheduler.defaultPeriodTimes().map { d ->
            times.find { it.first == d.first }?.second ?: d.second
        }
    }
    var values by remember(times) { mutableStateOf(initial) }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("节次时间段") },
        text = {
            Column(
                Modifier
                    .height(400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                values.forEachIndexed { index, value ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "第 ${index + 1} 节",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(64.dp),
                        )
                        OutlinedTextField(
                            value = value,
                            onValueChange = { v ->
                                values = values.mapIndexed { i, old -> if (i == index) v else old }
                                error = false
                            },
                            singleLine = true,
                            isError = error && !TimePattern.matches(value.trim()),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (error) {
                    Text(
                        "时间格式须为 HH:mm（如 08:00、21:30），请修正后保存",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (values.all { TimePattern.matches(it.trim()) }) {
                        onSave(values.mapIndexed { i, v -> (i + 1) to v.trim() })
                    } else {
                        error = true
                    }
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
