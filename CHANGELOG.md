# Historial de cambios

Este archivo recoge los cambios visibles de cada versión de AnimeAV1 Android.

## [Sin publicar]

## [2.2.0] - 2026-09-22

### Cambios

- Separados móvil y TV en dos APK firmados e independientes.
- El APK móvil recupera íntegramente el comportamiento de la versión 2.0.5 y elimina las adaptaciones introducidas desde la 2.1.0.
- El APK de TV usa toda la pantalla en orientación horizontal, sin navegación lateral nativa, biblioteca local ni funciones de descarga.
- La variante de TV conserva la web original y añade únicamente bloqueo publicitario, ventanas seguras y navegación con mando.
- El icono de capas o rombos se sustituye por una flecha de descarga en los menús de ambas variantes.
- Identificadores sustituidos por `com.dart998.animeav1` para móvil y `com.dart998.animeav1.tv` para TV.

### Importante

- El cambio de identificador hace que Android instale esta versión como una aplicación distinta de las versiones antiguas con otro identificador. La sesión y las descargas privadas anteriores no se migran automáticamente.

## [2.1.2] - 2026-09-21

### Corregido

- Mi cuenta abre el diálogo oficial de inicio de sesión de AnimeAV1 en lugar de navegar a una ruta protegida que responde 401.
- El formulario de acceso y la validación Turnstile de Cloudflare participan en la navegación con D-pad en Android TV.

## [2.1.1] - 2026-09-21

### Cambios

- Menú de TV reducido a 72 dp, sin margen exterior desperdiciado y con proporciones equivalentes al menú original.
- Icono de Descargas sustituido por una flecha descendente.
- Ocultado el menú inferior de la web en TV para conservar únicamente la navegación lateral nativa.

### Corregido

- Recorrido del D-pad determinista: el foco prioriza la misma fila o columna y no escapa del menú lateral al pulsar derecha.
- Mi cuenta redirige a la página de acceso cuando AnimeAV1 devuelve 401 por no existir una sesión iniciada.

## [2.1.0] - 2026-09-21

### Añadido

- Compatibilidad con Android TV, Google TV y Fire TV en el mismo APK.
- Entrada en el launcher Leanback con banner propio de AnimeAV1.
- Navegación mediante mando y D-pad con foco visual en la web, el menú y las vistas locales.
- Control de reproducción desde el mando: reproducir/pausar y saltos de 10 segundos.

### Cambios

- Interfaz de TV adaptada a visualización a distancia, área segura y orientación horizontal sin alterar las dimensiones de la interfaz móvil.

## [2.0.5] - 2026-09-21

### Cambios

- Eliminada la navegación entre secciones mediante gestos laterales.
- Reducido el menú de navegación a 60 dp y acercados los títulos a sus iconos para recuperar las proporciones de la aplicación original.

## [2.0.4] - 2026-09-20

### Añadido

- Acceso a Discord desde el botón de la cabecera.
- Navegación lateral también desde la sección nativa Descargas.
- Pruebas para el orden de selección de fuentes de vídeo.

### Cambios

- Las descargas eligen siempre la fuente `SUB`; usan una fuente sin etiqueta como alternativa y `DUB` únicamente como último recurso.
- Mis Listas abre directamente la categoría `Viendo`.
- La navegación lateral pasa a ser circular entre Inicio y Mi cuenta.

### Corregido

- Ocultación completa de las barras de Android durante la reproducción a pantalla completa, tanto online como offline.
- Resolución más fiable de enlaces y fuentes de descarga.

## [2.0.3] - 2026-09-20

### Añadido

- Barra de navegación lateral al girar el dispositivo, manteniendo los botones en una posición fija.
- Bloqueo nativo de destinos publicitarios conocidos.

### Cambios

- Integración visual de Descargas con los colores, tarjetas, tipografía y controles de AnimeAV1.
- Unificación de los fondos de la barra superior, el contenido y la navegación.
- Ajuste de la barra inferior, sus iconos y títulos tomando como referencia la versión anterior de la aplicación.

### Corregido

- Respeto de las áreas seguras superiores para evitar contenido bajo el reloj y los iconos del sistema.
- El selector de modo nocturno/diurno ya no permite que una redirección publicitaria sustituya la página.

## [2.0.2] - 2026-09-20

### Añadido

- Iconos propios para Inicio, Descargas, Horario, Mis Listas y Mi cuenta.

### Cambios

- Restaurado el tamaño visual de la navegación inferior.
- Rediseñada la biblioteca de descargas para integrarla en la pantalla principal.

## [2.0.1] - 2026-09-20

### Corregido

- La barra de navegación del sistema se oculta automáticamente y puede mostrarse temporalmente deslizando desde el borde inferior.

## [2.0.0] - 2026-09-19

### Añadido

- Reconstrucción completa de la aplicación híbrida AnimeAV1 Android.
- WebView con persistencia de sesión, cookies y navegación integrada.
- Navegación permanente entre Inicio, Descargas, Horario, Mis Listas y Mi cuenta.
- Cola secuencial y persistente de descargas con resolución de enlaces públicos de Mega.
- Descarga individual, descarga de episodios no vistos y lotes de series en estado `Viendo`.
- Biblioteca local, reproductor offline, landing sin conexión y recuperación automática de conectividad.
- Notificaciones Android de progreso con acciones para cancelar y reproducir.
- Limpieza de archivos locales al detectar episodios vistos en la biblioteca de AnimeAV1.
- Versión de la aplicación en el footer obtenida desde `BuildConfig.VERSION_NAME`.

[Sin publicar]: https://github.com/dart998/animeav1-android/compare/v2.2.0...HEAD
[2.2.0]: https://github.com/dart998/animeav1-android/releases/tag/v2.2.0
[2.1.2]: https://github.com/dart998/animeav1-android/releases/tag/v2.1.2
[2.1.1]: https://github.com/dart998/animeav1-android/releases/tag/v2.1.1
[2.1.0]: https://github.com/dart998/animeav1-android/releases/tag/v2.1.0
[2.0.5]: https://github.com/dart998/animeav1-android/releases/tag/v2.0.5
[2.0.4]: https://github.com/dart998/animeav1-android/releases/tag/v2.0.4
[2.0.3]: https://github.com/dart998/animeav1-android/releases/tag/v2.0.3
[2.0.2]: https://github.com/dart998/animeav1-android/releases/tag/v2.0.2
[2.0.1]: https://github.com/dart998/animeav1-android/releases/tag/v2.0.1
[2.0.0]: https://github.com/dart998/animeav1-android/releases/tag/v2.0.0
