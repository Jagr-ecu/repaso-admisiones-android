---
status: aceptado
date: 2026-11-09
decision-makers: Responsable del proyecto
---

# `ayudaPermitida(tipo)` en `:dominio` como única puerta de la ayuda

## Contexto y problema

En pruebas de sección y examen final no puede haber tutor ni pistas (regla 3). En la web, el bloqueo vivía en el
servidor; en Android no hay servidor.

## Factores de la decisión

* Una sola regla, fácil de probar.
* Ninguna llamada al modelo en pruebas o exámenes.

## Opciones consideradas

* Una función pura `ayudaPermitida(tipo)` en `:dominio`, consultada por `:tutor` antes de cualquier llamada
* Comprobaciones repartidas en cada pantalla
* Bloqueo en un servidor

## Decisión

Opción elegida: "`ayudaPermitida(tipo)` como única puerta". Si no se permite, `Tutor` lanza `AyudaProhibida` sin
tocar la red. Calificar un Writing no cuenta como ayuda.

### Consecuencias

* Bueno: un test recorre todos los tipos de sesión.
* Malo (aceptado): el bloqueo y el reloj del examen viven en el dispositivo y un estudiante con conocimientos
  técnicos podría saltárselos. Se acepta porque las notas no son oficiales. Si llegaran a serlo, hay que revisar esta decisión.

### Confirmación

`./gradlew :tutor:test` (test por tipo de sesión) y tests de `Reglas` en `:dominio`.

## Más información

`PLAN-MIGRACION.md`, sección 7. `CLAUDE.md`, regla 3.
