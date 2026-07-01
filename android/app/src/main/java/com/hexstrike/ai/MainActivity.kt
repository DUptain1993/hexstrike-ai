package com.hexstrike.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hexstrike.ai.ui.chat.ChatScreen
import com.hexstrike.ai.ui.onboarding.OnboardingScreen
import com.hexstrike.ai.ui.settings.SettingsScreen
import com.hexstrike.ai.ui.terminal.TerminalScreen
import com.hexstrike.ai.ui.theme.HexStrikeTheme

private object Routes {
    const val ONBOARDING = "onboarding"
    const val CHAT = "chat"
    const val TERMINAL = "terminal"
    const val SETTINGS = "settings"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HexStrikeTheme {
                HexStrikeApp()
            }
        }
    }
}

@Composable
private fun HexStrikeApp() {
    val app = (LocalContext.current.applicationContext as HexStrikeApplication)
    val settings by app.settingsRepository.settings.collectAsState()
    val navController = rememberNavController()

    val startDestination = if (settings.onboardingCompleted) Routes.CHAT else Routes.ONBOARDING

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in setOf(Routes.CHAT, Routes.TERMINAL, Routes.SETTINGS)

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.CHAT,
                        onClick = { navController.navigateToTab(Routes.CHAT) },
                        icon = { Icon(Icons.Filled.Chat, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_chat)) },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.TERMINAL,
                        onClick = { navController.navigateToTab(Routes.TERMINAL) },
                        icon = { Icon(Icons.Filled.Terminal, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_terminal)) },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = { navController.navigateToTab(Routes.SETTINGS) },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_settings)) },
                    )
                }
            }
        },
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onContinue = {
                        app.settingsRepository.update { it.copy(onboardingCompleted = true) }
                        navController.navigate(Routes.CHAT) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.CHAT) { ChatScreen() }
            composable(Routes.TERMINAL) { TerminalScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
        }
    }
}

private fun androidx.navigation.NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
