package com.example.backgroundvideo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.backgroundvideo.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Ayuda") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HelpSection(
                title = "Grabar con el teléfono en el bolsillo",
                body = "Pulsa «Iniciar grabación» y bloquea el teléfono. La grabación " +
                    "continúa con la pantalla apagada y aunque abras otras apps. " +
                    "Mientras grabas, la barra inferior se oculta para que un roce " +
                    "en el bolsillo no cambie de pantalla."
            )

            HelpSection(
                title = "Detener la grabación",
                body = "Puedes detenerla de tres formas: desde el botón grande de la " +
                    "pantalla principal, desde la notificación permanente del sistema " +
                    "(acción «Detener») o esperando a que venza la parada automática."
            )

            HelpSection(
                title = "Parada automática",
                body = "Escribe cuántos minutos quieres grabar como máximo. Si dejas el " +
                    "campo vacío, la grabación no se detendrá sola. El límite se aplica " +
                    "siempre en el servicio, incluso con la pantalla bloqueada."
            )

            HelpSection(
                title = "Resolución: 720p o 1080p",
                body = "720p genera archivos más pequeños y calienta menos el teléfono; " +
                    "ideal para trayectos largos. 1080p ofrece más detalle y consume " +
                    "aproximadamente el doble de espacio por minuto."
            )

            HelpSection(
                title = "¿Dónde se guardan los vídeos?",
                body = "En la carpeta privada de la app: " +
                    "Android/data/com.example.backgroundvideo/files/Movies/BackgroundVideo. " +
                    "Aparecen listados en la pestaña Historial, donde puedes reproducirlos, " +
                    "compartirlos o borrarlos."
            )

            HelpSection(
                title = "Si la grabación se corta sola",
                body = "Algunos fabricantes (Xiaomi, Huawei, Samsung, Honor…) aplican " +
                    "optimizaciones de batería muy agresivas. Si notas cortes, entra en " +
                    "los ajustes del sistema, busca PocketRec y desactiva la optimización " +
                    "de batería o marca la app como «sin restricciones»."
            )

            HelpSection(
                title = "Aviso legal",
                body = "Graba solo en lugares donde esté permitido. La legislación sobre " +
                    "grabación de imágenes y audio en espacios públicos y privados varía " +
                    "según el país."
            )

            HorizontalDivider()

            CreditsCard()
        }
    }
}

@Composable
private fun HelpSection(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CreditsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "PocketRec",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Grabación continua en segundo plano",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Desarrollado por",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "otaplus",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Versión ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Hecho con Android CameraX, Media3 y Jetpack Compose",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
