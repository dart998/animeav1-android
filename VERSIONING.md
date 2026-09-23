# Política de versiones

AnimeAV1 Android sigue versionado semántico (`MAJOR.MINOR.PATCH`).

- `MAJOR`: cambios incompatibles o reconstrucciones profundas.
- `MINOR`: funcionalidades nuevas compatibles.
- `PATCH`: correcciones compatibles.

## Release Candidates

Toda versión pasa primero por una o más candidatas con el formato
`MAJOR.MINOR.PATCH-rc.N`.

1. Los cambios se acumulan sin publicar.
2. La primera candidata se publica como `rc.1` y GitHub la marca como prerelease.
3. Cada corrección detectada durante las pruebas incrementa `N`.
4. La versión estable se publica solo tras validar los APK móvil y TV en los
   dispositivos correspondientes y no mantener errores bloqueantes conocidos.
5. Las releases estables y sus etiquetas son inmutables y no se reutilizan.

Cada RC y versión estable debe incluir changelog, resumen funcional, pruebas,
compilación de ambos APK y verificación de sus firmas.
