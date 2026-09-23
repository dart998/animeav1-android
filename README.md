# AnimeAV1 Android

Cliente Android híbrido de AnimeAV1 distribuido en dos APK: uno para móvil con biblioteca local y otro específico para televisión que conserva la web original.

## Funciones

- Navegación inferior permanente: Inicio, Descargas, Horario, Mis Listas y Mi cuenta.
- Descarga individual desde el botón de cada episodio y descarga de capítulos no vistos desde una serie.
- Lotes para todas las series en estado `Viendo`, consultando el progreso real de Mis Listas.
- Cola persistente de una sola descarga, sin duplicados, con estados, cancelación y reintento.
- Resolución y descifrado local de enlaces públicos de Mega.
- Biblioteca y reproductor local disponibles sin Internet.
- Landing offline y recuperación automática al volver la conexión.
- Limpieza automática de copias locales cuando la biblioteca las marca como vistas.
- Notificaciones de progreso, cancelar y ver.
- Versión del footer generada desde `BuildConfig.VERSION_NAME`.

El [resumen completo de funcionalidades](FEATURES.md) se mantiene separado del [historial de cambios por versión](CHANGELOG.md). Las publicaciones siguen la [política de versionado y Release Candidates](VERSIONING.md).

Los vídeos del APK móvil se guardan en el directorio privado externo de la aplicación (`Android/data/com.dart998.animeav1/files/Movies/AnimeAV1`). Desinstalar la aplicación puede eliminar ese contenido.

## APK móvil

El APK `AnimeAV1-Mobile-vX.Y.Z.apk` utiliza el identificador `com.dart998.animeav1` y mantiene las funciones móviles de la versión 2.0.5.

## Android TV y Fire TV

El APK `AnimeAV1-TV-vX.Y.Z.apk` usa el identificador independiente `com.dart998.animeav1.tv`. Funciona en horizontal y a pantalla completa, sin menú lateral nativo ni descargas. La web se conserva visualmente original, con bloqueo publicitario y soporte de mando.

Para uso personal puede instalarse manualmente con ADB:

```bash
adb connect IP_DEL_TELEVISOR
adb install -r AnimeAV1-TV-vX.Y.Z.apk
```

La versión mínima es Android 6.0 (API 23), por lo que en Fire TV es compatible con Fire OS 6 y posteriores.

## Arquitectura

- `mobile/MainActivity`: WebView móvil, navegación, modo offline e integración de descargas.
- `tv/MainActivity`: WebView de TV independiente, a pantalla completa y sin funciones de descarga.
- `DownloadStore`: fuente de verdad SQLite para cola y biblioteca local.
- `DownloadService`: servicio foreground y consumidor secuencial de la cola persistente.
- `MegaClient`: resolución y descifrado AES-CTR de enlaces públicos de archivo.
- `AnimeAv1Client`: biblioteca, progreso y episodios publicados.
- `OfflinePlayerActivity`: reproducción desde archivo, sin acceso a AnimeAV1.

## Compilación

Requiere JDK 17, Android SDK 36 y Gradle 9.3.1:

```bash
gradle assembleMobileDebug assembleTvDebug
```

El workflow de GitHub Actions prueba, compila y firma las variantes `mobileRelease` y `tvRelease`. En `main` publica ambos APK en la release correspondiente a `versionName`, usando su sección de `CHANGELOG.md` y el resumen vigente de `FEATURES.md`.
