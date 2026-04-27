package com.gnaled.swing.analysis

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Decodes the audio track of an MP4 to mono PCM and runs a simple
 * onset detector over it. Designed for tennis impact transients
 * (racquet hit, ball bounce on hard court): fast, broadband-but-HF-
 * heavy claps that show up as sharp jumps in short-time energy after
 * a basic high-pass.
 */
object AudioPeakDetector {

    data class Onset(val timestampMillis: Long, val strength: Float)

    private const val HOP_MILLIS = 10L
    private const val REFRACTORY_MILLIS = 120L
    /** Onset peaks must beat this multiple of the median flux to count. */
    private const val MEDIAN_MULTIPLIER = 6f

    fun detect(file: File): List<Onset> {
        val pcm = decodeMonoPcm(file) ?: return emptyList()
        if (pcm.samples.isEmpty() || pcm.sampleRate <= 0) return emptyList()
        return findOnsets(pcm.samples, pcm.sampleRate)
    }

    private data class Pcm(val samples: FloatArray, val sampleRate: Int)

    private fun decodeMonoPcm(file: File): Pcm? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { idx ->
                val mime = extractor.getTrackFormat(idx).getString(MediaFormat.KEY_MIME)
                mime?.startsWith("audio/") == true
            } ?: return null

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            val out = ArrayList<Float>(sampleRate * 4)

            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000L)
                    if (inputIndex >= 0) {
                        val buffer = codec.getInputBuffer(inputIndex)!!
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outputIndex = codec.dequeueOutputBuffer(info, 10_000L)
                if (outputIndex >= 0) {
                    val buffer = codec.getOutputBuffer(outputIndex)!!
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    val shorts = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                    if (channelCount <= 1) {
                        while (shorts.hasRemaining()) out.add(shorts.get() / 32768f)
                    } else {
                        val tmp = ShortArray(channelCount)
                        while (shorts.remaining() >= channelCount) {
                            shorts.get(tmp)
                            var sum = 0
                            for (s in tmp) sum += s
                            out.add((sum.toFloat() / channelCount) / 32768f)
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
            codec.stop()
            codec.release()
            Pcm(samples = out.toFloatArray(), sampleRate = sampleRate)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun findOnsets(pcm: FloatArray, sampleRate: Int): List<Onset> {
        val hopSamples = (sampleRate * HOP_MILLIS / 1000L).toInt().coerceAtLeast(1)
        val frameCount = pcm.size / hopSamples
        if (frameCount < 3) return emptyList()

        // Short-time RMS of a one-pole high-pass (x - x_prev), which is
        // adequate for picking out claps without a full FFT.
        val energy = FloatArray(frameCount)
        var hpPrev = 0f
        for (f in 0 until frameCount) {
            val start = f * hopSamples
            val end = (start + hopSamples).coerceAtMost(pcm.size)
            var sumSq = 0.0
            var n = 0
            for (i in start until end) {
                val v = pcm[i]
                val hp = v - hpPrev
                hpPrev = v
                sumSq += hp * hp
                n++
            }
            energy[f] = if (n > 0) sqrt(sumSq / n).toFloat() else 0f
        }

        // Spectral flux substitute: positive energy increase between frames.
        val flux = FloatArray(frameCount)
        for (i in 1 until frameCount) flux[i] = max(0f, energy[i] - energy[i - 1])

        val median = run {
            val sorted = flux.copyOf().also { it.sort() }
            sorted[sorted.size / 2]
        }
        val threshold = max(1e-4f, median * MEDIAN_MULTIPLIER)
        val refractoryFrames = (REFRACTORY_MILLIS / HOP_MILLIS).toInt().coerceAtLeast(1)

        val onsets = mutableListOf<Onset>()
        var lastFrame = -refractoryFrames * 2
        for (i in 1 until frameCount - 1) {
            if (flux[i] >= threshold &&
                flux[i] >= flux[i - 1] &&
                flux[i] >= flux[i + 1] &&
                i - lastFrame >= refractoryFrames
            ) {
                onsets += Onset(timestampMillis = i * HOP_MILLIS, strength = flux[i])
                lastFrame = i
            }
        }
        return onsets
    }
}
