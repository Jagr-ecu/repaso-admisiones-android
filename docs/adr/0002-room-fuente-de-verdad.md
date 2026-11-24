---
status: aceptado
date: 2026-11-06
decision-makers: Responsable del proyecto
---

# Room como fuente de verdad y Firestore como copia en la nube

## Contexto y problema

Los estudiantes estudian a veces sin Internet y pueden usar más de un dispositivo. Hay que decidir dónde vive el progreso.

## Factores de la decisión

* La práctica y las pruebas no pueden depender de la red (regla 8).
* Nada se pierde en silencio.
* Recuperar el progreso en otro dispositivo.

## Opciones consideradas

* Room como fuente de verdad, con cola de subida a Firestore
* Firestore como fuente de verdad, con su caché sin conexión
* Solo Room, sin nube

## Decisión

Opción elegida: "Room como fuente de verdad", porque todo se escribe primero en el dispositivo y la nube es una copia.

### Consecuencias

* Bueno: la app funciona igual sin red.
* Bueno: la cola de subida se llena con triggers de SQLite, en la misma transacción que el cambio.
* Bueno: `memoria` no se sube; se reconstruye desde los intentos, así un dispositivo atrasado no pisa lo nuevo.
* Malo: hay que mantener reglas de unión por campo (`Union.kt`) y migraciones de Room.
* Malo: lo no subido en un dispositivo perdido se pierde.

### Confirmación

Tests en el dispositivo (`SincroTest`, migraciones) y la prueba en modo avión de la fase 1 y la fase 5.

## Pros y contras de las opciones

### Firestore como fuente de verdad

* Bueno: menos código.
* Malo: la caché sin conexión no garantiza la lógica de unión ni consultas locales filtradas por usuario. Fue el atajo de la fase 1 y se reemplazó.

### Solo Room

* Bueno: lo más simple.
* Malo: sin panel de admin ni recuperación en otro dispositivo.

## Más información

`PLAN-MIGRACION.md`, sección 4.
