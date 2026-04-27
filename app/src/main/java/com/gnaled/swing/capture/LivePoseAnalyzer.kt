package com.gnaled.swing.capture

import android.content.Context
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.gnaled.swing.analysis.PoseAnalyzer
import com.gnaled.swing.analysis.PoseConnections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real-time pose detection on the camera preview stream. Plug this in as
 * an [ImageAnalysis] analyzer alongside the recording use case; it emits a
 * [LandmarkFrame] each time PoseLandmarker resolves.
 *
 * Uses the same model file as [PoseAnalyzer] but a separate
 * [RunningMode.LIVE_STREAM] instance — the two modes can't share a
 * detector. CameraX applies natural backpressure (`STRATEGY_KEEP_ONLY_LATEST`)
 * so we drop frames if the model can't keep up rather than queueing.
 */
class LivePoseAnalyzer(context: Context) : ImageAnalysis.Analyzer, AutoCloseable {

    private val _frame = MutableStateFlow<LandmarkFrame?>(null)
    val frame: StateFlow<LandmarkFrame?> = _frame.asStateFlow()

    private val landmarker: PoseLandmarker = PoseLandmarker.createFromOptions(
        context,
        PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(PoseAnalyzer.MODEL_ASSET)
                    .setDelegate(Delegate.GPU)
                    .build(),
            )
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { result, image -> onResult(result, image.width, image.height) }
            .setErrorListener { /* swallow live errors — drop the frame */ }
            .build(),
    )

    override fun analyze(image: ImageProxy) {
        try {
            val bitmap = image.toBitmap()
            val rotation = image.imageInfo.rotationDegrees
            val mpImage = BitmapImageBuilder(bitmap).build()
            val opts = ImageProcessingOptions.builder().setRotationDegrees(rotation).build()
            val timestampMs = SystemClock.uptimeMillis()
            landmarker.detectAsync(mpImage, opts, timestampMs)
        } finally {
            image.close()
        }
    }

    private fun onResult(result: PoseLandmarkerResult, imageWidth: Int, imageHeight: Int) {
        val pose = result.landmarks().firstOrNull()
        if (pose == null || pose.size != PoseConnections.LANDMARK_COUNT) {
            _frame.value = null
            return
        }
        val out = FloatArray(PoseConnections.LANDMARK_COUNT * PoseConnections.FLOATS_PER_LANDMARK)
        for (i in 0 until PoseConnections.LANDMARK_COUNT) {
            val lm = pose[i]
            val base = i * PoseConnections.FLOATS_PER_LANDMARK
            out[base] = lm.x()
            out[base + 1] = lm.y()
            out[base + 2] = lm.z()
            out[base + 3] = lm.visibility().orElse(0f)
        }
        // After rotation by ImageProcessingOptions, MediaPipe normalizes against
        // the rotated frame; for a portrait rotation the effective dimensions
        // are swapped from the sensor's landscape source.
        val rotated90 = imageWidth < imageHeight
        val w = if (rotated90) imageHeight else imageWidth
        val h = if (rotated90) imageWidth else imageHeight
        _frame.value = LandmarkFrame(landmarks = out, widthPx = w, heightPx = h)
    }

    override fun close() {
        landmarker.close()
    }
}

/** A single resolved pose frame in MediaPipe's normalized [0,1] coords. */
data class LandmarkFrame(
    val landmarks: FloatArray,
    val widthPx: Int,
    val heightPx: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LandmarkFrame) return false
        return widthPx == other.widthPx &&
            heightPx == other.heightPx &&
            landmarks.contentEquals(other.landmarks)
    }

    override fun hashCode(): Int {
        var result = widthPx
        result = 31 * result + heightPx
        result = 31 * result + landmarks.contentHashCode()
        return result
    }
}
