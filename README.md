# AnimeAV1 Android

Cliente Android híbrido de AnimeAV1. Conserva la web, la sesión y sus páginas, y añade una biblioteca local con cola secuencial de descargas, reproducción offline y notificaciones nativas.

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
- El mismo APK funciona en móvil, Android TV, Google TV y Fire TV, con navegación mediante mando.

El [resumen completo de funcionalidades](FEATURES.md) se mantiene separado del [historial de cambios por versión](CHANGELOG.md).

Los vídeos se guardan en el directorio privado externo de la aplicación (`Android/data/com.ovelayos.animeav1/files/Movies/AnimeAV1`). Desinstalar la aplicación puede eliminar ese contenido.

## Android TV y Fire TV

La misma APK sirve para móvil, Android TV, Google TV y Fire TV. En televisores se muestra en horizontal, añade foco visible para el D-pad y permite controlar la reproducción con el mando.

Para uso personal puede instalarse manualmente con ADB:

```bash
adb connect IP_DEL_TELEVISOR
adb install -r AnimeAV1-vX.Y.Z.apk
```

La versión mínima es Android 6.0 (API 23), por lo que en Fire TV es compatible con Fire OS 6 y posteriores.

## Arquitectura

- `MainActivity`: WebView, navegación, modo offline e integración JavaScript mínima.
- `DownloadStore`: fuente de verdad SQLite para cola y biblioteca local.
- `DownloadService`: servicio foreground y consumidor secuencial de la cola persistente.
- `MegaClient`: resolución y descifrado AES-CTR de enlaces públicos de archivo.
- `AnimeAv1Client`: biblioteca, progreso y episodios publicados.
- `OfflinePlayerActivity`: reproducción desde archivo, sin acceso a AnimeAV1.

## Compilación

Requiere JDK 17, Android SDK 36 y Gradle 9.3.1:

```bash
gradle assembleDebug
```

El workflow de GitHub Actions compila y firma `assembleRelease`. En `main` crea la release correspondiente a `versionName` usando su sección de `CHANGELOG.md` y el resumen vigente de `FEATURES.md`. La publicación falla si falta el changelog de esa versión.
