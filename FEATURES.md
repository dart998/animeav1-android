# Funcionalidades de AnimeAV1 Android

La aplicación se distribuye en dos APK independientes, ambos con versión coordinada.

- El acceso representado anteriormente mediante capas utiliza una flecha descendente coherente en móvil y TV.

## APK móvil

## Experiencia AnimeAV1

- AnimeAV1 se ejecuta dentro de un WebView conservando su aspecto, navegación, sesión y cookies.
- Menú permanente con Inicio, Descargas, Horario, Mis Listas y Mi cuenta.
- Mis Listas abre por defecto la categoría `Viendo`.
- Integración de enlaces, imágenes, ventanas emergentes y Discord dentro de una navegación coherente.
- Versión visible bajo “By fans for fans”, obtenida automáticamente de `BuildConfig.VERSION_NAME`.

## Descargas

- Captura del botón de descarga de los episodios y gestión nativa de Android.
- Selección de fuentes en orden `SUB`, sin etiqueta y, solo como último recurso, `DUB`.
- Resolución y descifrado local de enlaces públicos de Mega.
- Cola persistente y secuencial, sin episodios duplicados.
- Estados pendiente, resolviendo, descargando, completado, cancelado y error.
- Progreso, tamaño y acciones para ver, cancelar, retirar, borrar o reintentar.
- Descarga de episodios no vistos desde una serie y descarga masiva de pendientes de las series en estado `Viendo`.
- Resumen conjunto al finalizar los lotes.

## Biblioteca y reproducción offline

- Descargas agrupadas e integradas visualmente como una sección más de AnimeAV1.
- Reproducción desde archivos locales sin depender de AnimeAV1 ni de Internet.
- Landing offline con acceso directo a la biblioteca descargada.
- Arranque sin conexión y recuperación automática cuando vuelve Internet.
- Eliminación automática opcional de copias locales cuando un episodio pasa a estar visto.

## Integración Android

- Servicio de descarga en primer plano que continúa al navegar por la aplicación.
- Notificaciones con episodio, progreso, información de cola y acciones para cancelar o ver.
- Reproductor online y local en modo inmersivo, ocultando las barras del sistema durante la pantalla completa.
- Barra de navegación del sistema oculta normalmente y recuperable de forma temporal desde el borde inferior.
- Navegación Atrás adaptada a páginas web, vistas nativas, contenido externo y reproducción.
- Icono launcher normal, redondo y adaptativo con la identidad de AnimeAV1.

## APK TV

- Aplicación independiente para Android TV, Google TV y Fire TV con identificador `com.dart998.animeav1.tv`.
- Orientación horizontal y uso de toda la superficie disponible, sin franjas ni barras permanentes de Android.
- Web de AnimeAV1 sin menú lateral nativo, biblioteca local ni integración de descargas.
- Aspecto y navegación originales de la web, con bloqueo de anuncios y ventanas emergentes no confiables.
- Navegación con D-pad, acceso al formulario y Turnstile de Cloudflare y controles multimedia del mando.
