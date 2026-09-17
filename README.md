# AnimeAV1 Android

Aplicación Android híbrida: AnimeAV1 se muestra en una WebView con la sesión y el diseño del sitio; la app aporta navegación permanente, descargas secuenciales, biblioteca local y reproducción sin Internet. Paquete: `com.ovelayos.animeav1`. Java 17, `minSdk 23`, `targetSdk 36`.

## Navegación

La barra inferior es una vista Android situada fuera de la WebView, por lo que no depende de que la página haya cargado ni de que la SPA reconstruya su footer. Orden: Inicio, Descargas, Horario, Mis listas, Mi cuenta. Las otras cuatro opciones cargan las rutas de AnimeAV1; Descargas se representa dentro de la misma WebView, sobre el contenido web, y usa la base de datos local. El footer web original se oculta al detectarlo. Los gestos horizontales solo actúan en esas cinco secciones y excluyen controles que reciben gestos propios.

## Online y offline

- Las cookies de WebView persisten y se vacían al pausar o terminar una navegación. El login lo gestiona AnimeAV1.
- Si no hay red al arrancar, se carga una landing HTML local con acceso a Descargas y Reintentar. Un fallo de red de la página principal también abre esa landing sin borrar la sesión.
- Si vuelve la conexión mientras se ve la landing, la app vuelve a cargar la última ruta online solicitada. Los botones de secciones online indican que requieren Internet cuando el dispositivo no dispone de red.
- Los registros están en SQLite (`offline.db`) y los MP4 en el almacenamiento local de la app. `OfflineVideoServer` sirve el MP4 con soporte de rangos desde `offline.animeav1.local`; la reproducción no solicita datos a AnimeAV1.
- La vista de Descargas se consulta y gestiona sin Internet. El botón Atrás cierra primero Descargas; desde un vídeo local vuelve a la biblioteca.

## Descargas

El botón de descarga de un episodio envía su identificador y URL a `DownloadService`. El servicio resuelve enlaces Mega a partir del enlace recibido o del HTML del episodio, procesa la cola de uno en uno, evita duplicados y guarda estados de pendiente, resolviendo, descargando, completado, error y cancelado. La notificación activa muestra progreso, cola y Cancelar; una descarga individual terminada ofrece Ver y abre la página del episodio. Un lote termina con una notificación de resumen: abre la serie si solo hay una o Descargas si abarca varias.

El botón situado junto a Compartir en la ficha de una serie obtiene los episodios publicados, consulta el progreso real de la biblioteca y añade los no vistos que faltan. La descarga global consulta las series en estado Viendo. El estado de la biblioteca se sincroniza para eliminar los MP4 que pasan a estar vistos. En Descargas se puede cancelar, quitar de la cola, reproducir, borrar y reintentar, según el estado.

## Compilación y release

`gradle assembleRelease` requiere las variables `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` y `ANDROID_KEY_PASSWORD`. `.github/workflows/build-apk.yml` restaura el keystore desde secretos, compila, comprueba la firma con `apksigner`, sube el artefacto y publica `AnimeAV1-vX.Y.Z.apk` **solo en pushes a `main`**. Las ramas `rewrite/**` y `fix/**` ejecutan compilación y verificación sin publicar una release. El texto del footer usa `BuildConfig.VERSION_NAME`.

Consulta [CHANGELOG.md](CHANGELOG.md) para la evolución desde 1.0.0 y [docs/verification.md](docs/verification.md) para los escenarios que deben comprobarse antes de publicar.
