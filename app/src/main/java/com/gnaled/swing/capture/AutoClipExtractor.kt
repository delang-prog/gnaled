package com.gnaled.swing.capture

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Extracts a sub-clip [startMillis, endMillis] from [source] into
 * [outputFile] using Media3 Transformer. Used by Auto-clip mode to slice
 * a long continuous recording into per-swing clips after the session ends.
 *
 * Transformer must be created and started on the main thread, so the
 * [extract] suspend function switches to Dispatchers.Main internally.
 */
@OptIn(UnstableApi::class)
class AutoClipExtractor(private val context: Context) {

    suspend fun extract(
        source: File,
        startMillis: Long,
        endMillis: Long,
        outputFile: File,
    ): Boolean = withContext(Dispatchers.Main) {
        require(endMillis > startMillis) { "endMillis must be > startMillis" }
        val clip = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMillis.coerceAtLeast(0))
            .setEndPositionMs(endMillis)
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(source))
            .setClippingConfiguration(clip)
            .build()
        val edited = EditedMediaItem.Builder(mediaItem).build()

        suspendCancellableCoroutine { cont ->
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    if (cont.isActive) cont.resume(false)
                }
            }
            val transformer = Transformer.Builder(context).addListener(listener).build()
            transformer.start(edited, outputFile.absolutePath)
            cont.invokeOnCancellation { transformer.cancel() }
        }
    }
}
