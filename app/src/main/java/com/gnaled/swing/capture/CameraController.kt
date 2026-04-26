package com.gnaled.swing.capture

import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Wraps CameraX setup so the UI layer never touches the provider directly.
 * One instance per Capture screen; rebind on lifecycle changes.
 */
class CameraController(private val context: Context) {

    private var provider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)

    suspend fun bind(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val cameraProvider = awaitProvider()
        val preview = Preview.Builder().build().apply {
            surfaceProvider = previewView.surfaceProvider
        }
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(Quality.FHD, FallbackStrategy.higherQualityOrLowerThan(Quality.HD)),
            )
            .build()
        val capture = VideoCapture.withOutput(recorder)
        videoCapture = capture

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            capture,
        )
    }

    fun unbind() {
        recording?.stop()
        recording = null
        provider?.unbindAll()
    }

    @SuppressLint("MissingPermission")
    fun startRecording(file: File, withAudio: Boolean, onEvent: (VideoRecordEvent) -> Unit) {
        val capture = videoCapture ?: error("Camera not bound")
        check(recording == null) { "Already recording" }

        val pending = capture.output
            .prepareRecording(context, FileOutputOptions.Builder(file).build())
            .apply { if (withAudio) withAudioEnabled() }

        recording = pending.start(mainExecutor) { event ->
            if (event is VideoRecordEvent.Finalize) {
                recording = null
            }
            onEvent(event)
        }
    }

    fun stopRecording() {
        recording?.stop()
    }

    val isRecording: Boolean get() = recording != null

    private suspend fun awaitProvider(): ProcessCameraProvider {
        provider?.let { return it }
        val future = ProcessCameraProvider.getInstance(context)
        return suspendCoroutine { cont ->
            future.addListener({
                val p = future.get()
                provider = p
                cont.resume(p)
            }, mainExecutor)
        }
    }
}
