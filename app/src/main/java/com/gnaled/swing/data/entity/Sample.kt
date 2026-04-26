package com.gnaled.swing.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One pose-landmark frame for a swing. Landmarks are stored as a flat
 * `FloatArray` in landmark-major order: [x0, y0, z0, v0, x1, y1, z1, v1, ...]
 * matching MediaPipe PoseLandmarker's 33 landmarks.
 */
@Entity(
    tableName = "samples",
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
data class Sample(
    val swingId: String,
    val frameIndex: Int,
    val timestampMillis: Long,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val landmarks: FloatArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Sample) return false
        return swingId == other.swingId &&
            frameIndex == other.frameIndex &&
            timestampMillis == other.timestampMillis &&
            landmarks.contentEquals(other.landmarks)
    }

    override fun hashCode(): Int {
        var result = swingId.hashCode()
        result = 31 * result + frameIndex
        result = 31 * result + timestampMillis.hashCode()
        result = 31 * result + landmarks.contentHashCode()
        return result
    }
}
