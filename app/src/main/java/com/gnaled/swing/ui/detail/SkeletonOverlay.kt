package com.gnaled.swing.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.gnaled.swing.analysis.PoseConnections
import com.gnaled.swing.data.entity.Sample
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a 33-landmark skeleton on top of the underlying video. Landmarks are
 * normalized [0,1] in MediaPipe's coordinate space (origin top-left). The
 * canvas is letterboxed to the video aspect ratio so points line up with the
 * pixels they were detected on.
 */
@Composable
fun SkeletonOverlay(
    sample: Sample?,
    videoWidthPx: Int,
    videoHeightPx: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (sample == null || videoWidthPx <= 0 || videoHeightPx <= 0) return@Canvas
        val landmarks = sample.landmarks
        if (landmarks.size < PoseConnections.LANDMARK_COUNT * PoseConnections.FLOATS_PER_LANDMARK) {
            return@Canvas
        }

        val drawArea = letterbox(
            container = size,
            contentAspect = videoWidthPx.toFloat() / videoHeightPx.toFloat(),
        )

        val pointRadius = 4.dp.toPx()
        val strokeWidth = 3.dp.toPx()
        val jointColor = Color(0xFF22D3EE)
        val boneColor = Color(0xFFA3E635)
        val visibilityThreshold = 0.5f

        fun pointAt(idx: Int): Offset? {
            val base = idx * PoseConnections.FLOATS_PER_LANDMARK
            val v = landmarks[base + 3]
            if (v < visibilityThreshold) return null
            val x = drawArea.left + landmarks[base] * drawArea.width
            val y = drawArea.top + landmarks[base + 1] * drawArea.height
            return Offset(x, y)
        }

        for ((a, b) in PoseConnections.EDGES) {
            val pa = pointAt(a) ?: continue
            val pb = pointAt(b) ?: continue
            drawLine(
                color = boneColor,
                start = pa,
                end = pb,
                strokeWidth = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
        for (i in 0 until PoseConnections.LANDMARK_COUNT) {
            val p = pointAt(i) ?: continue
            drawCircle(color = jointColor, radius = pointRadius, center = p)
        }
        // Outline around the drawn region for orientation while debugging.
        drawRect(
            color = Color.White.copy(alpha = 0.05f),
            topLeft = Offset(drawArea.left, drawArea.top),
            size = Size(drawArea.width, drawArea.height),
            style = Stroke(width = 1f),
        )
    }
}

private data class Rect(val left: Float, val top: Float, val width: Float, val height: Float)

private fun letterbox(container: Size, contentAspect: Float): Rect {
    val containerAspect = container.width / container.height
    return if (contentAspect > containerAspect) {
        val w = container.width
        val h = w / contentAspect
        Rect(left = 0f, top = (container.height - h) / 2f, width = w, height = h)
    } else {
        val h = container.height
        val w = h * contentAspect
        Rect(left = (container.width - w) / 2f, top = 0f, width = w, height = h)
    }
}

private val Rect.right: Float get() = left + width
private val Rect.bottom: Float get() = top + height

@Suppress("unused")
private fun clamped(v: Float) = max(0f, min(1f, v))
