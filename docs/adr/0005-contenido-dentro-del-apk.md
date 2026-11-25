---
status: aceptado
date: 2026-11-06
decision-makers: Responsable del proyecto
---

# Contenido generado en Python y empaquetado dentro del APK

## Contexto y problema

Las preguntas se generan con plantillas, expresiones y SymPy en el proyecto web. Hay que decidir cómo llegan a Android.

## Factores de la decisión

* La respuesta correcta la calcula siempre el código, nunca el LLM (regla 1).
* Funcionar sin Internet.
* No llevar un intérprete de Python ni SymPy a Android.

## Opciones consideradas

* Variantes generadas por adelantado (`exportar-paquete`) y guardadas en `assets/contenido/`
* Portar el generador a Kotlin
* Descargar el contenido desde la nube

## Decisión

Opción elegida: "variantes generadas por adelantado". La web sigue siendo donde se escribe y valida el contenido.

### Consecuencias

* Bueno: 127 plantillas y unas 11 900 variantes ocupan cerca de 1 MB comprimido.
* Bueno: el formato se lee con kotlinx.serialization y no acepta campos desconocidos: un cambio de formato no pasa en silencio.
* Malo: actualizar contenido significa publicar un APK nuevo.
* Malo: algunas plantillas tienen pocas variantes, igual que en la web.

### Confirmación

`LectorContenidoTest` lee el curso completo. Los tests de `:dominio` y `:datos` se vuelven a correr si cambia el paquete.

## Más información

* `PLAN-MIGRACION.md`, sección 3.
* [Issue #14](https://github.com/jagr-ecu/repaso-admisiones-android/issues/14): pasar los temas a una base de datos (fuera del MVP).
