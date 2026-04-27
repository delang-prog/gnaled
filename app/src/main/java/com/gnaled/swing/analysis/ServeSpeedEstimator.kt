package com.gnaled.swing.analysis

import kotlin.math.abs

/**
 * Estimates serve speed by pairing two audio onsets — the racquet hit
 * (matched to the pose-detected contact moment) and the ball bounce
 * (the next loud onset after the hit) — with an assumed flight distance.
 *
 * Default flight distance is server's baseline → service line, ≈ 18.3 m,
 * which is a reasonable proxy for a "deep" first serve. For accuracy on
 * second serves or different camera placements, replace the default with
 * a calibrated value (future Settings work).
 *
 * Caveats:
 *  - The ball decelerates during flight and bounces, so this gives the
 *    time-averaged speed over the segment, not the instantaneous post-
 *    racquet velocity. For a 60–130 mph range it tends to read 5–15 mph
 *    low compared to a radar gun.
 *  - Mismatched onsets (e.g. a fence rattle picked up as the bounce) can
 *    produce wild numbers. The estimator returns null for any delta
 *    outside [0.15s, 1.2s], which corresponds to roughly 35–270 mph at
 *    the default distance.
 */
object ServeSpeedEstimator {

    private const val DEFAULT_FLIGHT_DISTANCE_METERS = 18.3
    private const val MPS_TO_MPH = 2.23694

    private const val HIT_MATCH_WINDOW_MILLIS = 300L
    private const val MIN_BOUNCE_DELAY_MILLIS = 150L
    private const val MAX_BOUNCE_DELAY_MILLIS = 1200L

    data class Result(
        val mph: Double,
        val hitTimestampMillis: Long,
        val bounceTimestampMillis: Long,
        val flightDistanceMeters: Double,
    )

    fun estimate(
        contactTimestampMillis: Long?,
        onsets: List<AudioPeakDetector.Onset>,
        flightDistanceMeters: Double = DEFAULT_FLIGHT_DISTANCE_METERS,
    ): Result? {
        if (contactTimestampMillis == null || onsets.size < 2) return null

        val hit = onsets.minByOrNull { abs(it.timestampMillis - contactTimestampMillis) }
            ?.takeIf { abs(it.timestampMillis - contactTimestampMillis) <= HIT_MATCH_WINDOW_MILLIS }
            ?: return null

        val bounce = onsets.firstOrNull { onset ->
            val dt = onset.timestampMillis - hit.timestampMillis
            dt in MIN_BOUNCE_DELAY_MILLIS..MAX_BOUNCE_DELAY_MILLIS
        } ?: return null

        val deltaSec = (bounce.timestampMillis - hit.timestampMillis) / 1000.0
        if (deltaSec <= 0.0) return null
        return Result(
            mph = flightDistanceMeters / deltaSec * MPS_TO_MPH,
            hitTimestampMillis = hit.timestampMillis,
            bounceTimestampMillis = bounce.timestampMillis,
            flightDistanceMeters = flightDistanceMeters,
        )
    }
}
