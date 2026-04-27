package com.gnaled.swing.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gnaled.swing.capture.CameraController
import com.gnaled.swing.capture.ClipHandle
import com.gnaled.swing.capture.ClipStorage
import com.gnaled.swing.capture.VideoMetadataReader
import com.gnaled.swing.ui.appContainer

@Composable
fun CaptureScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val container = appContainer()
    val viewModel: CaptureViewModel = viewModel(
        factory = CaptureViewModel.Factory(
            repository = container.swingRepository,
            analysisScheduler = container.poseAnalysisScheduler,
        ),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    val cameraGranted = rememberPermissionState(Manifest.permission.CAMERA)
    val audioGranted = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    LaunchedEffect(cameraGranted.granted) {
        if (cameraGranted.granted && !audioGranted.granted) audioGranted.request()
    }

    if (!cameraGranted.granted) {
        PermissionGate(
            title = "Camera permission needed",
            body = "Swing Analyzer needs the camera to record swings.",
            buttonLabel = "Grant camera",
            onRequest = { cameraGranted.request() },
        )
        return
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val controller = remember { CameraController(context) }

    LaunchedEffect(controller, lifecycleOwner, previewView) {
        runCatching { controller.bind(lifecycleOwner, previewView) }
            .onFailure { viewModel.onRecordingFailed(it.message ?: "Camera bind failed") }
    }
    DisposableEffect(controller) {
        onDispose { controller.unbind() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView },
        )

        StatusOverlay(state = state, modifier = Modifier.align(Alignment.TopCenter).padding(16.dp))

        RecordButton(
            isRecording = state is CaptureUiState.Recording,
            enabled = state !is CaptureUiState.Saving,
            onClick = {
                if (state is CaptureUiState.Recording) {
                    controller.stopRecording()
                } else {
                    val clip = ClipStorage.newClipFile(context)
                    val startMillis = System.currentTimeMillis()
                    controller.startRecording(
                        file = clip.file,
                        withAudio = audioGranted.granted,
                    ) { event ->
                        when (event) {
                            is VideoRecordEvent.Start -> viewModel.onRecordingStarted(startMillis)
                            is VideoRecordEvent.Finalize -> {
                                if (event.hasError()) {
                                    viewModel.onRecordingFailed(
                                        "Recording failed (${event.error}): ${event.cause?.message ?: "unknown"}",
                                    )
                                    clip.file.delete()
                                } else {
                                    finalizeClip(
                                        clip = clip,
                                        startMillis = startMillis,
                                        viewModel = viewModel,
                                    )
                                }
                            }
                            else -> Unit
                        }
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
        )
    }

    LaunchedEffect(state) {
        if (state is CaptureUiState.Saved || state is CaptureUiState.Error) {
            kotlinx.coroutines.delay(1500)
            viewModel.acknowledge()
        }
    }
}

private fun finalizeClip(
    clip: ClipHandle,
    startMillis: Long,
    viewModel: CaptureViewModel,
) {
    // Metadata read is fast for short clips; runs on the main-executor callback,
    // but the DB insert in the ViewModel is dispatched off the main thread.
    val metadata = VideoMetadataReader.read(clip.file)
    viewModel.onRecordingFinalized(
        clipId = clip.id,
        file = clip.file,
        metadata = metadata,
        recordedAtMillis = startMillis,
    )
}

@Composable
private fun StatusOverlay(state: CaptureUiState, modifier: Modifier = Modifier) {
    val text = when (state) {
        CaptureUiState.Idle -> null
        is CaptureUiState.Recording -> "● Recording"
        CaptureUiState.Saving -> "Saving…"
        is CaptureUiState.Saved -> "Saved"
        is CaptureUiState.Error -> state.message
    } ?: return

    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun RecordButton(
    isRecording: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = { if (enabled) onClick() },
        modifier = modifier.size(72.dp),
        containerColor = if (isRecording) Color(0xFFB91C1C) else Color(0xFFEF4444),
    ) {
        Icon(
            imageVector = if (isRecording) Icons.Filled.Stop else Icons.Outlined.FiberManualRecord,
            contentDescription = if (isRecording) "Stop" else "Record",
            tint = Color.White,
        )
    }
}

@Composable
private fun PermissionGate(
    title: String,
    body: String,
    buttonLabel: String,
    onRequest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )
        Button(onClick = onRequest, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)) {
            Text(buttonLabel)
        }
    }
}

private class PermissionState(
    private val grantedProvider: () -> Boolean,
    val request: () -> Unit,
) {
    val granted: Boolean get() = grantedProvider()
}

@Composable
private fun rememberPermissionState(permission: String): PermissionState {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
    }
    return remember(launcher) {
        PermissionState(grantedProvider = { granted }, request = { launcher.launch(permission) })
    }
}
