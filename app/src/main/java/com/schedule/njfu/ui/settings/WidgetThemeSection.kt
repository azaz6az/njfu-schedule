package com.schedule.njfu.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.schedule.njfu.data.AppDatabase
import com.schedule.njfu.data.SettingsEntity
import com.schedule.njfu.data.SettingsKeys
import com.schedule.njfu.widget.ExamCountdownWidgetProvider
import com.schedule.njfu.widget.NextClassWidgetProvider
import com.schedule.njfu.widget.ScheduleWidgetProvider
import com.schedule.njfu.widget.TodayWidgetProvider
import com.schedule.njfu.widget.WeekWidgetProvider
import com.schedule.njfu.widget.WidgetTheme
import kotlinx.coroutines.launch

/**
 * 设置页「小组件主题」选择组件（独立文件，供 SettingsScreen 挂载）。
 * 三套主题：莫兰迪纸感 / 清新浅色 / 深邃夜间，改后即时刷新全部已添加的小组件。
 */
@Composable
fun WidgetThemeSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(WidgetTheme.DEFAULT_KEY) }

    LaunchedEffect(Unit) {
        val saved = AppDatabase.get(context).settingsDao()
            .get(SettingsKeys.WIDGET_THEME) ?: WidgetTheme.DEFAULT_KEY
        selected = saved
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("小组件主题", style = MaterialTheme.typography.titleSmall)
            Text(
                "作用于「今日课程」「下一节课」「本周课表」等桌面小组件的底色与文字，改后即时生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val keys = listOf(
                WidgetTheme.KEY_MORANDI,
                WidgetTheme.KEY_FRESH,
                WidgetTheme.KEY_DEEP,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                keys.forEachIndexed { index, key ->
                    SegmentedButton(
                        selected = selected == key,
                        onClick = {
                            val target = key
                            if (selected != target) {
                                selected = target
                                scope.launch {
                                    AppDatabase.get(context).settingsDao()
                                        .put(SettingsEntity(SettingsKeys.WIDGET_THEME, target))
                                    // 即时刷新全部已添加的小组件
                                    ScheduleWidgetProvider.refreshAll(context)
                                    WeekWidgetProvider.refreshAll(context)
                                    NextClassWidgetProvider.refreshAll(context)
                                    TodayWidgetProvider.refreshAll(context)
                                    ExamCountdownWidgetProvider.refreshAll(context)
                                }
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = keys.size),
                    ) { Text(WidgetTheme.themeLabel(key)) }
                }
            }
        }
    }
}
