package com.gnaled.swing.ui.detail

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.gnaled.swing.data.entity.AnalysisStatus
import com.gnaled.swing.ui.PlaceholderScreen
import com.gnaled.swing.ui.appContainer

@OptIn(UnstableApi::class)
@Composable
fun DetailScreen(swingId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = appContainer()
    val viewModel: DetailViewModel = viewModel(
        key = "detail-$swingId",
        factory = DetailViewModel.Factory(
            appContext = context.applicationContext,
            repository = container.swingRepository,
            swingId = swingId,
        ),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val s = state) {
        DetailUiState.Loading -> PlaceholderScreen(title = "Loading…", body = "Fetching swing…")
        DetailUiState.Missing -> PlaceholderScreen(
            title = "Swing not found",
            body = "It may have been deleted. Pull back to the library.",
        )
        is DetailUiState.Ready -> ReadyDetail(state = s, viewModel = viewModel)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ReadyDetail(state: DetailUiState.Ready, viewModel: DetailViewModel) {
    var positionMillis by remember { mutableLongStateOf(0L) }

    LaunchedEffect(viewModel) {
        while (true) {
            positionMillis = viewModel.player.currentPosition
            kotlinx.coroutines.delay(33L)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = viewModel.player
                        useController = true
                        controllerAutoShow = true
                    }
                },
            )
            SkeletonOverlay(
                sample = viewModel.sampleAt(positionMillis),
                videoWidthPx = state.swing.widthPx,
                videoHeightPx = state.swing.heightPx,
                modifier = Modifier.fillMaxSize(),
            )
        }

        StatusFooter(state = state, modifier = Modifier.fillMaxWidth().padding(16.dp))
    }
}

@Composable
private fun StatusFooter(state: DetailUiState.Ready, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = state.swing.label,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        val statusText = when (state.analysisStatus) {
            AnalysisStatus.Pending -> "Analysis queued"
            AnalysisStatus.Running -> "Analyzing pose…"
            AnalysisStatus.Complete -> "Pose: ${state.samples.size} frames"
            AnalysisStatus.Failed -> "Analysis failed (tap to retry — coming soon)"
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}
