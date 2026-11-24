---
status: aceptado
date: 2026-11-09
decision-makers: Responsable del proyecto
---

# Funcionar sin servicios de Google e instalar el APK directamente

## Contexto y problema

La tablet real es una Huawei MatePad 11.5 con HarmonyOS 4.3: instala APK de Android pero no tiene Google Play
ni servicios de Google.

## Factores de la decisión

* Usar el dispositivo que ya existe.
* No depender de Google Play para instalar ni actualizar.

## Opciones consideradas

* No usar APIs que requieran servicios de Google; instalar el APK firmado a mano
* Publicar en Google Play y comprar otro dispositivo

## Decisión

Opción elegida: "sin servicios de Google". Firebase Auth y Firestore funcionan sin ellos (comprobado en la MatePad
en la fase 0 y la fase 1). El APK se firma con una clave propia y se instala directamente.

### Consecuencias

* Bueno: funciona en la MatePad.
* Malo: no se pueden usar FCM, Play Integrity ni el inicio de sesión de Google.
* Malo: si se pierde la clave de firma, no se puede actualizar la app sin desinstalarla.
* Malo: la tablet no debe actualizarse a HarmonyOS 5, que ya no ejecuta apps de Android.

### Confirmación

Prueba manual en la MatePad en cada fase con pantallas.

## Más información

* `PLAN-MIGRACION.md`, "Pendientes para empezar".
* [Issue #6](https://github.com/jagr-ecu/repaso-admisiones-android/issues/6): respaldo de la clave de firma.
