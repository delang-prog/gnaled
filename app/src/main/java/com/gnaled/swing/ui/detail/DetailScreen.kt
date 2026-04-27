package com.gnaled.swing.ui.detail

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.gnaled.swing.data.entity.AnalysisStatus
import com.gnaled.swing.data.entity.Metric
import com.gnaled.swing.data.entity.MetricKind
import com.gnaled.swing.ui.PlaceholderScreen
import com.gnaled.swing.ui.appContainer
import com.gnaled.swing.ui.common.SkeletonOverlay

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
                landmarks = viewModel.sampleAt(positionMillis)?.landmarks,
                contentWidthPx = state.swing.widthPx,
                contentHeightPx = state.swing.heightPx,
                modifier = Modifier.fillMaxSize(),
            )
        }

        DetailFooter(state = state, modifier = Modifier.fillMaxWidth().padding(16.dp))
    }
}

@Composable
private fun DetailFooter(state: DetailUiState.Ready, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = state.swing.label,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        Text(
            text = statusText(state),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
        if (state.analysisStatus == AnalysisStatus.Complete && state.metrics.isNotEmpty()) {
            MetricsCard(
                metrics = state.metrics,
                contactFrameIndex = state.swing.contactFrameIndex,
                contactTimestampMillis = state.swing.contactFrameIndex
                    ?.let { idx -> state.samples.getOrNull(idx)?.timestampMillis },
            )
        }
    }
}

@Composable
private fun MetricsCard(
    metrics: List<Metric>,
    contactFrameIndex: Int?,
    contactTimestampMillis: Long?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (contactFrameIndex != null && contactTimestampMillis != null) {
                MetricRow(
                    label = "Contact",
                    value = "frame $contactFrameIndex · ${formatSeconds(contactTimestampMillis)}",
                )
            }
            metrics.sortedBy { displayOrder(it.kind) }.forEach { metric ->
                MetricRow(
                    label = labelFor(metric.kind),
                    value = formatMetric(metric),
                )
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun statusText(state: DetailUiState.Ready): String = when (state.analysisStatus) {
    AnalysisStatus.Pending -> "Analysis queued"
    AnalysisStatus.Running -> "Analyzing pose…"
    AnalysisStatus.Complete -> "Pose: ${state.samples.size} frames"
    AnalysisStatus.Failed -> "Analysis failed"
}

private fun labelFor(kind: MetricKind): String = when (kind) {
    MetricKind.ShoulderTurnDeg -> "Shoulder turn"
    MetricKind.HipRotationDeg -> "Hip rotation"
    MetricKind.ContactHeightMeters -> "Contact height"
    MetricKind.SwingTempoSeconds -> "Tempo"
    MetricKind.RacquetHeadSpeedMph -> "Racquet head (proxy)"
    MetricKind.BallSpeedMph -> "Ball speed"
    MetricKind.HeartRateBpm -> "Heart rate"
}

private fun displayOrder(kind: MetricKind): Int = when (kind) {
    MetricKind.RacquetHeadSpeedMph -> 0
    MetricKind.BallSpeedMph -> 1
    MetricKind.SwingTempoSeconds -> 2
    MetricKind.ShoulderTurnDeg -> 3
    MetricKind.HipRotationDeg -> 4
    MetricKind.ContactHeightMeters -> 5
    MetricKind.HeartRateBpm -> 6
}

private fun formatMetric(metric: Metric): String {
    val v = metric.value
    return when (metric.kind) {
        MetricKind.SwingTempoSeconds -> "%.2f s".format(v)
        MetricKind.ShoulderTurnDeg, MetricKind.HipRotationDeg -> "%.0f°".format(v)
        MetricKind.RacquetHeadSpeedMph, MetricKind.BallSpeedMph -> "%.0f mph".format(v)
        MetricKind.ContactHeightMeters -> "%.2f ${metric.unit}".format(v)
        MetricKind.HeartRateBpm -> "%.0f bpm".format(v)
    }
}

private fun formatSeconds(millis: Long): String = "%.2fs".format(millis / 1000.0)
