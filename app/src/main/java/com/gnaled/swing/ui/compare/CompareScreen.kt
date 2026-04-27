package com.gnaled.swing.ui.compare

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.gnaled.swing.data.entity.Swing
import com.gnaled.swing.ui.appContainer
import com.gnaled.swing.ui.common.SkeletonOverlay

private const val OFFSET_RANGE_MS = 2000L

@OptIn(UnstableApi::class)
@Composable
fun CompareScreen() {
    val context = LocalContext.current
    val container = appContainer()
    val viewModel: CompareViewModel = viewModel(
        factory = CompareViewModel.Factory(
            appContext = context.applicationContext,
            repository = container.swingRepository,
        ),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    val library by container.swingRepository.observeSwings().collectAsStateWithLifecycle(initialValue = emptyList())
    var pickerSlot by remember { mutableStateOf<Slot?>(null) }

    var positionA by remember { mutableLongStateOf(0L) }
    var positionB by remember { mutableLongStateOf(0L) }
    LaunchedEffect(viewModel) {
        while (true) {
            positionA = viewModel.playerA.currentPosition
            positionB = viewModel.playerB.currentPosition
            kotlinx.coroutines.delay(33L)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        ClipPane(
            label = state.a?.swing?.label ?: "Pick swing A",
            player = viewModel.playerA,
            widthPx = state.a?.swing?.widthPx ?: 0,
            heightPx = state.a?.swing?.heightPx ?: 0,
            landmarks = viewModel.sampleAt(Slot.A, positionA)?.landmarks,
            onPick = { pickerSlot = Slot.A },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        ClipPane(
            label = state.b?.swing?.label ?: "Pick swing B",
            player = viewModel.playerB,
            widthPx = state.b?.swing?.widthPx ?: 0,
            heightPx = state.b?.swing?.heightPx ?: 0,
            landmarks = viewModel.sampleAt(Slot.B, positionB)?.landmarks,
            onPick = { pickerSlot = Slot.B },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )

        ControlBar(
            offsetMillis = state.offsetMillis,
            playing = state.playing,
            enabled = state.bothLoaded,
            onOffsetChange = viewModel::setOffsetMillis,
            onPlayPause = viewModel::togglePlay,
            onJumpToContact = viewModel::jumpToContact,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }

    pickerSlot?.let { slot ->
        SwingPickerDialog(
            swings = library,
            onPick = { id ->
                viewModel.load(slot, id)
                pickerSlot = null
            },
            onDismiss = { pickerSlot = null },
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ClipPane(
    label: String,
    player: ExoPlayer,
    widthPx: Int,
    heightPx: Int,
    landmarks: FloatArray?,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(Color(0xFF0B1220))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                }
            },
            update = { it.player = player },
        )
        SkeletonOverlay(
            landmarks = landmarks,
            contentWidthPx = widthPx,
            contentHeightPx = heightPx,
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(onClick = onPick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}

@Composable
private fun ControlBar(
    offsetMillis: Long,
    playing: Boolean,
    enabled: Boolean,
    onOffsetChange: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onJumpToContact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (enabled) {
                "Contact ${formatOffset(offsetMillis)}"
            } else {
                "Pick two swings to align them at contact"
            },
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            enabled = enabled,
            value = offsetMillis.toFloat(),
            valueRange = -OFFSET_RANGE_MS.toFloat()..OFFSET_RANGE_MS.toFloat(),
            onValueChange = { onOffsetChange(it.toLong()) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPlayPause, enabled = enabled) {
                Text(if (playing) "Pause" else "Play")
            }
            OutlinedButton(onClick = onJumpToContact, enabled = enabled) {
                Text("Contact")
            }
        }
    }
}

@Composable
private fun SwingPickerDialog(
    swings: List<Swing>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a swing") },
        text = {
            if (swings.isEmpty()) {
                Text("No swings yet. Record one in the Capture tab.")
            } else {
                LazyColumn(modifier = Modifier.height(360.dp)) {
                    items(swings, key = { it.id }) { s ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(s.id) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                        ) {
                            Column {
                                Text(s.label, style = MaterialTheme.typography.bodyLarge)
                                if (s.contactFrameIndex == null) {
                                    Text(
                                        "no contact detected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatOffset(millis: Long): String {
    val sign = if (millis >= 0) "+" else "−"
    return "$sign%.2fs".format(kotlin.math.abs(millis) / 1000.0)
}
