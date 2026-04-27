package com.gnaled.swing.analysis

import android.content.Context
import android.media.MediaMetadataRetriever
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Detection
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.gnaled.swing.data.entity.BallSample
import java.io.File

/**
 * Optical ball tracker using MediaPipe ObjectDetector. Filters detections
 * to COCO's "sports ball" class and the smallest plausible boxes (the ball
 * is small at typical phone-camera distances; large detections are usually
 * false positives like the head or a hand).
 *
 * This is best-effort: a generic detector will miss many small/blurred
 * tennis balls. If accuracy is poor, swap [MODEL_ASSET] for a tennis-
 * specific TFLite model or shrink [maxBoxFraction].
 */
class BallTracker(private val context: Context) {

    fun detect(
        swingId: String,
        videoFile: File,
        durationMillis: Long,
        frameRate: Float,
    ): List<BallSample> {
        require(videoFile.exists()) { "Video file does not exist: ${videoFile.absolutePath}" }

        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET)
                    .setDelegate(Delegate.GPU)
                    .build(),
            )
            .setRunningMode(RunningMode.VIDEO)
            .setMaxResults(MAX_RESULTS)
            .setScoreThreshold(SCORE_THRESHOLD)
            .setCategoryAllowlist(listOf(SPORTS_BALL_LABEL))
            .build()

        val detector = ObjectDetector.createFromOptions(context, options)
        val retriever = MediaMetadataRetriever()
        val out = mutableListOf<BallSample>()

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

                val frameW = bitmap.width
                val frameH = bitmap.height
                val image = BitmapImageBuilder(bitmap).build()
                val result = detector.detectForVideo(image, timestampMs)
                bitmap.recycle()

                val ball = pickBest(result.detections(), frameW, frameH) ?: continue
                out += BallSample(
                    swingId = swingId,
                    frameIndex = frameIndex,
                    timestampMillis = timestampMs,
                    x = ball.normalizedX,
                    y = ball.normalizedY,
                    confidence = ball.score,
                )
            }
        } finally {
            runCatching { retriever.release() }
            runCatching { detector.close() }
        }

        return out
    }

    private data class Pick(val normalizedX: Float, val normalizedY: Float, val score: Float)

    private fun pickBest(detections: List<Detection>, frameW: Int, frameH: Int): Pick? {
        if (detections.isEmpty() || frameW <= 0 || frameH <= 0) return null
        val frameArea = (frameW * frameH).toFloat()
        // Prefer the smallest, highest-confidence box that's still ball-sized.
        val plausible = detections.mapNotNull { d ->
            val score = d.categories().firstOrNull()?.score() ?: return@mapNotNull null
            val box = d.boundingBox()
            val areaFrac = (box.width() * box.height()) / frameArea
            if (areaFrac > MAX_BOX_FRACTION) return@mapNotNull null
            val centerX = (box.centerX()) / frameW.toFloat()
            val centerY = (box.centerY()) / frameH.toFloat()
            Triple(Pick(centerX, centerY, score), areaFrac, score)
        }
        if (plausible.isEmpty()) return null
        // Score = confidence / sqrt(area) — favors small + confident.
        return plausible.maxBy { (_, area, score) ->
            score / kotlin.math.sqrt(area.coerceAtLeast(1e-5f))
        }.first
    }

    companion object {
        const val MODEL_ASSET = "efficientdet_lite2.tflite"
        private const val SPORTS_BALL_LABEL = "sports ball"
        private const val SCORE_THRESHOLD = 0.2f
        private const val MAX_RESULTS = 5
        private const val MAX_BOX_FRACTION = 0.05f
        private const val MIN_FPS = 15f
        private const val MAX_FPS = 60f
    }
}
