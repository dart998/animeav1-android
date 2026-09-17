# Arquitectura de AnimeAV1 Android 2

La reconstrucción 2.x elimina la dependencia de modificar dinámicamente la barra inferior de AnimeAV1 y separa responsabilidades.

## Componentes

- `MainActivity`: ciclo de vida, WebView, navegación, conectividad, integración JS y coordinación de descargas.
- `SiteIntegration`: JavaScript mínimo para integrar acciones de descarga, botón “no vistos”, versión del footer, nuevas ventanas y swipe. No reposiciona el documento ni fuerza scroll.
- `LocalContentServer`: genera landing offline, biblioteca Descargas y reproductor local como páginas del mismo WebView.
- `DownloadService`: cola secuencial, resolución Mega, foreground service, progreso y notificaciones.
- `EpisodeStore`: SQLite local con el estado de las descargas.
- `AnimeAv1LibraryClient`: lectura del progreso de la biblioteca de AnimeAV1.
- `AnimeAv1SeriesClient`: obtiene el último episodio realmente publicado de una serie.
- `OfflineVideoServer`: sirve MP4 local con soporte Range.
- `AdBlocker`: filtrado ya utilizado por el WebView.

## Navegación

La barra inferior es nativa, fija y externa al DOM de AnimeAV1. Esto evita depender de clases o nodos que la SPA pueda recrear. La barra web original se oculta mediante una integración mínima.

Rutas:
- Inicio: `https://animeav1.com/`
- Descargas: `https://app.animeav1.local/downloads`
- Horario: `https://animeav1.com/horario`
- Mis Listas: `https://animeav1.com/cuenta/listas`
- Mi cuenta: `https://animeav1.com/cuenta`

## Offline

Las páginas `app.animeav1.local` se sirven desde memoria por `LocalContentServer`; nunca necesitan red. Los vídeos se sirven desde `offline.animeav1.local` mediante `OfflineVideoServer`.

Al arrancar sin red se carga `/offline`. Si se recupera una red validada, la navegación online vuelve a estar disponible sin reiniciar la Activity.

## Seguridad de la interfaz JS

La interfaz JavaScript expone exclusivamente acciones cerradas: descargar episodio, descargar no vistos, navegación, acciones locales, sincronización de biblioteca y apertura controlada de URL. Todas las entradas se vuelven a validar en Java.

## Versionado

La reconstrucción se prepara como 2.0.0 porque reemplaza la arquitectura de navegación/UI de la serie 1.x. Hasta que se publique, el trabajo permanece fuera de `main`.
