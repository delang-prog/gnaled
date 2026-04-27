package com.gnaled.swing.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.gnaled.swing.ui.appContainer
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val container = appContainer()
    val healthConnect = container.healthConnect
    val scope = rememberCoroutineScope()
    var hrGranted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { hrGranted = healthConnect.hasHeartRatePermission() }

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { result ->
        hrGranted = healthConnect.readHeartRatePermission in result
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)

        Text(text = "Health Connect", style = MaterialTheme.typography.titleMedium)
        when {
            !healthConnect.sdkAvailable -> Text(
                "Install Health Connect from the Play Store to import heart rate from Garmin.",
                style = MaterialTheme.typography.bodyMedium,
            )
            hrGranted -> Text(
                "Heart rate access granted — recent swings will pull HR from Health Connect.",
                style = MaterialTheme.typography.bodyMedium,
            )
            else -> {
                Text(
                    "Grant Health Connect read access to overlay heart rate on swing details. " +
                        "Garmin Connect must be syncing into Health Connect for this to do anything.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = {
                        scope.launch {
                            permissionLauncher.launch(healthConnect.requiredPermissions)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text("Grant heart rate access")
                }
            }
        }
    }
}
