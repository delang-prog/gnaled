package com.gnaled.swing.analysis

/**
 * MediaPipe PoseLandmarker — 33-landmark indexing and the standard
 * connection edges used to draw a skeleton overlay.
 *
 * Each Sample.landmarks float array is laid out as
 * [x0, y0, z0, v0, x1, y1, z1, v1, ...] with [LANDMARK_COUNT] points.
 */
object PoseConnections {

    const val LANDMARK_COUNT = 33
    const val FLOATS_PER_LANDMARK = 4

    const val NOSE = 0
    const val LEFT_EYE_INNER = 1
    const val LEFT_EYE = 2
    const val LEFT_EYE_OUTER = 3
    const val RIGHT_EYE_INNER = 4
    const val RIGHT_EYE = 5
    const val RIGHT_EYE_OUTER = 6
    const val LEFT_EAR = 7
    const val RIGHT_EAR = 8
    const val MOUTH_LEFT = 9
    const val MOUTH_RIGHT = 10
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_PINKY = 17
    const val RIGHT_PINKY = 18
    const val LEFT_INDEX = 19
    const val RIGHT_INDEX = 20
    const val LEFT_THUMB = 21
    const val RIGHT_THUMB = 22
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28
    const val LEFT_HEEL = 29
    const val RIGHT_HEEL = 30
    const val LEFT_FOOT_INDEX = 31
    const val RIGHT_FOOT_INDEX = 32

    /** Edges that form the skeleton when drawing landmarks. */
    val EDGES: List<Pair<Int, Int>> = listOf(
        // Face
        LEFT_EYE_INNER to LEFT_EYE,
        LEFT_EYE to LEFT_EYE_OUTER,
        LEFT_EYE_OUTER to LEFT_EAR,
        RIGHT_EYE_INNER to RIGHT_EYE,
        RIGHT_EYE to RIGHT_EYE_OUTER,
        RIGHT_EYE_OUTER to RIGHT_EAR,
        MOUTH_LEFT to MOUTH_RIGHT,
        // Torso
        LEFT_SHOULDER to RIGHT_SHOULDER,
        LEFT_SHOULDER to LEFT_HIP,
        RIGHT_SHOULDER to RIGHT_HIP,
        LEFT_HIP to RIGHT_HIP,
        // Left arm
        LEFT_SHOULDER to LEFT_ELBOW,
        LEFT_ELBOW to LEFT_WRIST,
        LEFT_WRIST to LEFT_PINKY,
        LEFT_WRIST to LEFT_INDEX,
        LEFT_WRIST to LEFT_THUMB,
        LEFT_INDEX to LEFT_PINKY,
        // Right arm
        RIGHT_SHOULDER to RIGHT_ELBOW,
        RIGHT_ELBOW to RIGHT_WRIST,
        RIGHT_WRIST to RIGHT_PINKY,
        RIGHT_WRIST to RIGHT_INDEX,
        RIGHT_WRIST to RIGHT_THUMB,
        RIGHT_INDEX to RIGHT_PINKY,
        // Left leg
        LEFT_HIP to LEFT_KNEE,
        LEFT_KNEE to LEFT_ANKLE,
        LEFT_ANKLE to LEFT_HEEL,
        LEFT_HEEL to LEFT_FOOT_INDEX,
        LEFT_ANKLE to LEFT_FOOT_INDEX,
        // Right leg
        RIGHT_HIP to RIGHT_KNEE,
        RIGHT_KNEE to RIGHT_ANKLE,
        RIGHT_ANKLE to RIGHT_HEEL,
        RIGHT_HEEL to RIGHT_FOOT_INDEX,
        RIGHT_ANKLE to RIGHT_FOOT_INDEX,
    )
}
