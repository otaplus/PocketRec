package com.example.backgroundvideo.recording

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.backgroundvideo.MainActivity
import com.example.backgroundvideo.R
import com.example.backgroundvideo.data.CameraFacing
import com.example.backgroundvideo.data.RecordingSession
import com.example.backgroundvideo.data.RecordingSettings
import com.example.backgroundvideo.data.VideoResolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Servicio en primer plano (tipos `camera` + `microphone`) que mantiene la
 * grabación viva aunque la pantalla esté apagada o el usuario cambie de app.
 *
 * Puntos clave de la implementación:
 * - `startForeground()` se llama SIEMPRE antes de tocar la cámara; en Android 14+
 *   iniciar la cámara sin un FGS de tipo `camera` activo lanza excepción.
 * - Mantiene un `PARTIAL_WAKE_LOCK` para que el vídeo no se corte con la pantalla
 *   apagada.
 * - El límite de duración lo aplica CameraX (`Handler.postDelayed`), no la UI.
 */
class RecordingService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main.immediate + job)

    private lateinit var lifecycleOwner: ServiceLifecycleOwner
    private lateinit var recorder: CameraVideoRecorder
    private var wakeLock: PowerManager.WakeLock? = null

    /** `true` cuando el FGS ya está promocionado con un tipo válido. */
    private var foregroundStarted = false
    private var stopping = false
    private var timerJob: Job? = null
    private var timerTick = 0

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner = ServiceLifecycleOwner().apply { start() }
        recorder = CameraVideoRecorder(this, lifecycleOwner).apply {
            onCameraError = { message ->
                RecordingSession.update { it.copy(errorMessage = message) }
            }
        }
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(settingsFrom(intent))
            ACTION_STOP -> requestStop()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun settingsFrom(intent: Intent): RecordingSettings {
        val facing = intent.getStringExtra(EXTRA_FACING)
            ?.let { runCatching { CameraFacing.valueOf(it) }.getOrNull() }
            ?: CameraFacing.BACK
        return RecordingSettings(
            facing = facing,
            resolution = intent.getStringExtra(EXTRA_RESOLUTION)
                ?.let { runCatching { VideoResolution.valueOf(it) }.getOrNull() }
                ?: VideoResolution.DEFAULT,
            withAudio = intent.getBooleanExtra(EXTRA_AUDIO, true),
            autoStopMillis = intent.getLongExtra(EXTRA_AUTO_STOP, 0L).takeIf { it > 0L }
        )
    }

    // ---------------------------------------------------------------- grabación

    private fun startRecording(settings: RecordingSettings) {
        Log.i(TAG, "startRecording(): ${'$'}settings")
        if (RecordingSession.state.value.isRecording || foregroundStarted) {
            Log.w(TAG, "startRecording ignorado: ya hay una grabación o FGS activo")
            return
        }
        stopping = false

        val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasCamera) {
            Log.e(TAG, "Sin permiso de cámara")
            RecordingSession.update {
                it.copy(isStarting = false, errorMessage = "Falta el permiso de cámara")
            }
            stopSelf()
            return
        }

        val withAudio = settings.withAudio && hasAudioPermission()
        val outputFile = CameraVideoRecorder.newOutputFile(this, settings.resolution)
        val types = serviceTypes(withAudio)
        Log.i(TAG, "Archivo destino: ${'$'}{outputFile.absolutePath} · tipos=0x${'$'}{Integer.toHexString(types)}")

        RecordingSession.update {
            it.copy(
                isRecording = false,
                isStarting = true,
                elapsedMillis = 0L,
                autoStopMillis = settings.autoStopMillis,
                errorMessage = null
            )
        }

        // 1) Promocionar el servicio (obligatorio antes de abrir la cámara).
        try {
            startForegroundInternal("Preparando cámara…", types)
            Log.i(TAG, "startForeground OK")
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground falló", t)
            RecordingSession.update {
                it.copy(
                    isStarting = false,
                    errorMessage = "No se pudo iniciar el servicio en primer plano: ${'$'}{t.message}"
                )
            }
            stopSelf()
            return
        }

        // 2) Wake lock parcial: la grabación no se detiene con la pantalla apagada.
        acquireWakeLock()

        // 3) Arrancar CameraX.
        recorder.start(
            facing = settings.facing,
            quality = CameraVideoRecorder.qualityFor(settings.resolution),
            targetFile = outputFile,
            durationLimitMillis = settings.autoStopMillis,
            withAudio = withAudio,
            onStarted = {
                Log.i(TAG, "CameraX: grabación iniciada")
                RecordingSession.update {
                    it.copy(
                        isRecording = true,
                        isStarting = false,
                        elapsedMillis = 0L,
                        autoStopMillis = settings.autoStopMillis
                    )
                }
                updateNotification("Grabando…", types, includeStopAction = true)
                startElapsedTimer()
            },
            onFinalized = { result -> onRecordingFinalized(result, outputFile) }
        )
    }

    private fun onRecordingFinalized(result: Result<RecorderResult>, expectedFile: File) {
        stopElapsedTimer()

        result.onSuccess { info ->
            val uri = runCatching {
                FileProvider.getUriForFile(this, "$packageName.fileprovider", info.file)
            }.getOrNull()
            RecordingSession.update {
                it.copy(
                    isRecording = false,
                    isStarting = false,
                    lastVideoUri = uri,
                    lastVideoName = info.file.name,
                    elapsedMillis = 0L
                )
            }
            notifyVideoSaved(info.file, uri)
        }.onFailure { error ->
            if (expectedFile.exists() && expectedFile.length() == 0L) {
                expectedFile.delete()
            }
            RecordingSession.update {
                it.copy(
                    isRecording = false,
                    isStarting = false,
                    elapsedMillis = 0L,
                    errorMessage = error.message ?: "Error al grabar"
                )
            }
            notifyError(error.message ?: "Error al grabar")
        }

        shutdownService()
    }

    /** Parada manual (UI o botón de la notificación). */
    private fun requestStop() {
        if (stopping) return
        stopping = true
        val state = RecordingSession.state.value
        if (state.isRecording || state.isStarting) {
            updateNotification("Guardando vídeo…", currentTypes(), includeStopAction = false)
        }
        recorder.stop()
        if (!state.isRecording && !state.isStarting) {
            shutdownService()
        }
    }

    private fun shutdownService() {
        stopElapsedTimer()
        releaseWakeLock()
        scope.launch {
            // Pequeña espera para que CameraX libere la cámara antes de irnos.
            delay(150L)
            ServiceCompat.stopForeground(this@RecordingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
            stopSelf()
        }
    }

    // ------------------------------------------------------------- temporizador

    private fun startElapsedTimer() {
        timerTick = 0
        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                delay(TICK_MILLIS)
                timerTick++
                val prev = RecordingSession.state.value
                if (!prev.isRecording) return@launch
                val elapsed = prev.elapsedMillis + TICK_MILLIS
                RecordingSession.update { it.copy(elapsedMillis = elapsed) }
                if (timerTick % 10 == 0) {
                    updateNotification(
                        textForTime(elapsed, prev.autoStopMillis),
                        currentTypes(),
                        includeStopAction = true
                    )
                }
            }
        }
    }

    private fun stopElapsedTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun textForTime(elapsed: Long, limit: Long?): String {
        fun fmt(ms: Long): String {
            val total = ms / 1000
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
            else String.format(Locale.US, "%02d:%02d", m, s)
        }
        return if (limit != null) "${fmt(elapsed)} · límite ${fmt(limit)}" else fmt(elapsed)
    }

    // ------------------------------------------------------------ notificaciones

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RECORDING,
                getString(R.string.recording_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.recording_channel_description)
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_EVENTS,
                getString(R.string.recording_finished_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private fun startForegroundInternal(text: String, types: Int) {
        val notification = buildNotification(text, includeStopAction = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, types)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
    }

    private fun updateNotification(text: String, types: Int, includeStopAction: Boolean) {
        if (!foregroundStarted) return
        val notification = buildNotification(text, includeStopAction)
        notifySafely(NOTIFICATION_ID, notification)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, types)
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifySafely(id: Int, notification: Notification) {
        if (!hasNotificationPermission()) return
        runCatching { NotificationManagerCompat.from(this).notify(id, notification) }
    }

    private fun buildNotification(text: String, includeStopAction: Boolean): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_record)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (includeStopAction) {
            val stopIntent = PendingIntent.getService(
                this,
                1,
                Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_stop,
                getString(R.string.recording_notification_stop),
                stopIntent
            )
        }
        return builder.build()
    }

    private fun notifyVideoSaved(file: File, uri: Uri?) {
        if (!hasNotificationPermission()) return
        val openIntent = uri?.let {
            PendingIntent.getActivity(
                this,
                2,
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(it, "video/mp4")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_record)
            .setContentTitle("Vídeo guardado")
            .setContentText(file.name)
            .setAutoCancel(true)
        openIntent?.let { builder.setContentIntent(it) }
        notifySafely(NOTIFICATION_ID_EVENT, builder.build())
    }

    private fun notifyError(message: String) {
        if (!hasNotificationPermission()) return
        val notification = NotificationCompat.Builder(this, CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_record)
            .setContentTitle("Grabación interrumpida")
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        notifySafely(NOTIFICATION_ID_EVENT, notification)
    }

    private fun hasNotificationPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    // ----------------------------------------------------------------- wake lock

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BackgroundVideo:recording")
            .apply {
                setReferenceCounted(false)
                acquire(MAX_WAKE_LOCK_MILLIS)
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
    }

    // -------------------------------------------------------------- ciclo de vida

    override fun onDestroy() {
        stopElapsedTimer()
        recorder.release()
        releaseWakeLock()
        foregroundStarted = false
        lifecycleOwner.destroy()
        RecordingSession.update {
            it.copy(isRecording = false, isStarting = false, elapsedMillis = 0L)
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun serviceTypes(withAudio: Boolean): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if (withAudio) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        return types
    }

    private fun currentTypes(): Int = serviceTypes(hasAudioPermission())

    companion object {
        private const val TAG = "RecordingService"
        private const val CHANNEL_RECORDING = "recording"
        private const val CHANNEL_EVENTS = "recording_events"
        private const val NOTIFICATION_ID = 101
        private const val NOTIFICATION_ID_EVENT = 102
        private const val TICK_MILLIS = 1_000L
        private const val MAX_WAKE_LOCK_MILLIS = 12 * 60 * 60 * 1000L

        const val ACTION_START = "com.example.backgroundvideo.action.START"
        const val ACTION_STOP = "com.example.backgroundvideo.action.STOP"

        private const val EXTRA_FACING = "extra_facing"
        private const val EXTRA_RESOLUTION = "extra_resolution"
        private const val EXTRA_AUDIO = "extra_audio"
        private const val EXTRA_AUTO_STOP = "extra_auto_stop"

        fun startIntent(context: Context, settings: RecordingSettings): Intent =
            Intent(context, RecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_FACING, settings.facing.name)
                putExtra(EXTRA_RESOLUTION, settings.resolution.name)
                putExtra(EXTRA_AUDIO, settings.withAudio)
                putExtra(EXTRA_AUTO_STOP, settings.autoStopMillis ?: 0L)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, RecordingService::class.java).setAction(ACTION_STOP)

        /** Arranca la grabación respetando las restricciones de Android 8+. */
        fun startRecording(context: Context, settings: RecordingSettings) {
            val intent = startIntent(context, settings)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
