package com.gnaled.swing.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

enum class MetricKind {
    ShoulderTurnDeg,
    HipRotationDeg,
    ContactHeightMeters,
    SwingTempoSeconds,
    RacquetHeadSpeedMph,
    BallSpeedMph,
    HeartRateBpm,
}

@Entity(
    tableName = "metrics",
    primaryKeys = ["swingId", "kind"],
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
data class Metric(
    val swingId: String,
    val kind: MetricKind,
    val value: Double,
    val unit: String,
)
