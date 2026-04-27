package com.gnaled.swing.ui.detail

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.gnaled.swing.data.SwingRepository
import com.gnaled.swing.data.entity.AnalysisStatus
import com.gnaled.swing.data.entity.Sample
import com.gnaled.swing.data.entity.Swing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns an ExoPlayer instance for the duration of the Detail screen and
 * exposes the loaded swing + samples.
 *
 * Sample lookup for the overlay is by playback position; the screen polls
 * `currentPlaybackMillis` and asks [sampleAt] for the closest frame.
 */
@OptIn(UnstableApi::class)
class DetailViewModel(
    appContext: Context,
    private val repository: SwingRepository,
    private val swingId: String,
) : ViewModel() {

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    val player: ExoPlayer = ExoPlayer.Builder(appContext).build().apply {
        playWhenReady = false
    }

    init {
        viewModelScope.launch {
            val swing = repository.get(swingId)
            if (swing == null) {
                _state.value = DetailUiState.Missing
                return@launch
            }
            val samples = repository.samples(swingId)
            player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(java.io.File(swing.videoPath))))
            player.prepare()
            _state.value = DetailUiState.Ready(swing = swing, samples = samples)
        }
    }

    /** Returns the sample whose timestamp is closest to [playbackMillis], or null. */
    fun sampleAt(playbackMillis: Long): Sample? {
        val samples = (state.value as? DetailUiState.Ready)?.samples ?: return null
        if (samples.isEmpty()) return null
        // Samples are ordered by frameIndex (ascending timestamp).
        var best = samples.first()
        var bestDiff = Math.abs(best.timestampMillis - playbackMillis)
        for (i in 1 until samples.size) {
            val diff = Math.abs(samples[i].timestampMillis - playbackMillis)
            if (diff < bestDiff) {
                best = samples[i]
                bestDiff = diff
            }
        }
        return best
    }

    override fun onCleared() {
        player.release()
    }

    class Factory(
        private val appContext: Context,
        private val repository: SwingRepository,
        private val swingId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DetailViewModel(appContext, repository, swingId) as T
    }
}

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data object Missing : DetailUiState
    data class Ready(val swing: Swing, val samples: List<Sample>) : DetailUiState {
        val analysisStatus: AnalysisStatus get() = swing.analysisStatus
    }
}
