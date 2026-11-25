---
status: aceptado
date: 2026-11-09
decision-makers: Responsable del proyecto
---

# `minSdk` 29 y `targetSdk` 36

## Contexto y problema

HarmonyOS 4.3 es compatible con un nivel de Android anterior a 12, y no muestra cuál. El plan inicial usaba `minSdk` 31.

## Factores de la decisión

* Que la app se instale en la MatePad.
* Usar librerías actuales de Compose y Firebase.
* Evitar el permiso de red local mientras no haga falta.

## Opciones consideradas

* `minSdk` 29 (Android 10), `targetSdk` 36
* `minSdk` 31 (Android 12)

## Decisión

Opción elegida: "`minSdk` 29 y `targetSdk` 36". `compileSdk` es la última estable.

### Consecuencias

* Bueno: se instala y funciona en la MatePad.
* Bueno: con `targetSdk` 36 no hace falta pedir `ACCESS_LOCAL_NETWORK` para hablar con LM Studio.
* Malo: Android 16 ignora el bloqueo de orientación en pantallas grandes, así que la tablet debe admitir las dos.
* Malo: al subir a `targetSdk` 37 habrá que pedir el permiso de red local y manejar que se niegue.

### Confirmación

`app/build.gradle.kts`. Pruebas en un emulador con Android 10 y otro con Android 16.

## Más información

`PLAN-MIGRACION.md`, sección 5, "Versiones de Android".
