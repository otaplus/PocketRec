package com.example.backgroundvideo.recording

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.CameraInfo
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import com.example.backgroundvideo.data.CameraFacing
import com.example.backgroundvideo.data.VideoResolution
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Resultado de una grabación finalizada. */
data class RecorderResult(val file: File, val durationMillis: Long)

/**
 * Encapsula CameraX para grabar vídeo sin depender de una Activity.
 *
 * Usa su propio [LifecycleOwner] (ver [ServiceLifecycleOwner]) que el
 * [RecordingService] mantiene en estado `STARTED` mientras el servicio vive, de
 * forma que la cámara sigue funcionando con la pantalla apagada y con la app en
 * segundo plano.
 *
 * La app está pensada para grabar con el teléfono en el bolsillo, por lo que
 * NO se enlaza ningún `Preview`: solo [VideoCapture] (y audio opcional), lo que
 * reduce el consumo y evita que el sistema desenfoque el vídeo por la pantalla
 * bloqueada.
 */
class CameraVideoRecorder(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var cameraStateObserver: Observer<CameraState>? = null

    /** Se invoca en el hilo principal cuando CameraX notifica un error no fatal. */
    var onCameraError: ((String) -> Unit)? = null

    /**
     * @param targetFile fichero destino.
     * @param durationLimitMillis límite de duración (CameraX lo aplica solo); `null` sin límite.
     * @param withAudio activa el micrófono (requiere permiso concedido).
     * @param onStarted se invoca cuando CameraX confirma el inicio real.
     * @param onFinalized se invoca al guardarse el vídeo o al fallar.
     */
    @SuppressLint("MissingPermission")
    fun start(
        facing: CameraFacing,
        quality: Quality,
        targetFile: File,
        durationLimitMillis: Long?,
        withAudio: Boolean,
        onStarted: () -> Unit,
        onFinalized: (Result<RecorderResult>) -> Unit
    ) {
        stopInternal(suppress = true)

        val future = ProcessCameraProvider.getInstance(context)
        // IMPORTANTE: CameraX exige el hilo principal para bindToLifecycle/unbindAll.
        future.addListener({
            val provider = try {
                future.get()
            } catch (t: Throwable) {
                Log.e(TAG, "ProcessCameraProvider no disponible", t)
                onFinalized(Result.failure(t))
                return@addListener
            }
            cameraProvider = provider
            Log.i(TAG, "CameraProvider listo")

            try {
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(quality))
                    .build()

                val capture = VideoCapture.withOutput(recorder)
                videoCapture = capture

                val selector = when (facing) {
                    CameraFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
                    CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
                }

                provider.unbindAll()
                val camera = provider.bindToLifecycle(lifecycleOwner, selector, capture)
                Log.i(TAG, "bindToLifecycle OK (${'$'}facing, ${'$'}quality)")
                observeCameraState(camera.cameraInfo)

                val outputOptions = FileOutputOptions.Builder(targetFile).build()
                val pending = recorder.prepareRecording(context, outputOptions)

                // IMPORTANTE: los builders son inmutables, hay que reasignar.
                val prepared = if (withAudio && hasAudioPermission()) {
                    pending.withAudioEnabled()
                } else {
                    pending
                }

                val startTime = System.currentTimeMillis()

                activeRecording = prepared.start(cameraExecutor) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> onStarted()
                        is VideoRecordEvent.Finalize -> {
                            activeRecording = null
                            Log.i(
                                TAG,
                                "Finalize: error=${'$'}{event.error} cause=${'$'}{event.cause} " +
                                    "bytes=${'$'}{targetFile.length()}"
                            )
                            if (event.hasError()) {
                                if (targetFile.exists() && targetFile.length() == 0L) {
                                    targetFile.delete()
                                }
                                onFinalized(
                                    Result.failure(
                                        IllegalStateException(
                                            "CameraX finalizó con error ${event.error} " +
                                                "(${event.cause?.message ?: "sin causa"})"
                                        )
                                    )
                                )
                            } else {
                                onFinalized(
                                    Result.success(
                                        RecorderResult(
                                            file = targetFile,
                                            durationMillis = System.currentTimeMillis() - startTime
                                        )
                                    )
                                )
                            }
                        }

                        else -> Unit
                    }
                }.apply {
                    if (durationLimitMillis != null) {
                        // El SystemClock marca la parada automática; CameraX usa su propio reloj.
                        scheduleAutoStop(durationLimitMillis)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Fallo al iniciar la grabación", t)
                onFinalized(Result.failure(t))
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private var autoStopRunnable: Runnable? = null

    private fun scheduleAutoStop(limitMillis: Long) {
        val runnable = Runnable { activeRecording?.stop() }
        autoStopRunnable = runnable
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(runnable, limitMillis)
    }

    /** Detiene la grabación solicitando la finalización del fichero. */
    fun stop() = stopInternal(suppress = false)

    private fun stopInternal(suppress: Boolean) {
        autoStopRunnable?.let {
            android.os.Handler(android.os.Looper.getMainLooper()).removeCallbacks(it)
        }
        autoStopRunnable = null
        if (suppress) {
            activeRecording?.close()
        } else {
            activeRecording?.stop()
        }
        activeRecording = null
    }

    /** Libera cámara y executor. Llamar siempre al destruir el servicio. */
    fun release() {
        stopInternal(suppress = false)
        cameraStateObserver?.let { observer ->
            cameraInfo?.cameraState?.removeObserver(observer)
        }
        cameraStateObserver = null
        cameraInfo = null
        runCatching { cameraProvider?.unbindAll() }
        cameraProvider = null
        videoCapture = null
        cameraExecutor.shutdown()
    }

    private var cameraInfo: CameraInfo? = null

    private fun observeCameraState(info: CameraInfo) {
        cameraInfo = info
        val observer = Observer<CameraState> { state ->
            val error = state.error ?: return@Observer
            val code = error.code
            // Los errores recuperables (p. ej. cámara reabierta tras desbloquear) los
            // gestiona CameraX solo; solo avisamos de los críticos.
            if (error.type == CameraState.ErrorType.CRITICAL) {
                onCameraError?.invoke(error.cause?.message ?: "Error de cámara ($code)")
            }
        }
        cameraStateObserver = observer
        info.cameraState.observe(lifecycleOwner, observer)
    }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "CameraVideoRecorder"

        /** Calidades soportadas. La app solo ofrece 720p y 1080p. */
        fun qualityFor(resolution: VideoResolution): Quality = when (resolution) {
            VideoResolution.R720 -> Quality.HD
            VideoResolution.R1080 -> Quality.FHD
        }

        /** Carpeta destino: `Movies/BackgroundVideo` (o almacenamiento interno). */
        fun outputDirectory(context: Context): File {
            val external = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            val root = external ?: File(context.filesDir, "videos")
            return File(root, "BackgroundVideo").apply { mkdirs() }
        }

        fun newOutputFile(context: Context, resolution: VideoResolution): File {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val name = "REC_" + stamp + "_" + resolution.fileTag + ".mp4"
            return File(outputDirectory(context), name)
        }
    }
}

/**
 * `LifecycleOwner` autónomo para que CameraX pueda usar la cámara sin una Activity.
 * El servicio lo mantiene en `STARTED`.
 */
class ServiceLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    fun start() {
        registry.currentState = Lifecycle.State.STARTED
    }

    fun stop() {
        registry.currentState = Lifecycle.State.CREATED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
