package com.gnaled.swing.data

import androidx.room.TypeConverter
import com.gnaled.swing.data.entity.AnalysisStatus
import com.gnaled.swing.data.entity.CaptureSource
import com.gnaled.swing.data.entity.MetricKind
import com.gnaled.swing.data.entity.SwingType
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Converters {
    @TypeConverter fun fromSwingType(value: SwingType): String = value.name
    @TypeConverter fun toSwingType(value: String): SwingType = SwingType.valueOf(value)

    @TypeConverter fun fromCaptureSource(value: CaptureSource): String = value.name
    @TypeConverter fun toCaptureSource(value: String): CaptureSource = CaptureSource.valueOf(value)

    @TypeConverter fun fromAnalysisStatus(value: AnalysisStatus): String = value.name
    @TypeConverter fun toAnalysisStatus(value: String): AnalysisStatus = AnalysisStatus.valueOf(value)

    @TypeConverter fun fromMetricKind(value: MetricKind): String = value.name
    @TypeConverter fun toMetricKind(value: String): MetricKind = MetricKind.valueOf(value)

    @TypeConverter
    fun fromFloatArray(value: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(value.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        value.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    @TypeConverter
    fun toFloatArray(value: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN)
        val floats = FloatArray(value.size / Float.SIZE_BYTES)
        for (i in floats.indices) floats[i] = buffer.float
        return floats
    }
}
