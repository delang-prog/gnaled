package com.gnaled.swing.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One ball-detection result for a swing. Coordinates are in MediaPipe's
 * normalized [0,1] image-plane coordinates (origin top-left), the same
 * frame as the pose [Sample].
 */
@Entity(
    tableName = "ball_samples",
    primaryKeys = ["swingId", "frameIndex"],
    foreignKeys = [
        ForeignKey(
            entity = Swing::class,
            parentColumns = ["id"],
            childColumns = ["swingId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("swingId")],
)
data class BallSample(
    val swingId: String,
    val frameIndex: Int,
    val timestampMillis: Long,
    val x: Float,
    val y: Float,
    val confidence: Float,
)
