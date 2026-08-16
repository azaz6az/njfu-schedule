package com.schedule.njfu.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.schedule.njfu.R
import com.schedule.njfu.data.CourseMapper
import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils
import com.schedule.njfu.ui.weekdayName

/**
 * 课程详情/编辑（course != null）与加课（course == null）对话框。
 * 保存时构造 source="manual" 的 Course 并回调 onSave；编辑模式回调 onDelete(id)。
 */
@Composable
fun CourseDialog(
    course: Course?,
    weekDay: Int,
    weekNumber: Int,
    onSave: (Course) -> Unit,
    onDelete: ((Long) -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember(course) { mutableStateOf(course?.name ?: "") }
    var teacher by remember(course) { mutableStateOf(course?.teacher ?: "") }
    var location by remember(course) { mutableStateOf(course?.location ?: "") }
    var day by remember(course, weekDay) { mutableStateOf(course?.dayOfWeek ?: weekDay.coerceIn(1, 7)) }
    var startPeriod by remember(course) { mutableStateOf(course?.startPeriod?.toString() ?: "1") }
    var endPeriod by remember(course) { mutableStateOf(course?.endPeriod?.toString() ?: "1") }
    var weeksText by remember(course, weekNumber) {
        mutableStateOf(course?.let { maskToText(it.weeks) } ?: weekNumber.toString())
    }
    var dayExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    fun save() {
        val n = name.trim()
        val s = startPeriod.trim().toIntOrNull()
        val e = endPeriod.trim().toIntOrNull()
        val mask = parseWeeksInput(weeksText)
        when {
            n.isEmpty() -> error = context.getString(R.string.course_error_name_empty)
            day !in 1..7 -> error = context.getString(R.string.course_error_day)
            s == null || s !in 1..WeekGrid.MAX_PERIODS ->
                error = context.getString(R.string.course_error_start_period, WeekGrid.MAX_PERIODS)
            e == null || e < s || e > WeekGrid.MAX_PERIODS ->
                error = context.getString(R.string.course_error_end_period, s, WeekGrid.MAX_PERIODS)
            mask == 0 -> error = context.getString(R.string.course_error_weeks)
            else -> onSave(
                Course(
                    id = course?.id ?: 0,
                    name = n,
                    teacher = teacher.trim(),
                    location = location.trim(),
                    dayOfWeek = day,
                    startPeriod = s,
                    endPeriod = e,
                    weeks = mask,
                    color = CourseMapper.colorFor(n),
                    source = "manual",
                    note = course?.note ?: "",
                )
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (course == null) stringResource(R.string.course_add_title) else stringResource(R.string.course_detail_title)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.course_name_label)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = teacher, onValueChange = { teacher = it },
                    label = { Text(stringResource(R.string.course_teacher_label)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = location, onValueChange = { location = it },
                    label = { Text(stringResource(R.string.course_location_label)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // 星期选择
                Column(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = stringResource(R.string.weekday_with_prefix, weekdayName(day)),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.course_day_label)) },
                        trailingIcon = { Text("▼") },
                        // clickable 直接作用于输入框（readOnly 会消费点击，放在父容器上会失效）
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dayExpanded = true }
                            .semantics {
                                role = Role.DropdownList
                                contentDescription = context.getString(R.string.course_day_select)
                            },
                    )
                    DropdownMenu(expanded = dayExpanded, onDismissRequest = { dayExpanded = false }) {
                        (1..7).forEach { d ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.weekday_with_prefix, weekdayName(d))) },
                                onClick = { day = d; dayExpanded = false },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startPeriod, onValueChange = { startPeriod = it },
                        label = { Text(stringResource(R.string.course_start_period_label)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = endPeriod, onValueChange = { endPeriod = it },
                        label = { Text(stringResource(R.string.course_end_period_label)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = weeksText, onValueChange = { weeksText = it },
                    label = { Text(stringResource(R.string.course_weeks_label)) },
                    supportingText = { Text(stringResource(R.string.course_weeks_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { save() }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (course != null && course.id > 0 && onDelete != null) {
                    TextButton(onClick = {
                        onDelete(course.id)
                        onDismiss()
                    }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

/** 周次掩码 → "1-16" / "1,3,5" 文本（编辑回填用） */
private fun maskToText(mask: Int): String {
    val weeks = (1..WeekUtils.MAX_WEEKS).filter { WeekUtils.contains(mask, it) }
    if (weeks.isEmpty()) return ""
    val parts = mutableListOf<String>()
    var start = weeks[0]
    var prev = weeks[0]
    for (w in weeks.drop(1)) {
        if (w == prev + 1) {
            prev = w
        } else {
            parts += if (start == prev) "$start" else "$start-$prev"
            start = w
            prev = w
        }
    }
    parts += if (start == prev) "$start" else "$start-$prev"
    return parts.joinToString(",")
}

/** "1-16"、"1,3,5"、"单周"、"双周"、"1-16(单)" / "1-16（双）" 风格解析（统一解析器） */
private fun parseWeeksInput(text: String): Int = WeekUtils.parseWeeksText(text)
