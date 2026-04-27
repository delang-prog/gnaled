package com.gnaled.swing.analysis

import com.gnaled.swing.data.entity.Metric
import com.gnaled.swing.data.entity.MetricKind
import com.gnaled.swing.data.entity.Sample
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Computes a small starter set of metrics from segmented samples. Everything
 * here is a 2D image-plane proxy — good enough for relative comparisons but
 * not absolute biomechanics. Real measurements need court-line calibration
 * (Step 7) or a calibrated ruler in the frame.
 */
object MetricsCalculator {

    private const val ASSUMED_SHOULDER_TO_ANKLE_METERS = 1.45
    private const val MPS_TO_MPH = 2.23694
    private const val VISIBILITY_THRESHOLD = 0.5f

    fun compute(
        swingId: String,
        samples: List<Sample>,
        segmentation: SwingSegmenter.Segmentation,
    ): List<Metric> {
        val out = mutableListOf<Metric>()
        val contact = samples[segmentation.contactFrameIndex]
        val backStart = samples[segmentation.backswingStartFrameIndex]
        // Restrict shoulder/hip rotation analysis to the swing window so
        // walking-to/from-the-tripod motion doesn't pollute the range.
        val swingWindow = samples.subList(
            segmentation.backswingStartFrameIndex,
            (segmentation.followThroughEndFrameIndex + 1).coerceAtMost(samples.size),
        )

        rangeDegrees(swingWindow, PoseConnections.LEFT_SHOULDER, PoseConnections.RIGHT_SHOULDER)?.let {
            out += Metric(swingId, MetricKind.ShoulderTurnDeg, it.toDouble(), "deg")
        }
        rangeDegrees(swingWindow, PoseConnections.LEFT_HIP, PoseConnections.RIGHT_HIP)?.let {
            out += Metric(swingId, MetricKind.HipRotationDeg, it.toDouble(), "deg")
        }

        contactHeightBodyRatio(contact, segmentation.dominantWrist)?.let {
            out += Metric(swingId, MetricKind.ContactHeightMeters, it.toDouble(), "bodyHeights")
        }

        val tempoSec = (contact.timestampMillis - backStart.timestampMillis) / 1000.0
        if (tempoSec > 0.0) {
            out += Metric(swingId, MetricKind.SwingTempoSeconds, tempoSec, "s")
        }

        racquetHeadSpeedMph(contact, segmentation.wristSpeeds)?.let {
            out += Metric(swingId, MetricKind.RacquetHeadSpeedMph, it, "mph")
        }

        return out
    }

    private fun rangeDegrees(samples: List<Sample>, leftIdx: Int, rightIdx: Int): Float? {
        val angles = samples.mapNotNull { angleDegrees(it, leftIdx, rightIdx) }
        if (angles.isEmpty()) return null
        val unwrapped = unwrap(angles)
        return unwrapped.max() - unwrapped.min()
    }

    /** atan2 wraps at ±180°; physically a smooth rotation crossing the seam
     *  shouldn't read as a 360° jump. Walk the sequence and add ±360° when
     *  consecutive samples differ by more than 180°. */
    private fun unwrap(angles: List<Float>): FloatArray {
        val out = FloatArray(angles.size)
        if (angles.isEmpty()) return out
        out[0] = angles[0]
        for (i in 1 until angles.size) {
            var diff = angles[i] - out[i - 1]
            while (diff > 180f) diff -= 360f
            while (diff < -180f) diff += 360f
            out[i] = out[i - 1] + diff
        }
        return out
    }

    private fun angleDegrees(s: Sample, leftIdx: Int, rightIdx: Int): Float? {
        if (visibility(s, leftIdx) < VISIBILITY_THRESHOLD) return null
        if (visibility(s, rightIdx) < VISIBILITY_THRESHOLD) return null
        val dx = x(s, rightIdx) - x(s, leftIdx)
        val dy = y(s, rightIdx) - y(s, leftIdx)
        return Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
    }

    private fun contactHeightBodyRatio(contact: Sample, wristIdx: Int): Float? {
        if (visibility(contact, wristIdx) < VISIBILITY_THRESHOLD) return null
        val ankleY = midY(contact, PoseConnections.LEFT_ANKLE, PoseConnections.RIGHT_ANKLE) ?: return null
        val shoulderY = midY(contact, PoseConnections.LEFT_SHOULDER, PoseConnections.RIGHT_SHOULDER) ?: return null
        val span = abs(ankleY - shoulderY)
        if (span < 1e-3f) return null
        // Image y grows downward; flip so positive = wrist above ankle.
        return (ankleY - y(contact, wristIdx)) / span
    }

    private fun racquetHeadSpeedMph(contact: Sample, wristSpeeds: FloatArray): Double? {
        val peak = wristSpeeds.maxOrNull() ?: return null
        if (peak <= 0f) return null
        val ankleY = midY(contact, PoseConnections.LEFT_ANKLE, PoseConnections.RIGHT_ANKLE) ?: return null
        val shoulderY = midY(contact, PoseConnections.LEFT_SHOULDER, PoseConnections.RIGHT_SHOULDER) ?: return null
        val span = abs(ankleY - shoulderY)
        if (span < 1e-3f) return null
        val unitsPerMeter = span / ASSUMED_SHOULDER_TO_ANKLE_METERS
        val mps = peak / unitsPerMeter
        return mps * MPS_TO_MPH
    }

    private fun midY(s: Sample, a: Int, b: Int): Float? {
        if (visibility(s, a) < VISIBILITY_THRESHOLD) return null
        if (visibility(s, b) < VISIBILITY_THRESHOLD) return null
        return (y(s, a) + y(s, b)) / 2f
    }

    private fun x(s: Sample, idx: Int): Float =
        s.landmarks[idx * PoseConnections.FLOATS_PER_LANDMARK]

    private fun y(s: Sample, idx: Int): Float =
        s.landmarks[idx * PoseConnections.FLOATS_PER_LANDMARK + 1]

    private fun visibility(s: Sample, idx: Int): Float =
        s.landmarks[idx * PoseConnections.FLOATS_PER_LANDMARK + 3]
}
