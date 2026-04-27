package com.gnaled.swing.analysis

import com.gnaled.swing.data.entity.Sample
import kotlin.math.hypot
import kotlin.math.max

/**
 * Locates the contact frame and the backswing/follow-through window from a
 * sequence of pose samples. Heuristic: contact = peak smoothed wrist speed.
 * The dominant wrist is the one with the higher peak speed.
 */
object SwingSegmenter {

    private const val EDGE_THRESHOLD_FRACTION = 0.3f
    private const val VISIBILITY_THRESHOLD = 0.5f

    data class Segmentation(
        val contactFrameIndex: Int,
        val backswingStartFrameIndex: Int,
        val followThroughEndFrameIndex: Int,
        val dominantWrist: Int,
        val wristSpeeds: FloatArray,
    )

    fun segment(samples: List<Sample>): Segmentation? {
        if (samples.size < 3) return null
        val rightSpeeds = wristSpeeds(samples, PoseConnections.RIGHT_WRIST)
        val leftSpeeds = wristSpeeds(samples, PoseConnections.LEFT_WRIST)
        val rightPeak = rightSpeeds.maxOrNull() ?: 0f
        val leftPeak = leftSpeeds.maxOrNull() ?: 0f
        val dominantWrist: Int
        val speeds: FloatArray
        if (rightPeak >= leftPeak) {
            dominantWrist = PoseConnections.RIGHT_WRIST
            speeds = rightSpeeds
        } else {
            dominantWrist = PoseConnections.LEFT_WRIST
            speeds = leftSpeeds
        }
        val contactIdx = speeds.indices.maxByOrNull { speeds[it] } ?: return null
        val peak = speeds[contactIdx]
        if (peak <= 0f) return null

        val threshold = peak * EDGE_THRESHOLD_FRACTION
        var backIdx = contactIdx
        while (backIdx > 0 && speeds[backIdx - 1] >= threshold) backIdx--
        var followIdx = contactIdx
        while (followIdx < speeds.size - 1 && speeds[followIdx + 1] >= threshold) followIdx++

        return Segmentation(
            contactFrameIndex = contactIdx,
            backswingStartFrameIndex = backIdx,
            followThroughEndFrameIndex = followIdx,
            dominantWrist = dominantWrist,
            wristSpeeds = speeds,
        )
    }

    private fun wristSpeeds(samples: List<Sample>, wristIndex: Int): FloatArray {
        val raw = FloatArray(samples.size)
        for (i in 1 until samples.size) {
            val a = samples[i - 1]
            val b = samples[i]
            val av = visibility(a, wristIndex)
            val bv = visibility(b, wristIndex)
            if (av < VISIBILITY_THRESHOLD || bv < VISIBILITY_THRESHOLD) continue
            val dt = max(1f, (b.timestampMillis - a.timestampMillis).toFloat()) / 1000f
            val dx = x(b, wristIndex) - x(a, wristIndex)
            val dy = y(b, wristIndex) - y(a, wristIndex)
            raw[i] = hypot(dx, dy) / dt
        }
        return smooth3(raw)
    }

    private fun smooth3(values: FloatArray): FloatArray {
        if (values.size < 3) return values
        val out = values.copyOf()
        for (i in 1 until values.size - 1) {
            out[i] = (values[i - 1] + values[i] + values[i + 1]) / 3f
        }
        return out
    }

    private fun x(s: Sample, idx: Int): Float =
        s.landmarks[idx * PoseConnections.FLOATS_PER_LANDMARK]

    private fun y(s: Sample, idx: Int): Float =
        s.landmarks[idx * PoseConnections.FLOATS_PER_LANDMARK + 1]

    private fun visibility(s: Sample, idx: Int): Float =
        s.landmarks[idx * PoseConnections.FLOATS_PER_LANDMARK + 3]
}
