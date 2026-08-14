package com.schedule.njfu.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.schedule.njfu.MainActivity
import com.schedule.njfu.data.AppDatabase
import com.schedule.njfu.data.CourseMapper
import com.schedule.njfu.data.semesterStart
import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

/** 4x3 周网格：周一..周日 7 列，每天最多展示 [MAX_PER_DAY] 门课，当日列高亮 */
class WeekWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now().dayOfWeek.value
        val byDay = runBlocking {
            val db = AppDatabase.get(context)
            val start = db.settingsDao().semesterStart()
            val week = WeekUtils.currentWeek(start, LocalDate.now())
            val weekCourses = db.courseDao().getAll()
                .map { it.toModel() }
                .filter { WeekUtils.contains(it.weeks, week) }
            (1..7).associateWith { day ->
                weekCourses.filter { it.dayOfWeek == day }
                    .sortedBy { it.startPeriod }
                    .take(MAX_PER_DAY)
            }
        }
        provideContent {
            Row(
                GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(0xFFF2F4F7.toInt()))
                    .clickable(
                        actionStartActivity<MainActivity>(
                            actionParametersOf(
                                WidgetAction.OPEN_APP_NAME_KEY.to(WidgetAction.OPEN_APP),
                            ),
                        ),
                    )
                    .padding(4.dp),
            ) {
                for (day in 1..7) {
                    DayColumn(day, byDay.getValue(day), today)
                }
            }
        }
    }

    private companion object {
        const val MAX_PER_DAY = 4
    }
}

@Composable
private fun RowScope.DayColumn(day: Int, courses: List<Course>, today: Int) {
    val isToday = day == today
    Column(
        GlanceModifier
            .defaultWeight()
            .padding(1.dp)
            .background(if (isToday) Color(0xFFDCEAF8) else Color.Transparent)
            .padding(2.dp),
    ) {
        Text(
            DAY_LABELS[day - 1],
            style = TextStyle(
                fontSize = 8.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                color = ColorProvider(if (isToday) Color(0xFF1565C0) else Color(0xFF64748B)),
            ),
            modifier = GlanceModifier.fillMaxWidth(),
        )
        courses.forEach { CourseBlock(it) }
    }
}

@Composable
private fun CourseBlock(course: Course) {
    val raw = if (course.color == 0) CourseMapper.colorFor(course.name) else course.color
    val bg = Color(CourseMapper.displayColor(raw))
    Text(
        course.name.take(1),
        style = TextStyle(
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = ColorProvider(Color.White),
            textAlign = TextAlign.Center,
        ),
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(18.dp)
            .padding(1.dp)
            .background(bg),
        maxLines = 1,
    )
}

private val DAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = WeekWidget()
}
