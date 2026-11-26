package com.example.backgroundvideo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.backgroundvideo.data.RecordingEntry
import com.example.backgroundvideo.data.RecordingLibrary
import com.example.backgroundvideo.data.RecordingSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val library = RecordingLibrary(application)

    private val _entries = MutableStateFlow<List<RecordingEntry>>(emptyList())
    val entries: StateFlow<List<RecordingEntry>> = _entries.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Se incrementa al terminar una grabación para forzar el refresco. */
    private var lastKnownVideoName: String? = null

    init {
        refresh()
        observeFinishedRecordings()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _entries.value = library.list()
            _isLoading.value = false
        }
    }

    fun delete(entry: RecordingEntry) {
        viewModelScope.launch {
            library.delete(entry)
            _entries.value = library.list()
        }
    }

    /**
     * Cuando el servicio finaliza una grabación nueva, refrescamos la lista
     * automáticamente sin que el usuario tenga que pulsar nada.
     */
    private fun observeFinishedRecordings() {
        viewModelScope.launch {
            RecordingSession.state.collect { state ->
                val name = state.lastVideoName
                if (name != null && name != lastKnownVideoName) {
                    lastKnownVideoName = name
                    _entries.value = library.list()
                }
            }
        }
    }
}
