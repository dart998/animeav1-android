# AnimeAV1 Android — especificación funcional

Este documento es la fuente de verdad funcional de la aplicación Android. La aplicación combina AnimeAV1 web con capacidades Android nativas sin sustituir innecesariamente la interfaz original.

## 1. Base WebView
- AnimeAV1 se ejecuta dentro de un WebView.
- JavaScript, DOM Storage, sesión y cookies persistentes están habilitados.
- El inicio de sesión se realiza en la web original y se conserva entre ejecuciones.
- La apariencia original se mantiene siempre que sea posible.
- La navegación online y la biblioteca local deben sentirse como una única aplicación.

## 2. Navegación inferior permanente
Orden fijo: **Inicio → Descargas → Horario → Mis Listas → Mi cuenta**.

- `Descargas` sustituye a `Catálogo`.
- La barra permanece visible durante la navegación normal y en las páginas locales.
- Cada elemento lleva directamente a su sección.
- La barra inferior original de la web se oculta para evitar duplicados; Android mantiene una barra equivalente y estable.

## 3. Gestos laterales
En las cinco secciones principales:
- swipe izquierda: siguiente sección;
- swipe derecha: sección anterior.

El gesto no actúa sobre reproductores, iframes, carruseles, sliders, enlaces, botones, inputs, textareas, selects ni contenido editable, y exige predominio claro del movimiento horizontal.

## 4. Descarga individual de episodios
Desde la página de un episodio:
- la acción Descargar se integra con el gestor Android;
- se identifican serie, episodio, página y origen;
- se resuelven enlaces Mega;
- se descarga al almacenamiento privado de la aplicación;
- se muestra progreso;
- se evita duplicar descargas existentes o en cola;
- la descarga continúa al navegar por otras páginas.

## 5. Cola secuencial
Los episodios se descargan uno a uno. Estados mínimos:
- pendiente;
- resolviendo;
- descargando;
- completado;
- cancelado;
- error.

La cola evita duplicados.

## 6. Descargar no vistos de una serie
En la página de una serie existe un control junto a Favoritos/Compartir con icono de descarga + ojo tachado.

- Consulta el progreso real de la biblioteca AnimeAV1.
- Usa `último visto + 1` hasta `último episodio publicado`.
- El número publicado se obtiene de episodios realmente presentes, no del total planificado.
- Excluye episodios ya descargados o activos en cola.
- La acción se confirma antes de añadir el lote.

## 7. Descarga masiva de no vistos
Desde Descargas se puede buscar contenido no visto de las series en estado “Viendo”.

- Se consulta la biblioteca.
- Para cada serie se determina el último episodio realmente publicado.
- Se añaden a la cola los episodios pendientes.
- La cola continúa siendo secuencial.
- Al terminar se muestra un resumen global del lote.

## 8. Integración con la biblioteca AnimeAV1
La aplicación puede leer:
- series;
- slug exacto;
- estado;
- episodios vistos;
- progreso.

Los cambios de biblioteca detectados en la web fuerzan una sincronización local.

## 9. Limpieza automática de vistos
Tras sincronizar la biblioteca, los episodios locales ya marcados como vistos se eliminan automáticamente para ahorrar almacenamiento.

## 10. Sección Descargas
`Descargas` es una sección integrada en la misma Activity/WebView, no una aplicación o Activity visual independiente.

Muestra:
- serie y episodio;
- estado;
- progreso y tamaño cuando estén disponibles;
- acciones adecuadas según estado.

## 11. Acciones sobre descargas
Según el estado:
- Ver;
- Cancelar;
- Borrar;
- Reintentar;
- eliminar un registro fallido/cancelado.

## 12. Reproducción local
- Un episodio completado puede abrirse desde Descargas.
- Si se visita online un episodio ya descargado, el reproductor puede utilizar el fichero local.
- El fichero local se sirve con soporte de peticiones HTTP Range para permitir seek.

## 13. Reproducción completamente offline
La reproducción local no depende de AnimeAV1 ni de Internet: usa el fichero almacenado y un reproductor local dentro de la app.

## 14. Arranque offline
La aplicación puede arrancar sin red.

En vez de una página en blanco muestra una landing local con el mensaje de que no hay conexión y de que únicamente están disponibles los episodios descargados.

## 15. Descargas completamente offline
La sección Descargas funciona sin Internet usando exclusivamente SQLite y el almacenamiento local.

## 16. Acciones offline
Sin conexión siguen disponibles:
- abrir Descargas;
- consultar los episodios descargados;
- reproducirlos;
- borrar archivos y registros locales;
- volver a la landing offline.

Las acciones que necesitan AnimeAV1 informan de que requieren conexión.

## 17. Recuperación de conexión
Si la conexión vuelve, la app recupera la navegación online sin necesidad de cerrarse y volver a abrirse.

## 18. Notificaciones durante descarga
La descarga activa utiliza un servicio foreground y muestra una notificación con progreso y acción **Cancelar**.

## 19. Notificaciones al finalizar
Al terminar se ofrece **Ver**.

- Descarga individual: abre el capítulo correspondiente.
- Descarga masiva: abre la página principal de la serie.
- Si se abre una descarga individual sin conexión y el fichero existe, se abre el reproductor local.

## 20. Enlaces e imágenes que solicitan nueva ventana
No existe un gestor de pestañas visible. Los `target=_blank` y `window.open` se capturan y se abren en el mismo WebView/historial, de forma que Atrás siempre permite regresar.

## 21. Botón Atrás
Atrás debe funcionar correctamente desde páginas web, series, episodios, Descargas, imágenes/enlaces externos, reproductor local y landing offline.

## 22. Identidad visual
Se utiliza el logo turquesa proporcionado como recurso real de launcher y round icon, y un icono monocromo específico para las notificaciones.

## 23. Versión visible
Debajo de “By fans for fans” se muestra `AnimeAV1 Android vX.Y.Z`, generado desde `BuildConfig.VERSION_NAME`.

## 24. Integración visual
Las páginas locales usan paleta oscura, turquesa, tipografía y espaciado compatibles con AnimeAV1. Las funciones Android deben sentirse integradas y no como otra app.

## 25. Arquitectura híbrida
Online: AnimeAV1 proporciona Inicio, series, episodios, cuenta, biblioteca, horario, comentarios y progreso.

Local: Android proporciona gestor de descargas, cola, SQLite, ficheros MP4, reproducción offline, notificaciones, landing offline y gestión de almacenamiento.

`Descargas` une ambos mundos.

## 26. Objetivo del producto
**AnimeAV1 Android = AnimeAV1 web + gestor de descargas + biblioteca local + reproducción offline + integración Android.**

Con Internet se conserva la experiencia web. Sin Internet, la aplicación se convierte en una biblioteca/reproductor de los episodios previamente descargados.
