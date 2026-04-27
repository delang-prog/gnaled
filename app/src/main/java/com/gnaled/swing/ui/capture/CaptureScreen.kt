package com.gnaled.swing.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gnaled.swing.capture.CameraController
import com.gnaled.swing.capture.ClipHandle
import com.gnaled.swing.capture.ClipStorage
import com.gnaled.swing.capture.LivePoseAnalyzer
import com.gnaled.swing.capture.VideoMetadataReader
import com.gnaled.swing.ui.appContainer
import com.gnaled.swing.ui.common.SkeletonOverlay

@Composable
fun CaptureScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val container = appContainer()
    val viewModel: CaptureViewModel = viewModel(
        factory = CaptureViewModel.Factory(
            repository = container.swingRepository,
            analysisScheduler = container.poseAnalysisScheduler,
            autoClipFinalizer = container.autoClipFinalizer,
        ),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val autoState by viewModel.autoState.collectAsStateWithLifecycle()

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
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }
    val liveAnalyzer = remember { LivePoseAnalyzer(context) }
    val controller = remember(liveAnalyzer) { CameraController(context, liveAnalyzer) }
    val liveFrame by liveAnalyzer.frame.collectAsStateWithLifecycle()

    var useFrontCamera by remember { mutableStateOf(false) }
    val selector = if (useFrontCamera) {
        CameraSelector.DEFAULT_FRONT_CAMERA
    } else {
        CameraSelector.DEFAULT_BACK_CAMERA
    }
    val recordingActive = state is CaptureUiState.Recording ||
        autoState is AutoState.Active ||
        autoState is AutoState.Processing

    LaunchedEffect(controller, lifecycleOwner, previewView, selector) {
        runCatching { controller.bind(lifecycleOwner, previewView, selector) }
            .onFailure { viewModel.onRecordingFailed(it.message ?: "Camera bind failed") }
    }
    DisposableEffect(controller) {
        onDispose {
            controller.unbind()
            liveAnalyzer.close()
        }
    }

    // Keep the screen on while the Capture screen is mounted. Without this
    // the OS turns the display off during long Auto sessions or while the
    // phone is propped on a tripod, which reclaims the camera and ends the
    // recording with ERROR_SOURCE_INACTIVE.
    val rootView = LocalView.current
    DisposableEffect(rootView) {
        rootView.keepScreenOn = true
        onDispose { rootView.keepScreenOn = false }
    }

    LaunchedEffect(autoState) {
        if (autoState is AutoState.Active) {
            liveAnalyzer.frame.collect { frame ->
                if (frame != null) {
                    viewModel.onAutoFrame(frame, SystemClock.uptimeMillis())
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView },
        )

        SkeletonOverlay(
            landmarks = liveFrame?.landmarks,
            contentWidthPx = liveFrame?.widthPx ?: 0,
            contentHeightPx = liveFrame?.heightPx ?: 0,
            mirrored = useFrontCamera,
            modifier = Modifier.fillMaxSize(),
        )

        FilledTonalIconButton(
            onClick = { if (!recordingActive) useFrontCamera = !useFrontCamera },
            enabled = !recordingActive,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Cameraswitch,
                contentDescription = if (useFrontCamera) "Switch to back camera" else "Switch to selfie camera",
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ModeSwitcher(
                mode = mode,
                enabled = autoState !is AutoState.Active && autoState !is AutoState.Processing,
                onSelect = viewModel::setMode,
            )
            Box(modifier = Modifier.padding(top = 8.dp)) {
                StatusOverlay(state = state, autoState = autoState, mode = mode)
            }
        }

        when (mode) {
            CaptureMode.Manual -> ManualControls(
                state = state,
                onClick = {
                    if (state is CaptureUiState.Recording) {
                        controller.stopRecording()
                    } else {
                        startManualRecording(
                            controller = controller,
                            context = context,
                            withAudio = audioGranted.granted,
                            viewModel = viewModel,
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
            )
            CaptureMode.Auto -> AutoControls(
                autoState = autoState,
                onStart = {
                    val clip = ClipStorage.newClipFile(context)
                    val monoStart = SystemClock.uptimeMillis()
                    controller.startRecording(
                        file = clip.file,
                        withAudio = audioGranted.granted,
                    ) { event ->
                        when (event) {
                            is VideoRecordEvent.Start -> viewModel.onAutoSessionStarted(clip.file, monoStart)
                            is VideoRecordEvent.Finalize -> {
                                if (event.hasError()) {
                                    viewModel.onAutoSessionFailed(
                                        "Recording failed (${event.error}): ${event.cause?.message ?: "unknown"}",
                                    )
                                } else {
                                    val durationMillis = runCatching {
                                        VideoMetadataReader.read(clip.file).durationMillis
                                    }.getOrElse { SystemClock.uptimeMillis() - monoStart }
                                    viewModel.onAutoSessionFinalized(durationMillis = durationMillis)
                                }
                            }
                            else -> Unit
                        }
                    }
                },
                onStop = { controller.stopRecording() },
                onAcknowledge = viewModel::acknowledgeAuto,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .fillMaxWidth(),
            )
        }
    }

    LaunchedEffect(state) {
        if (state is CaptureUiState.Saved || state is CaptureUiState.Error) {
            kotlinx.coroutines.delay(1500)
            viewModel.acknowledge()
        }
    }
}

private fun startManualRecording(
    controller: CameraController,
    context: android.content.Context,
    withAudio: Boolean,
    viewModel: CaptureViewModel,
) {
    val clip = ClipStorage.newClipFile(context)
    val startMillis = System.currentTimeMillis()
    controller.startRecording(file = clip.file, withAudio = withAudio) { event ->
        when (event) {
            is VideoRecordEvent.Start -> viewModel.onRecordingStarted(startMillis)
            is VideoRecordEvent.Finalize -> {
                if (event.hasError()) {
                    viewModel.onRecordingFailed(
                        "Recording failed (${event.error}): ${event.cause?.message ?: "unknown"}",
                    )
                    clip.file.delete()
                } else {
                    finalizeClip(clip = clip, startMillis = startMillis, viewModel = viewModel)
                }
            }
            else -> Unit
        }
    }
}

private fun finalizeClip(
    clip: ClipHandle,
    startMillis: Long,
    viewModel: CaptureViewModel,
) {
    val metadata = VideoMetadataReader.read(clip.file)
    viewModel.onRecordingFinalized(
        clipId = clip.id,
        file = clip.file,
        metadata = metadata,
        recordedAtMillis = startMillis,
    )
}

@Composable
private fun ModeSwitcher(
    mode: CaptureMode,
    enabled: Boolean,
    onSelect: (CaptureMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FilterChip(
            selected = mode == CaptureMode.Manual,
            enabled = enabled,
            onClick = { onSelect(CaptureMode.Manual) },
            label = { Text("Manual") },
        )
        FilterChip(
            selected = mode == CaptureMode.Auto,
            enabled = enabled,
            onClick = { onSelect(CaptureMode.Auto) },
            label = { Text("Auto") },
        )
    }
}

@Composable
private fun StatusOverlay(
    state: CaptureUiState,
    autoState: AutoState,
    mode: CaptureMode,
) {
    val text = when (mode) {
        CaptureMode.Manual -> when (state) {
            CaptureUiState.Idle -> null
            is CaptureUiState.Recording -> "● Recording"
            CaptureUiState.Saving -> "Saving…"
            is CaptureUiState.Saved -> "Saved"
            is CaptureUiState.Error -> state.message
        }
        CaptureMode.Auto -> when (autoState) {
            AutoState.Off -> null
            is AutoState.Active -> "● Auto · ${autoState.swingsDetected} swings · peak %.1f"
                .format(autoState.peakWristSpeed)
            is AutoState.Processing -> "Processing ${autoState.processed}/${autoState.total}…"
            is AutoState.Done -> "Saved ${autoState.saved} swings"
            is AutoState.Error -> autoState.message
        }
    } ?: return

    Box(
        modifier = Modifier
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
private fun ManualControls(
    state: CaptureUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRecording = state is CaptureUiState.Recording
    FloatingActionButton(
        onClick = { if (state !is CaptureUiState.Saving) onClick() },
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
private fun AutoControls(
    autoState: AutoState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (autoState) {
            AutoState.Off, is AutoState.Done, is AutoState.Error -> {
                Button(
                    onClick = {
                        if (autoState is AutoState.Done || autoState is AutoState.Error) onAcknowledge()
                        onStart()
                    },
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text("Start auto session")
                }
            }
            is AutoState.Active -> {
                Button(
                    onClick = onStop,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text("Stop session · ${autoState.swingsDetected} so far")
                }
            }
            is AutoState.Processing -> {
                Text(
                    text = "Cutting clips ${autoState.processed}/${autoState.total}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
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
