package com.gnaled.swing.capture

import android.media.MediaMetadataRetriever
import java.io.File

data class VideoMetadata(
    val durationMillis: Long,
    val widthPx: Int,
    val heightPx: Int,
    val frameRate: Float,
)

object VideoMetadataReader {
    fun read(file: File): VideoMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            VideoMetadata(
                durationMillis = retriever.long(MediaMetadataRetriever.METADATA_KEY_DURATION),
                widthPx = retriever.int(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
                heightPx = retriever.int(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
                frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                    ?.toFloatOrNull()
                    ?: 30f,
            )
        } finally {
            retriever.release()
        }
    }

    private fun MediaMetadataRetriever.long(key: Int): Long =
        extractMetadata(key)?.toLongOrNull() ?: 0L

    private fun MediaMetadataRetriever.int(key: Int): Int =
        extractMetadata(key)?.toIntOrNull() ?: 0
}
