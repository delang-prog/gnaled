package com.gnaled.swing.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gnaled.swing.analysis.PoseAnalysisScheduler
import com.gnaled.swing.capture.AutoClipFinalizer
import com.gnaled.swing.capture.LandmarkFrame
import com.gnaled.swing.capture.SwingTrigger
import com.gnaled.swing.capture.VideoMetadata
import com.gnaled.swing.capture.VideoMetadataReader
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

enum class CaptureMode { Manual, Auto }

sealed interface CaptureUiState {
    data object Idle : CaptureUiState
    data class Recording(val startedAtMillis: Long) : CaptureUiState
    data object Saving : CaptureUiState
    data class Saved(val swingId: String) : CaptureUiState
    data class Error(val message: String) : CaptureUiState
}

sealed interface AutoState {
    data object Off : AutoState
    data class Active(
        val swingsDetected: Int,
        val sessionStartMonotonicMillis: Long,
        val peakWristSpeed: Float = 0f,
    ) : AutoState
    data class Processing(val processed: Int, val total: Int) : AutoState
    data class Done(val saved: Int) : AutoState
    data class Error(val message: String) : AutoState
}

class CaptureViewModel(
    private val repository: SwingRepository,
    private val analysisScheduler: PoseAnalysisScheduler,
    private val autoClipFinalizer: AutoClipFinalizer,
) : ViewModel() {

    private val _state = MutableStateFlow<CaptureUiState>(CaptureUiState.Idle)
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    private val _mode = MutableStateFlow(CaptureMode.Manual)
    val mode: StateFlow<CaptureMode> = _mode.asStateFlow()

    private val _autoState = MutableStateFlow<AutoState>(AutoState.Off)
    val autoState: StateFlow<AutoState> = _autoState.asStateFlow()

    private var trigger: SwingTrigger? = null
    private var detections: MutableList<AutoClipFinalizer.Detection> = mutableListOf()
    private var sessionStartMonotonicMillis: Long = 0L
    private var sessionStartWallMillis: Long = 0L
    private var sessionSourceFile: File? = null

    fun setMode(newMode: CaptureMode) {
        if (_mode.value == newMode) return
        if (_autoState.value is AutoState.Active) return
        _mode.value = newMode
        _autoState.value = AutoState.Off
    }

    // ----- Manual recording -----

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
                label = manualLabel(recordedAtMillis),
                source = CaptureSource.ManualRecord,
                recordedAtMillis = recordedAtMillis,
                durationMillis = metadata.durationMillis,
                frameRate = metadata.frameRate,
                widthPx = metadata.widthPx,
                heightPx = metadata.heightPx,
                videoPath = file.absolutePath,
            )
            repository.insert(swing)
            analysisScheduler.enqueue(clipId)
            _state.value = CaptureUiState.Saved(clipId)
        }
    }

    fun acknowledge() {
        _state.value = CaptureUiState.Idle
    }

    // ----- Auto-clip mode -----

    fun onAutoSessionStarted(file: File, monotonicNowMillis: Long) {
        sessionSourceFile = file
        sessionStartMonotonicMillis = monotonicNowMillis
        sessionStartWallMillis = System.currentTimeMillis()
        detections = mutableListOf()
        trigger = SwingTrigger()
        _autoState.value = AutoState.Active(
            swingsDetected = 0,
            sessionStartMonotonicMillis = monotonicNowMillis,
        )
    }

    fun onAutoFrame(frame: LandmarkFrame, monotonicMillis: Long) {
        val current = _autoState.value as? AutoState.Active ?: return
        val t = trigger ?: return
        val triggered = t.feed(frame, monotonicMillis)
        if (triggered != null) {
            detections += AutoClipFinalizer.Detection(
                contactMillisFromStart = triggered - sessionStartMonotonicMillis,
                sessionStartWallMillis = sessionStartWallMillis,
            )
        }
        _autoState.value = current.copy(
            swingsDetected = detections.size,
            peakWristSpeed = t.peakSpeed,
        )
    }

    fun onAutoSessionFinalized(durationMillis: Long, preMillis: Long = 1500L, postMillis: Long = 1500L) {
        val source = sessionSourceFile ?: run {
            _autoState.value = AutoState.Error("Auto session finalized without a source file")
            return
        }
        val pendingDetections = detections.toList()
        sessionSourceFile = null
        trigger = null
        detections = mutableListOf()

        if (pendingDetections.isEmpty()) {
            _autoState.value = AutoState.Done(saved = 0)
            runCatching { source.delete() }
            return
        }

        _autoState.value = AutoState.Processing(processed = 0, total = pendingDetections.size)
        viewModelScope.launch {
            try {
                val saved = autoClipFinalizer.finalize(
                    source = source,
                    sourceDurationMillis = durationMillis,
                    detections = pendingDetections,
                    preMillis = preMillis,
                    postMillis = postMillis,
                    onProgress = { processed, total ->
                        _autoState.value = AutoState.Processing(processed, total)
                    },
                )
                _autoState.value = AutoState.Done(saved = saved)
            } catch (t: Throwable) {
                _autoState.value = AutoState.Error(t.message ?: "Auto-clip processing failed")
            }
        }
    }

    fun onAutoSessionFailed(message: String) {
        sessionSourceFile?.delete()
        sessionSourceFile = null
        trigger = null
        detections = mutableListOf()
        _autoState.value = AutoState.Error(message)
    }

    fun acknowledgeAuto() {
        _autoState.value = AutoState.Off
    }

    private fun manualLabel(recordedAtMillis: Long): String {
        val time = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(recordedAtMillis))
        return "Swing $time"
    }

    class Factory(
        private val repository: SwingRepository,
        private val analysisScheduler: PoseAnalysisScheduler,
        private val autoClipFinalizer: AutoClipFinalizer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CaptureViewModel(repository, analysisScheduler, autoClipFinalizer) as T
    }
}
