---
status: aceptado
date: 2026-11-06
decision-makers: Responsable del proyecto
---

# Las conversaciones con Kiwi no se guardan

## Contexto y problema

En la web, `mensajes_tutor` guardaba las conversaciones. Los usuarios son menores y sus mensajes pueden tener datos personales.

## Factores de la decisión

* Guardar lo mínimo de datos de menores.
* No subir conversaciones a la nube.

## Opciones consideradas

* Conversaciones solo en la memoria de la pantalla
* Guardarlas en Room sin subirlas
* Guardarlas y subirlas para que el admin las revise

## Decisión

Opción elegida: "solo en memoria". Se guarda el contador de pistas y, en Writing, el texto, la nota y el comentario,
porque son trabajo calificado.

### Consecuencias

* Bueno: no hay conversaciones en Room ni en Firestore.
* Malo: al salir del ejercicio, la conversación se pierde y Kiwi podría repetir una pista.
* Malo: el admin no puede revisar lo que dijo Kiwi. La guardia es la protección, así que no se reduce ninguna de sus pruebas.
* Pendiente: revisar si LM Studio guarda registros en el PC
  ([issue #2](https://github.com/jagr-ecu/repaso-admisiones-android/issues/2)).

### Confirmación

Revisión del esquema de Room (`datos/schemas/`) y de `sincro/Documentos.kt`: ninguna tabla ni documento de mensajes.

## Más información

`PLAN-MIGRACION.md`, sección 6, "Conversaciones sin guardar".
