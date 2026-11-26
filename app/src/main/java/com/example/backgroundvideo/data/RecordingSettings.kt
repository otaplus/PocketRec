package com.example.backgroundvideo.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "recording_settings")

/** Cámara usada para grabar. */
enum class CameraFacing {
    BACK,
    FRONT
}

/**
 * Resolución de grabación ofrecida al usuario. Se limita a dos opciones
 * para simplificar la elección; CameraX mapea cada una a una `Quality`.
 */
enum class VideoResolution(val label: String, val heightPx: Int, val fileTag: String) {
    R720("720p", 720, "720p"),
    R1080("1080p", 1080, "1080p");

    companion object {
        val DEFAULT = R1080

        /** Migración desde la versión anterior que guardaba "HD"/"FHD"/"UHD". */
        fun fromLegacyQualityName(raw: String?): VideoResolution = when (raw) {
            "HD" -> R720
            "FHD", "UHD" -> R1080
            else -> runCatching { valueOf(raw ?: "") }.getOrDefault(DEFAULT)
        }
    }
}

/**
 * Ajustes de grabación elegidos por el usuario.
 *
 * [autoStopMillis] es el temporizador máximo de grabación: cuando se alcanza,
 * el servicio detiene y guarda el vídeo automáticamente. `null` = sin límite.
 */
data class RecordingSettings(
    val facing: CameraFacing = CameraFacing.BACK,
    val resolution: VideoResolution = VideoResolution.DEFAULT,
    val withAudio: Boolean = true,
    val autoStopMillis: Long? = null
) {
    /** Minutos de la parada automática como texto editable ("" = sin límite). */
    val autoStopMinutesText: String
        get() = autoStopMillis?.let { (it / 60_000L).toString() } ?: ""

    companion object {
        /** Límite superior razonable: 24 h. */
        const val MAX_AUTO_STOP_MINUTES = 24 * 60L

        fun minutesToMillis(minutes: Long?): Long? =
            minutes?.takeIf { it in 1..MAX_AUTO_STOP_MINUTES }?.times(60_000L)
    }
}

class RecordingSettingsRepository(private val context: Context) {

    private object Keys {
        val FACING = stringPreferencesKey("facing")
        val QUALITY = stringPreferencesKey("quality")
        val RESOLUTION = stringPreferencesKey("resolution")
        val AUDIO = booleanPreferencesKey("audio")
        val AUTO_STOP_MINUTES = intPreferencesKey("auto_stop_minutes")
        val AUTO_STOP_MILLIS = longPreferencesKey("auto_stop_millis")
    }

    val settings: Flow<RecordingSettings> = context.dataStore.data.map { prefs ->
        val resolution = prefs[Keys.RESOLUTION]
            ?.let { runCatching { VideoResolution.valueOf(it) }.getOrNull() }
            ?: VideoResolution.fromLegacyQualityName(prefs[Keys.QUALITY])

        // Fuente preferente: milisegundos exactos. Fallback: los minutos antiguos.
        val autoStopMillis = prefs[Keys.AUTO_STOP_MILLIS]?.takeIf { it > 0L }
            ?: prefs[Keys.AUTO_STOP_MINUTES]?.takeIf { it > 0 }?.times(60_000L)

        RecordingSettings(
            facing = prefs[Keys.FACING]
                ?.let { runCatching { CameraFacing.valueOf(it) }.getOrNull() }
                ?: CameraFacing.BACK,
            resolution = resolution,
            withAudio = prefs[Keys.AUDIO] ?: true,
            autoStopMillis = autoStopMillis
        )
    }

    suspend fun save(settings: RecordingSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FACING] = settings.facing.name
            prefs[Keys.RESOLUTION] = settings.resolution.name
            prefs.remove(Keys.QUALITY)
            prefs[Keys.AUDIO] = settings.withAudio
            if (settings.autoStopMillis == null) {
                prefs.remove(Keys.AUTO_STOP_MILLIS)
            } else {
                prefs[Keys.AUTO_STOP_MILLIS] = settings.autoStopMillis
            }
            prefs.remove(Keys.AUTO_STOP_MINUTES)
        }
    }
}
