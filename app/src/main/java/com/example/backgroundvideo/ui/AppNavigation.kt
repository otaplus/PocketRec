package com.example.backgroundvideo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.backgroundvideo.data.RecordingSession

/** Destinos de la app. */
enum class AppDestination(val route: String, val label: String, val icon: ImageVector) {
    RECORD("record", "Grabar", Icons.Filled.Videocam),
    HISTORY("history", "Historial", Icons.Filled.VideoLibrary),
    HELP("help", "Ayuda", Icons.Filled.Info)
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination

    // Durante la grabación ocultamos la barra inferior para que un roce en el
    // bolsillo no cambie de pantalla por accidente.
    val recordingState by RecordingSession.state.collectAsStateWithLifecycle()
    val isRecording = recordingState.isRecording || recordingState.isStarting

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = !isRecording,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                NavigationBar {
                    AppDestination.entries.forEach { destination ->
                        val selected = currentRoute?.hierarchy
                            ?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.RECORD.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppDestination.RECORD.route) {
                RecordScreen(
                    onOpenHistory = {
                        navController.navigate(AppDestination.HISTORY.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(AppDestination.HISTORY.route) {
                HistoryScreen()
            }
            composable(AppDestination.HELP.route) {
                HelpScreen()
            }
        }
    }
}
