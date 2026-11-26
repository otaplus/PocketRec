package com.example.backgroundvideo.data

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Estado observable de la grabación, compartido entre el servicio y la UI. */
data class RecordingState(
    val isRecording: Boolean = false,
    val isStarting: Boolean = false,
    val elapsedMillis: Long = 0L,
    val autoStopMillis: Long? = null,
    val lastVideoUri: Uri? = null,
    val lastVideoName: String? = null,
    val errorMessage: String? = null
) {
    /** Tiempo restante del temporizador, o `null` si no hay límite. */
    val remainingMillis: Long?
        get() = autoStopMillis?.let { (it - elapsedMillis).coerceAtLeast(0L) }
}

/**
 * Fuente de verdad única en memoria del estado de grabación. El servicio escribe
 * y la Activity observa. También es la vía de comunicación UI -> servicio para
 * pedir una parada manual (ver [stopRequest]).
 */
object RecordingSession {

    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    fun update(transform: (RecordingState) -> RecordingState) {
        _state.update(transform)
    }

    fun clearError() = _state.update { it.copy(errorMessage = null) }
}
