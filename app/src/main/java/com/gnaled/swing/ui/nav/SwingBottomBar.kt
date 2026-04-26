package com.gnaled.swing.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

private data class BarItem(val route: NavRoute, val label: String, val icon: ImageVector)

private val items = listOf(
    BarItem(NavRoute.Library, "Library", Icons.Outlined.VideoLibrary),
    BarItem(NavRoute.Capture, "Capture", Icons.Outlined.Videocam),
    BarItem(NavRoute.Compare, "Compare", Icons.Outlined.CompareArrows),
    BarItem(NavRoute.Settings, "Settings", Icons.Outlined.Settings),
)

@Composable
fun SwingBottomBar(currentRoute: String?, onSelect: (NavRoute) -> Unit) {
    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route.path,
                onClick = { onSelect(item.route) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
            )
        }
    }
}

