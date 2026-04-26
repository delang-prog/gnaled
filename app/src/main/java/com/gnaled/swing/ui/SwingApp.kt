package com.gnaled.swing.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gnaled.swing.ui.capture.CaptureScreen
import com.gnaled.swing.ui.compare.CompareScreen
import com.gnaled.swing.ui.detail.DetailScreen
import com.gnaled.swing.ui.library.LibraryScreen
import com.gnaled.swing.ui.nav.NavRoute
import com.gnaled.swing.ui.nav.SwingBottomBar
import com.gnaled.swing.ui.settings.SettingsScreen

@Composable
fun SwingApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            SwingBottomBar(
                currentRoute = currentRoute,
                onSelect = { route ->
                    navController.navigate(route.path) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = NavRoute.Library.path,
            modifier = Modifier.padding(padding),
        ) {
            composable(NavRoute.Capture.path) { CaptureScreen() }
            composable(NavRoute.Library.path) {
                LibraryScreen(
                    onSwingClick = { id -> navController.navigate(NavRoute.Detail.build(id)) },
                )
            }
            composable(NavRoute.Detail.path) { entry ->
                val id = entry.arguments?.getString(NavRoute.Detail.ARG_ID).orEmpty()
                DetailScreen(swingId = id, onBack = { navController.popBackStack() })
            }
            composable(NavRoute.Compare.path) { CompareScreen() }
            composable(NavRoute.Settings.path) { SettingsScreen() }
        }
    }
}
