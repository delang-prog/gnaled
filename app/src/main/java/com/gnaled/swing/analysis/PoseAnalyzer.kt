package com.gnaled.swing.analysis

import android.content.Context
import android.media.MediaMetadataRetriever
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.gnaled.swing.data.entity.Sample
import java.io.File

/**
 * Runs MediaPipe PoseLandmarker in VIDEO mode over an MP4 clip and emits
 * one [Sample] per analysed frame. The model file
 * `pose_landmarker_full.task` must be present in `app/src/main/assets/`.
 *
 * The simplest viable implementation uses [MediaMetadataRetriever] to seek
 * to each frame timestamp; this is adequate for the short clips this app
 * deals with (≤10s). Swap to MediaCodec if profiling shows it's a bottleneck.
 */
class PoseAnalyzer(private val context: Context) {

    fun analyze(
        swingId: String,
        videoFile: File,
        durationMillis: Long,
        frameRate: Float,
    ): Result {
        require(videoFile.exists()) { "Video file does not exist: ${videoFile.absolutePath}" }

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET)
                    .setDelegate(Delegate.GPU)
                    .build(),
            )
            .setRunningMode(RunningMode.VIDEO)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build()

        val landmarker = PoseLandmarker.createFromOptions(context, options)
        val retriever = MediaMetadataRetriever()
        val samples = mutableListOf<Sample>()

        try {
            retriever.setDataSource(videoFile.absolutePath)
            val effectiveFps = frameRate.coerceIn(MIN_FPS, MAX_FPS)
            val frameStepMs = (1000f / effectiveFps).toLong().coerceAtLeast(1L)
            val frameCount = ((durationMillis / frameStepMs).toInt()).coerceAtLeast(1)

            for (frameIndex in 0 until frameCount) {
                val timestampMs = frameIndex * frameStepMs
                val bitmap = retriever.getFrameAtTime(
                    timestampMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                ) ?: continue

                val image = BitmapImageBuilder(bitmap).build()
                val result = landmarker.detectForVideo(image, timestampMs)
                bitmap.recycle()

                val landmarks = flattenLandmarks(result) ?: continue
                samples += Sample(
                    swingId = swingId,
                    frameIndex = frameIndex,
                    timestampMillis = timestampMs,
                    landmarks = landmarks,
                )
            }
        } finally {
            runCatching { retriever.release() }
            runCatching { landmarker.close() }
        }

        return Result(samples = samples)
    }

    data class Result(val samples: List<Sample>)

    companion object {
        const val MODEL_ASSET = "pose_landmarker_full.task"
        private const val MIN_FPS = 15f
        private const val MAX_FPS = 60f

        private fun flattenLandmarks(result: PoseLandmarkerResult): FloatArray? {
            val pose = result.landmarks().firstOrNull() ?: return null
            if (pose.size != PoseConnections.LANDMARK_COUNT) return null
            val out = FloatArray(PoseConnections.LANDMARK_COUNT * PoseConnections.FLOATS_PER_LANDMARK)
            for (i in 0 until PoseConnections.LANDMARK_COUNT) {
                val lm = pose[i]
                val base = i * PoseConnections.FLOATS_PER_LANDMARK
                out[base] = lm.x()
                out[base + 1] = lm.y()
                out[base + 2] = lm.z()
                out[base + 3] = lm.visibility().orElse(0f)
            }
            return out
        }
    }
}
