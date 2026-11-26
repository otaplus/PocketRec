package com.example.backgroundvideo.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.backgroundvideo.data.CameraFacing
import com.example.backgroundvideo.data.RecordingSession
import com.example.backgroundvideo.data.RecordingSettings
import com.example.backgroundvideo.data.RecordingSettingsRepository
import com.example.backgroundvideo.data.VideoResolution
import com.example.backgroundvideo.recording.RecordingService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RecordingSettingsRepository(application)

    /** Estado de grabación publicado por el servicio. */
    val recordingState = RecordingSession.state

    /** Ajustes vigentes; se persisten con debounce mientras se editan. */
    private val _settings = MutableStateFlow(RecordingSettings())
    val settings: StateFlow<RecordingSettings> = _settings.asStateFlow()

    private var persistJob: Job? = null

    init {
        viewModelScope.launch {
            // Solo aplicamos lo almacenado al arrancar: si el usuario ya está
            // editando, no queremos pisarle el texto que está escribiendo.
            val stored = repository.settings
            stored.collect { persisted ->
                if (persistJob == null) _settings.value = persisted
            }
        }
    }

    fun setFacing(facing: CameraFacing) = update { it.copy(facing = facing) }

    fun setResolution(resolution: VideoResolution) = update { it.copy(resolution = resolution) }

    fun setWithAudio(enabled: Boolean) = update { it.copy(withAudio = enabled) }

    /**
     * Recibe el texto tal cual lo escribe el usuario (solo dígitos).
     * Vacío o 0 significa "sin límite".
     */
    fun setAutoStopMinutesText(text: String) {
        val cleaned = text.filter { it.isDigit() }.take(5)
        val minutes = cleaned.toLongOrNull()
        update(
            transform = { it.copy(autoStopMillis = RecordingSettings.minutesToMillis(minutes)) },
            debounceMs = PERSIST_DEBOUNCE_MS
        )
    }

    private fun update(
        debounceMs: Long = 0L,
        transform: (RecordingSettings) -> RecordingSettings
    ) {
        val next = transform(_settings.value)
        _settings.value = next

        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            repository.save(next)
        }
    }

    fun startRecording() {
        val state = recordingState.value
        if (state.isRecording || state.isStarting) return
        RecordingService.startRecording(getApplication(), _settings.value)
    }

    fun stopRecording() {
        val context = getApplication<Application>()
        context.startService(RecordingService.stopIntent(context))
    }

    fun dismissError() = RecordingSession.clearError()

    fun shareIntent(): Intent? {
        val uri = recordingState.value.lastVideoUri ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private companion object {
        const val PERSIST_DEBOUNCE_MS = 500L
    }
}
