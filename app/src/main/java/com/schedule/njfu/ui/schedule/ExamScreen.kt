package com.schedule.njfu.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schedule.njfu.model.Exam
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun ExamScreen(viewModel: ExamViewModel, onAdd: () -> Unit) {
    val exams by viewModel.exams.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Exam?>(null) }

    Column(Modifier.fillMaxSize()) {
        // 标题区 + 副标题（共 N 门 · 最近一场 X 天后）
        val nextDays = exams
            .mapNotNull { runCatching {
                ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(it.date))
            }.getOrNull() }
            .filter { it >= 0 }
            .minOrNull()
        val subtitle = when {
            exams.isEmpty() -> "暂无考试安排"
            nextDays == null -> "共 ${exams.size} 门"
            nextDays == 0L -> "共 ${exams.size} 门 · 最近一场就在今天"
            else -> "共 ${exams.size} 门 · 最近一场 $nextDays 天后"
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
            Text(
                "考试安排",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (exams.isEmpty()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "暂无考试安排",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(exams, key = { it.id }) { exam -> ExamRow(exam, onClick = { deleteTarget = exam }) }
            }
        }
        Button(
            onClick = { showDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(50),
        ) {
            Text("＋ 添加考试")
        }
    }

    if (showDialog) {
        ExamDialog(
            onSave = { exam ->
                viewModel.addExam(exam)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
    deleteTarget?.let { exam ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除考试") },
            text = { Text("确定删除「${exam.name}」的考试安排？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteExam(exam.id)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ExamRow(exam: Exam, onClick: () -> Unit) {
    // 临近 7 天（含今天）高亮，已过期置灰
    val days = runCatching {
        ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(exam.date))
    }.getOrDefault(Long.MAX_VALUE)
    val isUpcoming = days in 0..7
    val isPast = days < 0

    val colors = when {
        isUpcoming -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        )
        isPast -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        )
        else -> CardDefaults.cardColors()
    }
    val textColor = when {
        isUpcoming -> MaterialTheme.colorScheme.onPrimaryContainer
        isPast -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = colors,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                exam.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isUpcoming) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (exam.location.isBlank()) exam.date else "${exam.date}  ·  ${exam.location}",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun ExamDialog(onSave: (Exam) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    val dateValid = remember(date) { runCatching { LocalDate.parse(date.trim()) }.isSuccess }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加考试") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("考试名称") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("日期（yyyy-MM-dd）") },
                    singleLine = true,
                    isError = date.isNotBlank() && !dateValid,
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("地点") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(Exam(name = name.trim(), date = date.trim(), location = location.trim()))
                },
                enabled = name.isNotBlank() && dateValid,
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
