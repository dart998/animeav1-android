# Plan de validación funcional 2.x

Antes de publicar una release se deben validar como mínimo estos escenarios en dispositivo real:

1. Arranque online con sesión previa y sin sesión.
2. Barra fija: Inicio, Descargas, Horario, Mis Listas y Mi cuenta.
3. Swipe en ambas direcciones entre las cinco secciones y ausencia de interferencia sobre vídeo/carruseles/inputs.
4. Descarga individual completa desde un episodio Mega.
5. Cancelación de descarga desde notificación y desde Descargas.
6. Reintento tras error/cancelación.
7. Cola de varios episodios ejecutándose uno a uno.
8. “Descargar no vistos” de una serie usando último publicado real.
9. Lote global de series en “Viendo”.
10. Resumen final de lote.
11. Reproducción local desde Descargas y seek dentro del vídeo.
12. Sustitución por vídeo local al visitar online un episodio descargado.
13. Limpieza automática tras marcar un episodio como visto.
14. Apertura de imagen/enlace `target=_blank`, seguida de Atrás.
15. Arranque en modo avión: landing offline.
16. Descargas usable en modo avión.
17. Reproducción de MP4 en modo avión.
18. Borrado local en modo avión.
19. Recuperar Wi-Fi/datos y volver a navegación online sin reiniciar.
20. Notificación en curso con Cancelar.
21. Notificación completada con Ver: episodio individual abre capítulo/local y lote abre serie.
22. Icono de launcher y round icon correctos.
23. Icono monocromo de notificación correcto.
24. Footer muestra versión de `BuildConfig.VERSION_NAME`.
25. Atrás desde online, Descargas, player local y landing offline.
26. Rotación/fullscreen de vídeo sin perder navegación ni cola.
