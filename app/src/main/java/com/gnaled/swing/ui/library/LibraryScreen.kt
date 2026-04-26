package com.gnaled.swing.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gnaled.swing.data.entity.Swing
import com.gnaled.swing.ui.PlaceholderScreen
import com.gnaled.swing.ui.appContainer
import java.text.DateFormat
import java.util.Date

@Composable
fun LibraryScreen(onSwingClick: (String) -> Unit) {
    val container = appContainer()
    val viewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.Factory(container.swingRepository),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.swings.isEmpty()) {
        PlaceholderScreen(
            title = "No swings yet",
            body = "Recorded clips will appear here. Use the Capture tab to add one.",
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.swings, key = { it.id }) { swing ->
            SwingRow(swing = swing, onClick = { onSwingClick(swing.id) })
        }
    }
}

@Composable
private fun SwingRow(swing: Swing, onClick: () -> Unit) {
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = swing.label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = formatter.format(Date(swing.recordedAtMillis)),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
