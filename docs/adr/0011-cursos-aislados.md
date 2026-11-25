---
status: aceptado
date: 2026-11-09
decision-makers: Responsable del proyecto
---

# Todo el contenido y el progreso van dentro de un curso

## Contexto y problema

Hoy solo existe el curso Admisiones, pero se quieren añadir otros. Los ids de temas y plantillas solo son únicos
dentro de su curso.

## Factores de la decisión

* Añadir cursos sin migrar datos.
* Que los cursos nunca se mezclen.

## Opciones consideradas

* `curso_id` en el paquete, en Room y en Firestore desde el principio
* Un solo curso implícito y migrar después

## Decisión

Opción elegida: "`curso_id` en todo". El paquete usa el formato 2 (`cursos.json` y una carpeta por curso), y la
pantalla de inicio muestra los cursos antes de las materias.

### Consecuencias

* Bueno: un curso nuevo es contenido nuevo, sin cambio de esquema.
* Malo: todas las consultas llevan un filtro más.

### Confirmación

Tablas de Room con `usuario_id` y `curso_id` (`datos/schemas/`). El lector siempre pide el curso.

## Más información

* `PLAN-MIGRACION.md`, "Cursos".
* [Issue #19](https://github.com/jagr-ecu/repaso-admisiones-android/issues/19): acceso a cursos por estudiante.
