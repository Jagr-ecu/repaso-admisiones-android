---
status: aceptado
date: 2026-11-06
decision-makers: Responsable del proyecto
---

# App Android nativa con Kotlin y Jetpack Compose

## Contexto y problema

La versión web necesita el PC y un navegador. Hace falta una app que funcione sin Internet en tablets y celulares.

## Factores de la decisión

* Funcionar sin conexión, con progreso local.
* Una sola base de pantallas para tablet y celular.
* Dibujar fórmulas LaTeX avanzadas igual que en la web.
* Instalarse en la MatePad sin servicios de Google.

## Opciones consideradas

* Kotlin y Jetpack Compose
* PWA (la web instalable)
* Multiplataforma (Flutter o React Native)

## Decisión

Opción elegida: "Kotlin y Jetpack Compose", porque da acceso directo a Room, WorkManager y WebView, y
`WindowSizeClass` resuelve tablet y celular con las mismas pantallas.

### Consecuencias

* Bueno: lógica pura en Kotlin (`:dominio`) que se prueba en la JVM.
* Bueno: control total del WebView para KaTeX.
* Malo: hay que portar a Kotlin la lógica de Python y mantener las dos versiones alineadas (ver ADR-0006).
* Malo: no hay versión para iOS.

### Confirmación

`./gradlew :app:assembleDebug` y prueba en la MatePad real.

## Pros y contras de las opciones

### PWA

* Bueno: reutiliza la web.
* Malo: depende del navegador de HarmonyOS y de su almacenamiento, que puede borrarse.

### Flutter o React Native

* Bueno: también serviría para iOS.
* Malo: otro lenguaje que aprender y otra capa sobre Firebase sin servicios de Google.

## Más información

`PLAN-MIGRACION.md`, sección 1.
