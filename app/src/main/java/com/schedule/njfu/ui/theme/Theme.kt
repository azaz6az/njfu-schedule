package com.schedule.njfu.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ---- 莫兰迪纸感 · 浅色 ----
private val LightColors = lightColorScheme(
    primary = Color(0xFFA88C8C),
    onPrimary = Color(0xFFFAFAF8),
    primaryContainer = Color(0xFFF1E9E9),
    onPrimaryContainer = Color(0xFF6B5555),
    secondary = Color(0xFF9FB4C7),
    onSecondary = Color(0xFFFAFAF8),
    secondaryContainer = Color(0xFFE8EEF3),
    onSecondaryContainer = Color(0xFF4A5A68),
    tertiary = Color(0xFFA8BCA3),
    onTertiary = Color(0xFFFAFAF8),
    tertiaryContainer = Color(0xFFEAF0E9),
    onTertiaryContainer = Color(0xFF4A5A46),
    background = Color(0xFFFAFAF8),
    onBackground = Color(0xFF4A4A44),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF4A4A44),
    surfaceVariant = Color(0xFFF3F2EC),
    onSurfaceVariant = Color(0xFF8F8E86),
    outline = Color(0xFFE0DFD8),
    outlineVariant = Color(0xFFEFEEE8),
    error = Color(0xFFB35959),
    onError = Color(0xFFFAFAF8),
    errorContainer = Color(0xFFF3E3E3),
    onErrorContainer = Color(0xFF7A3F3F),
)

// ---- 莫兰迪纸感 · 深色 ----
private val DarkColors = darkColorScheme(
    primary = Color(0xFFC4A9A9),
    onPrimary = Color(0xFF2A2020),
    primaryContainer = Color(0xFF332B2B),
    onPrimaryContainer = Color(0xFFE4CCCC),
    secondary = Color(0xFFA9BDCE),
    onSecondary = Color(0xFF232A31),
    secondaryContainer = Color(0xFF2B333B),
    onSecondaryContainer = Color(0xFFCBD8E2),
    tertiary = Color(0xFFB4C8AF),
    onTertiary = Color(0xFF232A22),
    tertiaryContainer = Color(0xFF2B332A),
    onTertiaryContainer = Color(0xFFD3E2CF),
    background = Color(0xFF1E1E1C),
    onBackground = Color(0xFFE8E6E0),
    surface = Color(0xFF262622),
    onSurface = Color(0xFFE8E6E0),
    surfaceVariant = Color(0xFF2B2B27),
    onSurfaceVariant = Color(0xFF8F8E86),
    outline = Color(0xFF3A3A34),
    outlineVariant = Color(0xFF2B2B27),
    error = Color(0xFFD48A8A),
    onError = Color(0xFF2A2020),
    errorContainer = Color(0xFF4A3232),
    onErrorContainer = Color(0xFFEAC0C0),
)

/**
 * 课程卡片 6 色板（莫兰迪低饱和 · 加深档）。
 * 白字对比度实测 5.1~6.2:1（WCAG AA 正文 ≥ 4.5:1），配合
 * CourseCard 内 95%/92% 白字仍 ≥ 4.5:1，保证可读性。
 */
object CoursePalette {
    val colors = listOf(
        Color(0xFF4E6B58), // 灰绿
        Color(0xFF4A647C), // 灰蓝
        Color(0xFF8A5E5E), // 灰粉
        Color(0xFF73675A), // 暖灰
        Color(0xFF6A5F78), // 灰紫
        Color(0xFF58746A), // 灰青
    )
}

@Composable
fun ScheduleTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colorScheme = when {
        // Android 12+：Material You 动态取色（跟随壁纸），莫兰迪作为回退
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}