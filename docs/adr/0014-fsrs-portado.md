---
status: aceptado
date: 2026-11-09
decision-makers: Responsable del proyecto
---

# Repaso espaciado FSRS portado de py-fsrs

## Contexto y problema

La web usa py-fsrs para programar los repasos. El traspaso del progreso exige que Android dé las mismas fechas.

## Factores de la decisión

* Mismas fechas que la web.
* Sin dependencias poco mantenidas.

## Opciones consideradas

* Portar FSRS-6 de py-fsrs a `:dominio` (`Memoria.kt`)
* FsrsKt (comunitaria) o una librería de Java

## Decisión

Opción elegida: "portar FSRS-6", porque no hay una librería oficial de Kotlin en Maven Central.

### Consecuencias

* Bueno: coincide con la web en 1 730 repasos de referencia.
* Malo: si py-fsrs cambia, hay que portar el cambio a mano.

### Confirmación

Casos de referencia de repasos en `./gradlew :dominio:test`.

## Más información

`PLAN-MIGRACION.md`, fase 3.
