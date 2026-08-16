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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.schedule.njfu.R
import com.schedule.njfu.widget.WidgetTheme

/**
 * 设置页「小组件主题」选择组件（纯展示，不读写设置）。
 * 主题状态由 SettingsViewModel 以 StateFlow 提供，选择回调由宿主接线。
 * 三套主题：莫兰迪纸感 / 清新浅色 / 深邃夜间。
 */
@Composable
fun WidgetThemeSection(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.widget_theme_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.widget_theme_desc),
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
                            if (selected != key) onSelect(key)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = keys.size),
                    ) { Text(stringResource(WidgetTheme.themeLabelRes(key))) }
                }
            }
        }
    }
}
