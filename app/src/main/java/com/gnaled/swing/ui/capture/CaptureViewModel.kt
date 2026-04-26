package com.gnaled.swing.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gnaled.swing.capture.VideoMetadata
import com.gnaled.swing.data.SwingRepository
import com.gnaled.swing.data.entity.CaptureSource
import com.gnaled.swing.data.entity.Swing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface CaptureUiState {
    data object Idle : CaptureUiState
    data class Recording(val startedAtMillis: Long) : CaptureUiState
    data object Saving : CaptureUiState
    data class Saved(val swingId: String) : CaptureUiState
    data class Error(val message: String) : CaptureUiState
}

class CaptureViewModel(private val repository: SwingRepository) : ViewModel() {

    private val _state = MutableStateFlow<CaptureUiState>(CaptureUiState.Idle)
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    fun onRecordingStarted(startedAtMillis: Long) {
        _state.value = CaptureUiState.Recording(startedAtMillis)
    }

    fun onRecordingFailed(message: String) {
        _state.value = CaptureUiState.Error(message)
    }

    fun onRecordingFinalized(clipId: String, file: File, metadata: VideoMetadata, recordedAtMillis: Long) {
        _state.update { CaptureUiState.Saving }
        viewModelScope.launch {
            val swing = Swing(
                id = clipId,
                label = defaultLabel(recordedAtMillis),
                source = CaptureSource.ManualRecord,
                recordedAtMillis = recordedAtMillis,
                durationMillis = metadata.durationMillis,
                frameRate = metadata.frameRate,
                widthPx = metadata.widthPx,
                heightPx = metadata.heightPx,
                videoPath = file.absolutePath,
            )
            repository.insert(swing)
            _state.value = CaptureUiState.Saved(clipId)
        }
    }

    fun acknowledge() {
        _state.value = CaptureUiState.Idle
    }

    private fun defaultLabel(recordedAtMillis: Long): String {
        val time = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(recordedAtMillis))
        return "Swing $time"
    }

    class Factory(private val repository: SwingRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CaptureViewModel(repository) as T
    }
}
