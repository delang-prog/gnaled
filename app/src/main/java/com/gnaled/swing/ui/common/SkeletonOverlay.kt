package com.gnaled.swing.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.gnaled.swing.analysis.PoseConnections

/**
 * Draws a 33-landmark skeleton on top of an underlying video / preview.
 * Landmarks come in MediaPipe's normalized [0,1] coordinate space (origin
 * top-left). The canvas letterboxes to [contentWidthPx]/[contentHeightPx]
 * so the points line up with the pixels they were detected on. Use this
 * overlay only on top of a view configured with FIT_CENTER scaling — for
 * FILL_CENTER the overlay would need a matching center-crop transform.
 */
@Composable
fun SkeletonOverlay(
    landmarks: FloatArray?,
    contentWidthPx: Int,
    contentHeightPx: Int,
    modifier: Modifier = Modifier,
    visibilityThreshold: Float = 0.5f,
    mirrored: Boolean = false,
) {
    Canvas(modifier = modifier) {
        if (landmarks == null || contentWidthPx <= 0 || contentHeightPx <= 0) return@Canvas
        if (landmarks.size < PoseConnections.LANDMARK_COUNT * PoseConnections.FLOATS_PER_LANDMARK) {
            return@Canvas
        }

        val drawArea = letterbox(
            container = size,
            contentAspect = contentWidthPx.toFloat() / contentHeightPx.toFloat(),
        )

        val pointRadius = 4.dp.toPx()
        val strokeWidth = 3.dp.toPx()
        val jointColor = Color(0xFF22D3EE)
        val boneColor = Color(0xFFA3E635)

        fun pointAt(idx: Int): Offset? {
            val base = idx * PoseConnections.FLOATS_PER_LANDMARK
            if (landmarks[base + 3] < visibilityThreshold) return null
            val rawX = if (mirrored) (1f - landmarks[base]) else landmarks[base]
            val x = drawArea.left + rawX * drawArea.width
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
                cap = StrokeCap.Round,
            )
        }
        for (i in 0 until PoseConnections.LANDMARK_COUNT) {
            val p = pointAt(i) ?: continue
            drawCircle(color = jointColor, radius = pointRadius, center = p)
        }
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
