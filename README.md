# AnimeAV1 Android

Aplicación Android sencilla basada en WebView para abrir `https://animeav1.com/` y conservar la sesión mediante cookies persistentes del WebView.

## Características

- WebView con JavaScript habilitado.
- DOM Storage habilitado.
- Cookies persistentes.
- Cookies de terceros habilitadas para compatibilidad.
- Navegación atrás dentro del WebView.
- Solo HTTPS.

## Compilación

GitHub Actions genera automáticamente un APK debug en cada push a `main` y también permite ejecución manual desde la pestaña Actions.

El APK se publica como artifact del workflow `Build Android APK`.
