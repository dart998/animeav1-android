# AnimeAV1 Android

Cliente Android híbrido para `https://animeav1.com/`: conserva la experiencia web y añade descargas, cola, biblioteca local, reproducción offline y funciones Android.

## Especificación

La lista funcional completa está en [`docs/FEATURES.md`](docs/FEATURES.md). La arquitectura de la reconstrucción 2.x está en [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) y el plan de validación en [`docs/TEST_PLAN.md`](docs/TEST_PLAN.md).

## Funciones principales

- WebView con sesión/cookies persistentes.
- Navegación fija: Inicio → Descargas → Horario → Mis Listas → Mi cuenta.
- Descarga individual y por lotes, con cola secuencial.
- Resolución y descarga desde Mega.
- Descargas no vistas por serie y para series en “Viendo”.
- Biblioteca local con cancelar, reintentar, borrar y reproducir.
- Reproducción MP4 local con seek y funcionamiento totalmente offline.
- Landing offline al arrancar sin conexión.
- Limpieza automática de episodios ya vistos.
- Notificaciones de progreso con Cancelar y finalización con Ver.
- Swipe entre las cinco secciones principales.
- Integración de enlaces/nuevas ventanas en el mismo historial.
- Logo AnimeAV1 como launcher icon y versión visible en el footer.

## Compilación

El proyecto usa Java 17, `compileSdk 36`, `minSdk 23` y `targetSdk 36`. GitHub Actions genera un APK release firmado cuando están disponibles los secrets de firma. Las ramas de reconstrucción generan artefactos, pero GitHub Release solo se publica desde `main`.
