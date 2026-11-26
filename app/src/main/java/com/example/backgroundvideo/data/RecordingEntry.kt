package com.example.backgroundvideo.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.backgroundvideo.recording.CameraVideoRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Un vídeo guardado en disco, tal como se muestra en el historial. */
data class RecordingEntry(
    val file: File,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val durationMillis: Long?
) {
    val durationKnown: Boolean get() = durationMillis != null
}

/**
 * Fuente del historial: escanea la carpeta de salida de la app.
 *
 * No hay base de datos a propósito: así el historial nunca se desincroniza de
 * lo que realmente existe en disco.
 */
class RecordingLibrary(private val context: Context) {

    suspend fun list(): List<RecordingEntry> = withContext(Dispatchers.IO) {
        val dir = CameraVideoRecorder.outputDirectory(context)
        val files = dir.listFiles { file -> file.isFile && file.extension.equals("mp4", true) }
            ?: return@withContext emptyList()

        files
            .sortedByDescending { it.lastModified() }
            .map { file -> toEntry(file) }
    }

    private fun toEntry(file: File): RecordingEntry {
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrElse { Uri.fromFile(file) }

        return RecordingEntry(
            file = file,
            uri = uri,
            name = file.name,
            sizeBytes = file.length(),
            lastModifiedMillis = file.lastModified(),
            durationMillis = readDuration(file)
        )
    }

    private fun readDuration(file: File): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun delete(entry: RecordingEntry): Boolean = withContext(Dispatchers.IO) {
        runCatching { entry.file.delete() }.getOrDefault(false)
    }

    /** Borra una entrada por nombre de archivo; útil tras recibir un evento del servicio. */
    suspend fun deleteByName(name: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(CameraVideoRecorder.outputDirectory(context), name)
        runCatching { file.delete() }.getOrDefault(false)
    }
}
