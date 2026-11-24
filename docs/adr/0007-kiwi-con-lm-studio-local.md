---
status: aceptado
date: 2026-11-06
decision-makers: Responsable del proyecto
---

# Kiwi con LM Studio en el PC de la casa, sin RAG

## Contexto y problema

Kiwi, el tutor, necesita un modelo de lenguaje. Los usuarios son menores y no hay presupuesto.

## Factores de la decisión

* Costo cero.
* Los mensajes de los estudiantes no salen de la casa.
* La app sigue funcionando sin Kiwi.

## Opciones consideradas

* LM Studio en el PC, con su API compatible con OpenAI, por la red local
* API de un proveedor en la nube
* Modelo dentro del dispositivo

## Decisión

Opción elegida: "LM Studio en el PC". No se porta RAG: se envía el material del concepto completo, con un límite
de 3 000 caracteres (el más largo medido tiene 2 589).

### Consecuencias

* Bueno: gratis y privado.
* Bueno: `:tutor` es Kotlin puro y se prueba en la JVM.
* Malo: fuera del wifi de la casa, Kiwi no está disponible y los Writing quedan pendientes de nota.
* Malo: LM Studio habla HTTP sin cifrar; hay que permitirlo en `network_security_config.xml`.
* Malo: varios estudiantes a la vez comparten un modelo y las respuestas pueden tardar.

### Confirmación

Tests de `:tutor`. Prueba con LM Studio real pendiente en el
[issue #1](https://github.com/jagr-ecu/repaso-admisiones-android/issues/1).

## Más información

`PLAN-MIGRACION.md`, sección 6.
