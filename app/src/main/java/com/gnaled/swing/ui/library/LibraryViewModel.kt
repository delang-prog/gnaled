package com.gnaled.swing.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gnaled.swing.data.SwingRepository
import com.gnaled.swing.data.entity.Swing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class LibraryUiState(val swings: List<Swing> = emptyList())

class LibraryViewModel(repository: SwingRepository) : ViewModel() {

    val uiState: StateFlow<LibraryUiState> =
        repository.observeSwings()
            .map { LibraryUiState(swings = it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = LibraryUiState(),
            )

    class Factory(private val repository: SwingRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LibraryViewModel(repository) as T
        }
    }
}
