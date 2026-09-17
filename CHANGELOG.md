# Historial de AnimeAV1 Android

Resumen de los cambios presentes en el historial Git, agrupados por versión publicada. Los commits intermedios de desarrollo y corrección de workflows se integran en la versión a la que pertenecen.

| Versión | Cambios |
| --- | --- |
| 1.0.0 | Proyecto Android inicial, WebView con cookies, tema, icono, APK en GitHub Actions y modo de navegación inmersivo. |
| 1.0.1 | Nuevo icono visual inspirado en AnimeAV1. |
| 1.0.2 | Bloqueo nativo de anuncios y recursos no deseados en la WebView. |
| 1.0.3 | Clave estable de firma y verificación de la firma del APK de release. |
| 1.0.4 | Mejoras de área segura, barra de estado y bloqueo de ventanas emergentes. |
| 1.0.5 | Color de barra de estado acorde con la página. |
| 1.0.6 | Vídeo HTML5 a pantalla completa. |
| 1.0.7 | Gesto para actualizar la página. |
| 1.0.8 | Mantener la pantalla encendida durante vídeo a pantalla completa. |
| 1.1.0–1.1.2 | Descarga offline inicial; integración con menú y botón del episodio; enlace y resolución de Mega; ajustes del menú original. |
| 1.2.0–1.2.1 | Estado de biblioteca, cola secuencial, descarga de no vistos por serie y para varias series, limpieza de episodios vistos y acceso restringido a servidores Mega. |
| 1.3.0–1.3.1 | Estados de descarga, cancelación, notificaciones con acciones, gestión de archivos y reproductor local; primeras pantallas de Descargas y ajuste del cálculo de episodios publicados. |
| 1.4.0–1.4.2 | Descargas integrada en la misma WebView, aislamiento de fondo, navegación y versión visible en el footer. |
| 1.4.3 | Cambios en JS del menú, gestos, ventanas emergentes y notificaciones; se observaron regresiones de scroll y del botón Descargas. |
| 1.4.4 | PNG turquesa original para icono, notificación monocroma, correcciones del selector del menú y de la navegación de nuevas ventanas. Persistieron fallos del menú y arranques en blanco observados en dispositivo. |
| 1.5.0 | Rediseño de navegación como barra nativa permanente; vista local de Descargas simplificada; landing offline, recuperación de conexión y del proceso WebView; resumen de lotes; icono adaptable con el PNG original; documentación completa. |

La versión 1.5.0 conserva la identidad de paquete, la base de datos `offline.db` y los MP4 existentes. El cambio de arquitectura interna no rompe la compatibilidad de datos.
