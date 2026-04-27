package com.gnaled.swing.capture

import com.gnaled.swing.analysis.PoseConnections
import kotlin.math.hypot

/**
 * Detects "a swing happened around now" from a stream of [LandmarkFrame]s.
 *
 * Algorithm: take the dominant wrist (whichever is more visible), compute
 * inter-frame speed in normalized image units per second, and fire when it
 * crosses [minPeakUnitsPerSec]. A refractory period suppresses double-fires
 * within [refractoryMillis] of the last trigger.
 *
 * The default threshold is a guess — real values depend on framing distance
 * and shutter speed, so this should be tunable from settings later.
 */
class SwingTrigger(
    private val minPeakUnitsPerSec: Float = 2.5f,
    private val refractoryMillis: Long = 1500L,
    private val visibilityThreshold: Float = 0.5f,
) {

    private var lastX = 0f
    private var lastY = 0f
    private var lastTimestampMillis = 0L
    private var lastTriggerMillis = Long.MIN_VALUE
    private var primed = false

    /** Returns the trigger timestamp if a swing fired, otherwise null. */
    fun feed(frame: LandmarkFrame, monotonicMillis: Long): Long? {
        val wrist = dominantWrist(frame) ?: return null
        if (!primed) {
            lastX = wrist.x; lastY = wrist.y; lastTimestampMillis = monotonicMillis
            primed = true
            return null
        }
        val dt = ((monotonicMillis - lastTimestampMillis).coerceAtLeast(1L)) / 1000f
        val dx = wrist.x - lastX
        val dy = wrist.y - lastY
        val speed = hypot(dx, dy) / dt
        lastX = wrist.x; lastY = wrist.y; lastTimestampMillis = monotonicMillis

        if (speed >= minPeakUnitsPerSec &&
            monotonicMillis - lastTriggerMillis >= refractoryMillis
        ) {
            lastTriggerMillis = monotonicMillis
            return monotonicMillis
        }
        return null
    }

    fun reset() {
        primed = false
        lastTriggerMillis = Long.MIN_VALUE
    }

    private fun dominantWrist(frame: LandmarkFrame): WristPoint? {
        val r = wristAt(frame, PoseConnections.RIGHT_WRIST)
        val l = wristAt(frame, PoseConnections.LEFT_WRIST)
        return when {
            r != null && (l == null || r.visibility >= l.visibility) -> r
            l != null -> l
            else -> null
        }
    }

    private fun wristAt(frame: LandmarkFrame, idx: Int): WristPoint? {
        val base = idx * PoseConnections.FLOATS_PER_LANDMARK
        if (frame.landmarks.size < base + PoseConnections.FLOATS_PER_LANDMARK) return null
        val v = frame.landmarks[base + 3]
        if (v < visibilityThreshold) return null
        return WristPoint(frame.landmarks[base], frame.landmarks[base + 1], v)
    }

    private data class WristPoint(val x: Float, val y: Float, val visibility: Float)
}
