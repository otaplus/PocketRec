# PocketRec

**Graba tus recorridos con el teléfono en el bolsillo.** PocketRec sigue grabando vídeo con la pantalla apagada, el dispositivo bloqueado o mientras usas cualquier otra app.

> Desarrollado por **otaplus**

---

## El problema que resuelve

Si grabas un trayecto con la app de cámara del sistema y guardas el teléfono en el bolsillo, la pantalla táctil acaba deteniendo la grabación. PocketRec mueve **toda la captura a un servicio en primer plano**, de modo que la Activity y la pantalla dejan de importar: la grabación continúa hasta que tú decidas pararla o venza el temporizador.

## Características

| | |
|---|---|
| 🎥 **Grabación en segundo plano** | Servicio en primer plano con tipos `camera` + `microphone`. Sobrevive al bloqueo de pantalla, al apagado de pantalla y al cambio de app. |
| ⏱️ **Parada automática** | Campo de texto libre en minutos. Vacío = sin límite. El límite lo aplica el servicio, no la interfaz, así que funciona igual con la pantalla bloqueada. |
| 📐 **720p o 1080p** | Solo dos opciones, para que elegir sea rápido. 720p ahorra espacio y batería; 1080p da más detalle. |
| 📚 **Historial** | Lista de todas las grabaciones con fecha, duración y tamaño. |
| ▶️ **Reproductor propio** | Ve tus vídeos sin salir de la app (Media3 ExoPlayer). |
| 🔔 **Notificación con acción** | Detén la grabación desde la notificación, sin desbloquear. |
| 🎙️ **Audio opcional** | Activa o desactiva el micrófono. |
| 📱 **Frontal o trasera** | Selector de cámara. |
| 🧠 **Ajustes persistentes** | DataStore recuerda tus preferencias entre sesiones. |

## Capturas

_Próximamente._

## Descargar

El APK listo para instalar está en el repositorio:

```
dist/PocketRec-1.0.apk
```

También está adjunto a la [release v1.0](../../releases/latest).

> [!WARNING]
> Este APK es una build de **debug** firmada con la clave de depuración de Android. Sirve para probar la app e instalarla manualmente, pero **no** para publicarla en Google Play.

### Instalación

1. Descarga `PocketRec-1.0.apk` al teléfono.
2. Permite la instalación desde orígenes desconocidos para tu navegador o gestor de archivos.
3. Abre el APK e instala.
4. Concede los permisos de cámara, micrófono y notificaciones al primer arranque.

## Cómo se usa

1. Elige cámara, resolución y si quieres audio.
2. Si quieres un límite, escribe los minutos en **Parada automática**.
3. Pulsa **Iniciar grabación** y bloquea el teléfono.
4. Para terminar: pulsa **Detener grabación**, usa la acción *Detener* de la notificación, o espera al temporizador.
5. Los vídeos aparecen en **Historial**, donde puedes reproducirlos, compartirlos o borrarlos.

## Permisos

| Permiso | Para qué |
|---|---|
| `CAMERA` | Captura de vídeo. |
| `RECORD_AUDIO` | Pista de audio (opcional, se puede desactivar). |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_CAMERA` + `FOREGROUND_SERVICE_MICROPHONE` | Mantener la grabación viva en segundo plano (obligatorio desde Android 14). |
| `POST_NOTIFICATIONS` | Notificación persistente con el botón de detener. |
| `WAKE_LOCK` | Evitar que el sistema duerma el dispositivo y corte la grabación. |

No se envía ningún dato a Internet. La app no declara el permiso `INTERNET`.

## Dónde se guardan los vídeos

```
Android/data/com.example.backgroundvideo/files/Movies/BackgroundVideo/
```

Son accesibles desde la pestaña **Historial** de la app. Al desinstalar la app, el sistema elimina esta carpeta.

## Compilar desde el código

Requisitos:

- Android Studio (Ladybug o superior)
- JDK 17
- Android SDK con `compileSdk 37`

```bash
./gradlew app:assembleDebug
```

El APK resultante queda en `app/build/outputs/apk/debug/app-debug.apk`.

Para regenerar el APK de `dist/`:

```bash
./gradlew app:assembleDebug
copy app\build\outputs\apk\debug\app-debug.apk dist\PocketRec-1.0.apk
```

## Arquitectura

```
app/src/main/java/com/example/backgroundvideo/
├── MainActivity.kt              # Punto de entrada, aloja la navegación
├── data/
│   ├── RecordingSettings.kt     # Ajustes + repositorio DataStore
│   ├── RecordingEntry.kt        # Modelo de historial + escaneo de disco
│   └── RecordingSession.kt      # StateFlow compartido servicio ↔ UI
├── recording/
│   ├── CameraVideoRecorder.kt   # CameraX sin dependencia de Activity
│   └── RecordingService.kt      # Foreground service, wake lock, temporizador
└── ui/
    ├── AppNavigation.kt         # NavHost + barra inferior
    ├── RecordScreen.kt          # Pantalla de grabación
    ├── HistoryScreen.kt         # Historial
    ├── VideoPlayerDialog.kt     # Reproductor ExoPlayer
    ├── HelpScreen.kt            # Ayuda y créditos
    └── MainViewModel.kt / HistoryViewModel.kt
```

### Decisiones técnicas

- **`ServiceLifecycleOwner` propio.** CameraX necesita un `LifecycleOwner`; usar el del servicio permite grabar sin Activity viva.
- **Sin `Preview` enlazado.** La app está pensada para el bolsillo: no hay preview que consuma recursos ni que el sistema desenfoque al bloquear la pantalla.
- **`startForeground()` antes de abrir la cámara.** En Android 14+ abrir la cámara sin un FGS de tipo `camera` activo lanza excepción.
- **Historial derivado del sistema de archivos.** Sin base de datos: nunca se desincroniza de lo que realmente existe en disco.
- **Barra de navegación oculta durante la grabación.** Evita cambios de pantalla accidentales dentro del bolsillo.

## Limitaciones conocidas

- Algunos fabricantes (Xiaomi, Huawei, Samsung, Honor…) aplican optimizaciones de batería agresivas. Si la grabación se corta en reposo prolongado, hay que excluir PocketRec de esas optimizaciones en los ajustes del sistema.
- El `WAKE_LOCK` tiene un tope de 12 horas por sesión.
- Los vídeos no aparecen en la galería del sistema; se gestionan desde el historial de la app.

## Aviso legal

Graba solo donde esté permitido. La normativa sobre captación de imagen y sonido en espacios públicos y privados varía según el país.

## Créditos

Hecho por **otaplus** con:

- [Android CameraX](https://developer.android.com/media/camera/camerax)
- [AndroidX Media3](https://developer.android.com/media/media3)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore)

## Licencia

[MIT](LICENSE) © 2026 otaplus
