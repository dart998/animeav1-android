# Verificación funcional de 1.5.0

Este documento agrupa los 26 apartados de la especificación del usuario. **Compilación y revisión de código no sustituyen la prueba en un dispositivo real con la sesión de AnimeAV1 y una descarga Mega.** Antes de publicar, registrar aquí el resultado de cada escenario.

| Apartados | Escenario y resultado esperado | Implementación |
| --- | --- | --- |
| 1, 24–26 | Login en WebView y aspecto original; después, activar modo avión y usar biblioteca local. | `MainActivity`, `SiteIntegration`, `EpisodeStore` |
| 2–3 | Los cinco botones son visibles desde el inicio y funcionan tras login, reinicio y scroll; swipe en orden; no se activa en vídeo, slider, galería, input ni scroll vertical. | Barra `activity_main.xml`, `MainActivity.navigateTo`, `SiteIntegration.install` |
| 4–5 | Descargar un episodio Mega, seguir navegando, comprobar seis estados, progreso y bloqueo de duplicados. | `DownloadService`, `EpisodeStore` |
| 6–9 | Descargar no vistos desde ficha y desde series Viendo; excluir vistos, descargados y en cola; verificar limpieza tras marcar visto. | `SiteIntegration`, `AnimeAv1LibraryClient`, `MainActivity` |
| 10–11 | Abrir Descargas desde cualquier ruta y sin red; Ver, Cancelar, Quitar, Borrar y Reintentar según estado; fondo inmóvil y menú visible. | `DownloadsIntegration`, `MainActivity` |
| 12–16, 25 | Abrir app en modo avión, entrar en Descargas, reproducir MP4, borrar, volver a la landing; sin peticiones de vídeo a AnimeAV1. | `MainActivity.showOfflineLanding`, `OfflineVideoServer` |
| 17 | Recuperar la red sin cerrar la app y abrir Inicio, Horario, Mis listas y Mi cuenta. | `MainActivity.watchConnectivity` |
| 18–19 | Ver Cancelar durante la descarga, Ver al terminar; notificación individual abre episodio, lote de una serie abre su ficha y lote multisérie la biblioteca. | `DownloadService` |
| 20–21 | Abrir imagen de comentario y enlace de nueva pestaña en la misma WebView; Atrás desde imagen, web, Descargas, reproductor y landing. | `SiteIntegration`, `MainActivity.onBackPressed` |
| 22–23 | Comprobar PNG original en launcher normal y redondo/adaptable, icono monocromo de notificación y versión 1.5.0 bajo «By fans for fans». | Recursos Android, `SiteIntegration` |

## Resultado automatizado

Pendiente de registrar la ejecución de Actions en la rama y en `main`: compilación, firma y artefacto. Las pruebas de dispositivo de los escenarios anteriores requieren un emulador o teléfono Android con sesión y contenido descargable; no se deducen de un workflow verde.
