package com.schedule.njfu.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.schedule.njfu.data.AppDatabase
import com.schedule.njfu.ui.schedule.ExamScreen
import com.schedule.njfu.ui.schedule.ExamViewModel
import com.schedule.njfu.ui.schedule.ScheduleScreen
import com.schedule.njfu.ui.schedule.ScheduleViewModel
import com.schedule.njfu.ui.settings.SettingsScreen
import com.schedule.njfu.ui.settings.SettingsViewModel

object Routes {
    const val SCHEDULE = "schedule"
    const val EXAMS = "exams"
    const val SETTINGS = "settings"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun AppNav(db: AppDatabase) {
    val navController = rememberNavController()
    val tabs = listOf(
        Tab(Routes.SCHEDULE, "课表", Icons.AutoMirrored.Filled.List),
        Tab(Routes.EXAMS, "考试", Icons.Filled.DateRange),
        Tab(Routes.SETTINGS, "设置", Icons.Filled.Settings),
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStack by navController.currentBackStackEntryAsState()
                val currentDestination = backStack?.destination
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = Routes.SCHEDULE, modifier = Modifier.padding(innerPadding)) {
            composable(Routes.SCHEDULE) {
                val vm: ScheduleViewModel = viewModel(factory = ScheduleViewModel.Factory(db))
                ScheduleScreen(vm)
            }
            composable(Routes.EXAMS) {
                val vm: ExamViewModel = viewModel(factory = ExamViewModel.Factory(db))
                ExamScreen(vm, onAdd = { /* 考试添加已内联在 ExamScreen */ })
            }
            composable(Routes.SETTINGS) {
                val context = LocalContext.current
                val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(db, context))
                LaunchedEffect(Unit) { vm.load() }
                SettingsScreen(vm)
            }
        }
    }
}
