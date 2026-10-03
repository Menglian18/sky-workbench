package com.nana.workbench.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nana.workbench.ui.home.HomeScreen
import com.nana.workbench.ui.memo.MemoScreen
import com.nana.workbench.ui.settings.SettingsScreen
import com.nana.workbench.ui.sky.SkyScreen
import com.nana.workbench.ui.study.StudyTasksScreen
import com.nana.workbench.ui.today.TodayScreen
import androidx.compose.material.icons.filled.ArrowBack

private fun TabDestination.icon(): ImageVector = when (this) {
    TabDestination.HOME -> Icons.Filled.Home
    TabDestination.TODAY -> Icons.Filled.DateRange
    TabDestination.PLAY -> Icons.Filled.Star
    TabDestination.STUDY -> Icons.Filled.List
    TabDestination.MEMO -> Icons.Filled.Create
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NanaApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentTab = TabDestination.fromRoute(currentRoute)
    val title = when (currentRoute) {
        Routes.SETTINGS -> "数据与设置"
        else -> currentTab?.label ?: TabDestination.default.label
    }

    Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    // 1.1 改进 · D2：进设置后要能退出来
                    if (currentRoute == Routes.SETTINGS) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
                    titleContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                ),
                actions = {
                    if (currentRoute == TabDestination.HOME.route) {
                        IconButton(onClick = { navController.navigateToSettings() }) {
                            Icon(Icons.Filled.Settings, contentDescription = "数据与设置")
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface) {
                Routes.tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = { navController.switchTab(tab) },
                        icon = { Icon(tab.icon(), contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TabDestination.default.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TabDestination.HOME.route) {
                HomeScreen(onOpenTab = { tab -> navController.switchTab(tab) })
            }
            composable(TabDestination.TODAY.route) { TodayScreen() }
            composable(TabDestination.PLAY.route) { SkyScreen() }
            composable(TabDestination.STUDY.route) { StudyTasksScreen() }
            composable(TabDestination.MEMO.route) { MemoScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
        }
    }
}

private fun NavHostController.navigateToSettings() {
    navigate(Routes.SETTINGS) { launchSingleTop = true }
}

/**
 * 切换底部格子：不堆栈，且始终回到起始页之上，避免来回点导致返回键要按很多次。
 */
private fun NavHostController.switchTab(tab: TabDestination) {
    navigate(tab.route) {
        popUpTo(TabDestination.default.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
