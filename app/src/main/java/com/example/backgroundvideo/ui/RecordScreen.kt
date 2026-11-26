package com.example.backgroundvideo.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.backgroundvideo.data.CameraFacing
import com.example.backgroundvideo.data.RecordingSettings
import com.example.backgroundvideo.data.RecordingState
import com.example.backgroundvideo.data.VideoResolution

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    onOpenHistory: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.recordingState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var pendingStart by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val cameraOk = granted[Manifest.permission.CAMERA]
            ?: hasPermission(context, Manifest.permission.CAMERA)
        if (!cameraOk) {
            Toast.makeText(context, "Se necesita el permiso de cámara", Toast.LENGTH_LONG).show()
            pendingStart = false
        } else if (pendingStart) {
            pendingStart = false
            viewModel.startRecording()
        }
    }

    fun requestPermissionsAndStart() {
        val missing = requiredPermissions().filterNot { hasPermission(context, it) }
        if (missing.isEmpty()) {
            viewModel.startRecording()
        } else {
            pendingStart = true
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    val errorMessage = state.errorMessage
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("Grabación interrumpida") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissError() }) { Text("OK") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("PocketRec") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RecordingStatusCard(state)
            Spacer(Modifier.height(20.dp))

            PrimaryRecordButton(
                state = state,
                onClick = {
                    if (state.isRecording || state.isStarting) {
                        viewModel.stopRecording()
                    } else {
                        requestPermissionsAndStart()
                    }
                }
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = if (state.isRecording || state.isStarting) {
                    "Puedes bloquear el teléfono o cambiar de app: la grabación continúa.\n" +
                        "Detenla desde aquí o desde la notificación."
                } else {
                    "El teléfono puede estar bloqueado, en el bolsillo o en otra app."
                },
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
            SettingsCard(
                settings = settings,
                enabled = !state.isRecording && !state.isStarting,
                onFacingChange = viewModel::setFacing,
                onResolutionChange = viewModel::setResolution,
                onAudioChange = viewModel::setWithAudio,
                onMinutesChange = viewModel::setAutoStopMinutesText
            )

            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = onOpenHistory,
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.material3.Icon(
                    Icons.AutoMirrored.Filled.List,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text("Ver historial de grabaciones")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RecordingStatusCard(state: RecordingState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = when {
                                state.isRecording -> MaterialTheme.colorScheme.error
                                state.isStarting -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.outline
                            },
                            shape = CircleShape
                        )
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = when {
                        state.isRecording -> "GRABANDO"
                        state.isStarting -> "INICIANDO…"
                        else -> "DETENIDO"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = formatElapsed(state.elapsedMillis),
                style = MaterialTheme.typography.displaySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )

            val remaining = state.remainingMillis
            if (state.isRecording && remaining != null) {
                Spacer(Modifier.height(10.dp))
                val limit = state.autoStopMillis
                val progress = if (limit != null && limit > 0L) {
                    (state.elapsedMillis.toFloat() / limit).coerceIn(0f, 1f)
                } else {
                    0f
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Se detendrá automáticamente en ${formatElapsed(remaining)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun PrimaryRecordButton(state: RecordingState, onClick: () -> Unit) {
    val active = state.isRecording || state.isStarting
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary
        )
    ) {
        Text(
            text = if (active) "DETENER GRABACIÓN" else "INICIAR GRABACIÓN",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsCard(
    settings: RecordingSettings,
    enabled: Boolean,
    onFacingChange: (CameraFacing) -> Unit,
    onResolutionChange: (VideoResolution) -> Unit,
    onAudioChange: (Boolean) -> Unit,
    onMinutesChange: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Ajustes", style = MaterialTheme.typography.titleMedium)

            // Cámara
            Column {
                Text("Cámara", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    CameraFacing.entries.forEachIndexed { index, facing ->
                        SegmentedButton(
                            selected = settings.facing == facing,
                            onClick = { onFacingChange(facing) },
                            enabled = enabled,
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = CameraFacing.entries.size
                            )
                        ) {
                            Text(if (facing == CameraFacing.BACK) "Trasera" else "Frontal")
                        }
                    }
                }
            }

            // Resolución: solo 720p y 1080p
            Column {
                Text("Resolución", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    VideoResolution.entries.forEachIndexed { index, resolution ->
                        SegmentedButton(
                            selected = settings.resolution == resolution,
                            onClick = { onResolutionChange(resolution) },
                            enabled = enabled,
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = VideoResolution.entries.size
                            )
                        ) {
                            Text(resolution.label)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "1080p ocupa aproximadamente el doble de espacio que 720p.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Audio
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Grabar audio", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Micrófono activado durante la grabación",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.withAudio,
                    onCheckedChange = onAudioChange,
                    enabled = enabled
                )
            }

            // Parada automática: cuadro de texto libre
            Column {
                Text("Parada automática", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = settings.autoStopMinutesText,
                    onValueChange = onMinutesChange,
                    enabled = enabled,
                    label = { Text("Minutos") },
                    placeholder = { Text("Sin límite") },
                    suffix = { Text("min") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Deja el campo vacío para grabar sin límite de tiempo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun formatElapsed(millis: Long): String {
    val total = millis / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", m, s)
    }
}

private fun requiredPermissions(): List<String> = buildList {
    add(Manifest.permission.CAMERA)
    add(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
