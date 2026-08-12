package com.schedule.njfu.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.schedule.njfu.MainActivity
import com.schedule.njfu.data.AppDatabase
import com.schedule.njfu.data.semesterStart
import com.schedule.njfu.model.WeekUtils
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

class ScheduleWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val courses = runBlocking {
            val db = AppDatabase.get(context)
            val start = db.settingsDao().semesterStart()
            val week = WeekUtils.currentWeek(start, LocalDate.now())
            val today = LocalDate.now().dayOfWeek.value
            db.courseDao().getAll()
                .map { it.toModel() }
                .filter { it.dayOfWeek == today && WeekUtils.contains(it.weeks, week) }
                .sortedBy { it.startPeriod }
        }
        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(0xFFF2F4F7.toInt()))
                    .clickable(
                        actionStartActivity<MainActivity>(
                            actionParametersOf(
                                WidgetAction.OPEN_APP_NAME_KEY.to(WidgetAction.OPEN_APP),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.TopStart,
            ) {
                Column(GlanceModifier.padding(12.dp)) {
                    Text(
                        "今日课程",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    )
                    if (courses.isEmpty()) {
                        Text("今天无课", style = TextStyle(fontSize = 12.sp))
                    } else {
                        courses.forEach { c ->
                            Text(
                                "${c.startPeriod}-${c.endPeriod}节 ${c.name} ${c.location}",
                                style = TextStyle(fontSize = 11.sp),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

class ScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = ScheduleWidget()
}
