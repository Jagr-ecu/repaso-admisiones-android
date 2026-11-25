---
status: aceptado
date: 2026-11-06
decision-makers: Responsable del proyecto
---

# Lógica en `:dominio` con Kotlin puro y casos de referencia de Python

## Contexto y problema

Reglas, verificador, cuaderno, selección, repasos y guardia se portan desde Python. Si Kotlin corrige distinto,
una respuesta correcta se marca como mala.

## Factores de la decisión

* Tests rápidos, sin emulador.
* Mismo resultado que Python.
* Aislar la lógica crítica de la interfaz.

## Opciones consideradas

* Módulo `:dominio` sin Android, comprobado con casos de referencia (`exportar-referencia`)
* Lógica dentro de los ViewModels, con tests de interfaz

## Decisión

Opción elegida: "`:dominio` en Kotlin puro". Todos los demás módulos dependen de él, y él de nadie.

### Consecuencias

* Bueno: los tests corren en la JVM con `./gradlew :dominio:test`.
* Bueno: 19 622 casos del verificador y 7 700 de la guardia coinciden con Python.
* Malo: el motor de expresiones de Android (ICU) no es el de la JVM. El verificador y la guardia también se prueban en el dispositivo, y no usan `\d`, `\s` ni `\w`.

### Confirmación

`./gradlew :dominio:test` y `./gradlew :datos:connectedDebugAndroidTest`.

## Más información

`PLAN-MIGRACION.md`, sección 2 y fase 3.
