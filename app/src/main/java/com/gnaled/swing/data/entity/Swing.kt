package com.gnaled.swing.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SwingType { Forehand, Backhand, Serve, Volley, Unknown }

enum class CaptureSource { ManualRecord, AutoClip, Imported }

enum class AnalysisStatus { Pending, Running, Complete, Failed }

@Entity(tableName = "swings")
data class Swing(
    @PrimaryKey val id: String,
    val label: String,
    val type: SwingType = SwingType.Unknown,
    val source: CaptureSource = CaptureSource.ManualRecord,
    val recordedAtMillis: Long,
    val durationMillis: Long,
    val frameRate: Float,
    val widthPx: Int,
    val heightPx: Int,
    val videoPath: String,
    val contactFrameIndex: Int? = null,
    val analysisStatus: AnalysisStatus = AnalysisStatus.Pending,
)
