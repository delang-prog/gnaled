package com.gnaled.swing.ui.detail

import androidx.compose.runtime.Composable
import com.gnaled.swing.ui.PlaceholderScreen

@Composable
fun DetailScreen(swingId: String, onBack: () -> Unit) {
    PlaceholderScreen(
        title = "Swing $swingId",
        body = "Annotated playback, swing phases, and the metrics dashboard go here.",
    )
}
