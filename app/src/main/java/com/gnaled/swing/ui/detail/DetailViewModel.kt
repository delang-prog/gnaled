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
import com.gnaled.swing.data.entity.Metric
import com.gnaled.swing.data.entity.Sample
import com.gnaled.swing.data.entity.Swing
import com.gnaled.swing.health.HealthConnectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

private const val HR_WINDOW_PRE_SECONDS = 30L
private const val HR_WINDOW_POST_SECONDS = 30L

/**
 * Loads a swing from the repository as Flows so analysis updates land on
 * the screen without a manual refresh: when the worker finishes and writes
 * samples + metrics, the combined flow re-emits and the UI shows the
 * metrics card immediately.
 */
@OptIn(UnstableApi::class)
class DetailViewModel(
    appContext: Context,
    private val repository: SwingRepository,
    private val healthConnect: HealthConnectRepository,
    private val swingId: String,
) : ViewModel() {

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    val player: ExoPlayer = ExoPlayer.Builder(appContext).build().apply {
        playWhenReady = false
    }

    private var playerPrepared = false
    private var hrLoadedFor: Long? = null

    init {
        viewModelScope.launch {
            combine(
                repository.observeSwing(swingId),
                repository.observeSamples(swingId),
                repository.observeMetrics(swingId),
            ) { swing, samples, metrics ->
                Triple(swing, samples, metrics)
            }.collect { (swing, samples, metrics) ->
                if (swing == null) {
                    _state.value = DetailUiState.Missing
                    return@collect
                }
                if (!playerPrepared) {
                    player.setMediaItem(
                        MediaItem.fromUri(android.net.Uri.fromFile(java.io.File(swing.videoPath))),
                    )
                    player.prepare()
                    playerPrepared = true
                }
                val previous = state.value as? DetailUiState.Ready
                _state.value = DetailUiState.Ready(
                    swing = swing,
                    samples = samples,
                    metrics = metrics,
                    averageHeartRateBpm = previous?.averageHeartRateBpm,
                )
                if (hrLoadedFor != swing.recordedAtMillis) {
                    hrLoadedFor = swing.recordedAtMillis
                    loadHeartRate(swing)
                }
            }
        }
    }

    private fun loadHeartRate(swing: Swing) {
        viewModelScope.launch {
            val start = Instant.ofEpochMilli(swing.recordedAtMillis).minusSeconds(HR_WINDOW_PRE_SECONDS)
            val end = Instant.ofEpochMilli(swing.recordedAtMillis + swing.durationMillis)
                .plusSeconds(HR_WINDOW_POST_SECONDS)
            val samples = healthConnect.heartRateBetween(start, end)
            if (samples.isEmpty()) return@launch
            val avg = samples.map { it.bpm }.average().toLong()
            _state.update { current ->
                (current as? DetailUiState.Ready)?.copy(averageHeartRateBpm = avg) ?: current
            }
        }
    }

    fun sampleAt(playbackMillis: Long): Sample? {
        val samples = (state.value as? DetailUiState.Ready)?.samples ?: return null
        if (samples.isEmpty()) return null
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
        private val healthConnect: HealthConnectRepository,
        private val swingId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DetailViewModel(appContext, repository, healthConnect, swingId) as T
    }
}

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data object Missing : DetailUiState
    data class Ready(
        val swing: Swing,
        val samples: List<Sample>,
        val metrics: List<Metric>,
        val averageHeartRateBpm: Long?,
    ) : DetailUiState {
        val analysisStatus: AnalysisStatus get() = swing.analysisStatus
    }
}
