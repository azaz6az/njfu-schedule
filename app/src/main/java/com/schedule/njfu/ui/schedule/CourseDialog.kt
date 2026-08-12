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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.schedule.njfu.data.CourseMapper
import com.schedule.njfu.importer.ExcelImporter
import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils

private val DayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

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

    fun save() {
        val n = name.trim()
        val s = startPeriod.trim().toIntOrNull()
        val e = endPeriod.trim().toIntOrNull()
        val mask = parseWeeksInput(weeksText)
        when {
            n.isEmpty() -> error = "请输入课程名称"
            day !in 1..7 -> error = "星期需为 1-7"
            s == null || s !in 1..WeekGrid.MAX_PERIODS -> error = "开始节需为 1-${WeekGrid.MAX_PERIODS}"
            e == null || e < s || e > WeekGrid.MAX_PERIODS -> error = "结束节需在 ${s}..${WeekGrid.MAX_PERIODS} 之间"
            mask == 0 -> error = "周次格式无效，如 1-16 / 单周 / 1-16(单)"
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
        title = { Text(if (course == null) "添加课程" else "课程详情") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("课程名称") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = teacher, onValueChange = { teacher = it },
                    label = { Text("教师") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = location, onValueChange = { location = it },
                    label = { Text("地点") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // 星期选择
                Column(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = "周${DayLabels.getOrNull(day - 1) ?: ""}",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("星期") },
                        trailingIcon = { Text("▼") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dayExpanded = true },
                    )
                    DropdownMenu(expanded = dayExpanded, onDismissRequest = { dayExpanded = false }) {
                        (1..7).forEach { d ->
                            DropdownMenuItem(
                                text = { Text("周${DayLabels[d - 1]}") },
                                onClick = { day = d; dayExpanded = false },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startPeriod, onValueChange = { startPeriod = it },
                        label = { Text("开始节") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = endPeriod, onValueChange = { endPeriod = it },
                        label = { Text("结束节") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = weeksText, onValueChange = { weeksText = it },
                    label = { Text("周次") },
                    supportingText = { Text("如 1-16、单周、1,3,5、1-16(单)") },
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
            TextButton(onClick = { save() }) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (course != null && course.id > 0 && onDelete != null) {
                    TextButton(onClick = {
                        onDelete(course.id)
                        onDismiss()
                    }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
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

/** "1-16"、"1,3,5"、"单周"、"双周"、"1-16(单)" / "1-16（双）" 风格解析 */
private fun parseWeeksInput(text: String): Int {
    val t = text.trim()
    if (t == "单周") return WeekUtils.oddWeeks(1, WeekUtils.MAX_WEEKS)
    if (t == "双周") return WeekUtils.evenWeeks(1, WeekUtils.MAX_WEEKS)
    val suffix = Regex("^(.+?)[（(](单|双)[)）]$").find(t)
    if (suffix != null) {
        val base = ExcelImporter.parseWeeks(suffix.groupValues[1])
        val odd = suffix.groupValues[2] == "单"
        var mask = 0
        for (w in 1..WeekUtils.MAX_WEEKS) {
            if (WeekUtils.contains(base, w) && (w % 2 == 1) == odd) {
                mask = mask or (1 shl (w - 1))
            }
        }
        return mask
    }
    return ExcelImporter.parseWeeks(t)
}
