package com.gnaled.swing.capture

import android.content.Context
import com.gnaled.swing.analysis.PoseAnalysisScheduler
import com.gnaled.swing.data.SwingRepository
import com.gnaled.swing.data.entity.CaptureSource
import com.gnaled.swing.data.entity.Swing
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * After an Auto-clip session ends, slices the long source recording into
 * per-swing clips, persists a Swing row for each, and enqueues pose
 * analysis. Each detected swing produces one clip spanning
 * [contact - preMillis, contact + postMillis].
 *
 * Reports progress via the [onProgress] callback so the UI can show a
 * "processing N of M" indicator.
 */
class AutoClipFinalizer(
    private val context: Context,
    private val extractor: AutoClipExtractor,
    private val repository: SwingRepository,
    private val scheduler: PoseAnalysisScheduler,
) {

    data class Detection(val contactMillisFromStart: Long, val sessionStartWallMillis: Long)

    suspend fun finalize(
        source: File,
        sourceDurationMillis: Long,
        detections: List<Detection>,
        preMillis: Long,
        postMillis: Long,
        onProgress: (processed: Int, total: Int) -> Unit,
    ): Int {
        val merged = mergeOverlapping(detections, preMillis, postMillis, sourceDurationMillis)
        var saved = 0
        merged.forEachIndexed { index, window ->
            val clipHandle = ClipStorage.newClipFile(context)
            val ok = runCatching {
                extractor.extract(
                    source = source,
                    startMillis = window.startMillis,
                    endMillis = window.endMillis,
                    outputFile = clipHandle.file,
                )
            }.getOrDefault(false)
            if (ok && clipHandle.file.exists() && clipHandle.file.length() > 0L) {
                val metadata = runCatching { VideoMetadataReader.read(clipHandle.file) }.getOrNull()
                if (metadata != null) {
                    val recordedAtMillis = window.recordedAtWallMillis
                    val swing = Swing(
                        id = clipHandle.id,
                        label = autoLabel(recordedAtMillis),
                        source = CaptureSource.AutoClip,
                        recordedAtMillis = recordedAtMillis,
                        durationMillis = metadata.durationMillis,
                        frameRate = metadata.frameRate,
                        widthPx = metadata.widthPx,
                        heightPx = metadata.heightPx,
                        videoPath = clipHandle.file.absolutePath,
                    )
                    repository.insert(swing)
                    scheduler.enqueue(clipHandle.id)
                    saved++
                } else {
                    clipHandle.file.delete()
                }
            } else {
                clipHandle.file.delete()
            }
            onProgress(index + 1, merged.size)
        }
        // Source recording can be deleted regardless of how many extracts succeeded.
        runCatching { source.delete() }
        return saved
    }

    private data class Window(
        val startMillis: Long,
        val endMillis: Long,
        val recordedAtWallMillis: Long,
    )

    private fun mergeOverlapping(
        detections: List<Detection>,
        preMillis: Long,
        postMillis: Long,
        sourceDurationMillis: Long,
    ): List<Window> {
        if (detections.isEmpty()) return emptyList()
        val windows = detections.sortedBy { it.contactMillisFromStart }.map {
            Window(
                startMillis = (it.contactMillisFromStart - preMillis).coerceAtLeast(0),
                endMillis = (it.contactMillisFromStart + postMillis).coerceAtMost(sourceDurationMillis),
                recordedAtWallMillis = it.sessionStartWallMillis + it.contactMillisFromStart,
            )
        }
        val merged = mutableListOf<Window>()
        for (w in windows) {
            val last = merged.lastOrNull()
            if (last != null && w.startMillis <= last.endMillis) {
                merged[merged.lastIndex] = last.copy(endMillis = maxOf(last.endMillis, w.endMillis))
            } else {
                merged += w
            }
        }
        return merged
    }

    private fun autoLabel(recordedAtMillis: Long): String {
        val time = SimpleDateFormat("MMM d, h:mm:ss a", Locale.getDefault()).format(Date(recordedAtMillis))
        return "Auto · $time"
    }
}
