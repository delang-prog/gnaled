package com.gnaled.swing.ui.compare

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.gnaled.swing.data.SwingRepository
import com.gnaled.swing.data.entity.Sample
import com.gnaled.swing.data.entity.Swing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

/**
 * Manages two ExoPlayer instances and the data behind a side-by-side
 * compare view. Both clips are aligned at their detected contact frame:
 * the slider on the screen represents an offset *relative to contact*,
 * which both players seek to in lockstep.
 */
@OptIn(UnstableApi::class)
class CompareViewModel(
    appContext: Context,
    private val repository: SwingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CompareUiState())
    val state: StateFlow<CompareUiState> = _state.asStateFlow()

    val playerA: ExoPlayer = ExoPlayer.Builder(appContext).build().apply { playWhenReady = false }
    val playerB: ExoPlayer = ExoPlayer.Builder(appContext).build().apply { playWhenReady = false }

    fun load(slot: Slot, swingId: String) {
        viewModelScope.launch {
            val swing = repository.get(swingId) ?: return@launch
            val samples = repository.samples(swingId)
            val player = playerFor(slot)
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(swing.videoPath))))
            player.prepare()
            val clip = ClipBundle(swing = swing, samples = samples)
            _state.update {
                when (slot) {
                    Slot.A -> it.copy(a = clip)
                    Slot.B -> it.copy(b = clip)
                }
            }
        }
    }

    fun setOffsetMillis(offsetMillis: Long) {
        _state.update { it.copy(offsetMillis = offsetMillis) }
        applySeek(offsetMillis)
    }

    fun togglePlay() {
        val playing = !state.value.playing
        playerA.playWhenReady = playing
        playerB.playWhenReady = playing
        _state.update { it.copy(playing = playing) }
    }

    fun jumpToContact() {
        setOffsetMillis(0)
        playerA.playWhenReady = false
        playerB.playWhenReady = false
        _state.update { it.copy(playing = false) }
    }

    fun sampleAt(slot: Slot, playbackMillis: Long): Sample? {
        val clip = state.value.clip(slot) ?: return null
        val samples = clip.samples
        if (samples.isEmpty()) return null
        var best = samples[0]
        var bestDiff = abs(best.timestampMillis - playbackMillis)
        for (i in 1 until samples.size) {
            val diff = abs(samples[i].timestampMillis - playbackMillis)
            if (diff < bestDiff) {
                best = samples[i]
                bestDiff = diff
            }
        }
        return best
    }

    private fun applySeek(offsetMillis: Long) {
        val s = state.value
        s.a?.contactTimestampMillis?.let { playerA.seekTo((it + offsetMillis).coerceAtLeast(0)) }
        s.b?.contactTimestampMillis?.let { playerB.seekTo((it + offsetMillis).coerceAtLeast(0)) }
    }

    private fun playerFor(slot: Slot): ExoPlayer = when (slot) {
        Slot.A -> playerA
        Slot.B -> playerB
    }

    override fun onCleared() {
        playerA.release()
        playerB.release()
    }

    class Factory(
        private val appContext: Context,
        private val repository: SwingRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CompareViewModel(appContext, repository) as T
    }
}

enum class Slot { A, B }

data class CompareUiState(
    val a: ClipBundle? = null,
    val b: ClipBundle? = null,
    val offsetMillis: Long = 0L,
    val playing: Boolean = false,
) {
    fun clip(slot: Slot): ClipBundle? = when (slot) { Slot.A -> a; Slot.B -> b }
    val bothLoaded: Boolean get() = a != null && b != null
}

data class ClipBundle(val swing: Swing, val samples: List<Sample>) {
    val contactTimestampMillis: Long? =
        swing.contactFrameIndex?.let { samples.getOrNull(it)?.timestampMillis }
}
