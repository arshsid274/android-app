package com.cue.reply.ui

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cue.reply.ui.components.CueTopBar
import com.cue.reply.ui.screens.ActivityScreen
import com.cue.reply.ui.screens.ChatDetailScreen
import com.cue.reply.ui.screens.ChatsScreen
import com.cue.reply.ui.screens.HomeScreen
import com.cue.reply.ui.screens.OnboardingScreen
import com.cue.reply.ui.screens.SettingsScreen
import com.cue.reply.ui.theme.CueDuration
import com.cue.reply.ui.theme.CueEasing

private enum class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val iconSelected: ImageVector,
) {
    HOME("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    CHATS("chats", "Chats", Icons.AutoMirrored.Outlined.Chat, Icons.AutoMirrored.Filled.Chat),
    ACTIVITY("activity", "Activity", Icons.Outlined.Analytics, Icons.Filled.Analytics),
    SETTINGS("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
}

@Composable
fun CueApp() {
    val navController = rememberNavController()
    val vm: CueViewModel = viewModel()
    val settings by vm.settings.collectAsState()
    val startRoute by vm.startRoute.collectAsState()

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val onTab = currentRoute in Tab.entries.map { it.route }

    if (startRoute == null) {
        // Splash gate: settings still loading, keep the brand mark on screen.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Sentience Core",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        return
    }

    Scaffold(
        topBar = {
            if (onTab) {
                CueTopBar(killed = settings.killed, onToggleKill = { vm.toggleKill() })
            }
        },
        bottomBar = {
            if (onTab) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Tab.entries.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    if (selected) tab.iconSelected else tab.icon,
                                    contentDescription = tab.label,
                                )
                            },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        // Tabs are switched dozens of times a day, so they get no transition —
        // animation there would only ever read as lag. Only the thread detail,
        // which is entered occasionally and is spatially "deeper", animates.
        NavHost(
            navController = navController,
            startDestination = startRoute!!,
            modifier = Modifier.padding(padding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable("onboarding") {
                OnboardingScreen(vm) {
                    navController.navigate(Tab.HOME.route) {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            }
            composable(Tab.HOME.route) {
                HomeScreen(vm) {
                    navController.navigate(Tab.ACTIVITY.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
            composable(Tab.CHATS.route) {
                ChatsScreen(vm) { threadKey ->
                    navController.navigate("chat/${Uri.encode(threadKey)}")
                }
            }
            composable(Tab.ACTIVITY.route) { ActivityScreen(vm) }
            composable(Tab.SETTINGS.route) { SettingsScreen(vm) }
            composable(
                route = "chat/{threadKey}",
                arguments = listOf(navArgument("threadKey") { type = NavType.StringType }),
                // Enters from the right and leaves the same way, so the back
                // gesture reverses the motion the user just saw. Exit is quicker
                // than enter: the system responding should outpace the decision.
                enterTransition = {
                    slideInHorizontally(
                        animationSpec = tween(CueDuration.PANEL, easing = CueEasing.Drawer),
                        initialOffsetX = { it / 6 },
                    ) + fadeIn(tween(CueDuration.PANEL, easing = CueEasing.Out))
                },
                popExitTransition = {
                    slideOutHorizontally(
                        animationSpec = tween(CueDuration.CHIP, easing = CueEasing.Out),
                        targetOffsetX = { it / 6 },
                    ) + fadeOut(tween(CueDuration.CHIP, easing = CueEasing.Out))
                },
            ) { entry ->
                val threadKey = Uri.decode(entry.arguments?.getString("threadKey").orEmpty())
                ChatDetailScreen(vm, threadKey, onBack = { navController.popBackStack() })
            }
        }
    }
}
