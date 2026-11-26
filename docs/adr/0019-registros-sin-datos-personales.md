---
status: aceptado
date: 2026-11-25
decision-makers: Responsable del proyecto
---

# Registros sin datos personales, comprobado con Konsist

## Contexto y problema

La app guarda datos de menores: nombres, textos de Writing y, solo en memoria, la conversación con Kiwi
([ADR-0009](0009-conversaciones-sin-guardar.md)). Un `Log` con cualquiera de esos datos acaba en logcat, donde lo
pueden leer `adb`, un informe de errores o un programa de depuración. Así se saltaría la regla de no guardar las
conversaciones.

Hoy solo hay un registro: el aviso de la consola del WebView de fórmulas (`Formula.kt`), que imprimía el
contenido del aviso. Como ese WebView también pinta los mensajes de Kiwi, el aviso podía llevar su texto.

## Factores de la decisión

* Que `./gradlew check` falle si alguien añade un registro con datos personales (criterio del issue #42).
* Sin resolución de tipos en detekt, porque es lenta y no funciona bien con AGP 9.
* Reutilizar lo que ya hay: el módulo `:arquitectura` con Konsist ([ADR-0017](0017-tests-de-arquitectura-con-konsist.md)).

## Opciones consideradas

* Un test de Konsist en `:arquitectura`: lista de archivos que pueden usar `Log` y nombres de variables prohibidos.
* `ForbiddenMethodCall` de detekt, que necesita resolución de tipos (`detektMain`).
* Una regla propia de detekt, en un módulo de reglas.
* Quitar los `Log` con R8 en release (`-assumenosideeffects`).

## Decisión

Opción elegida: **"test de Konsist en `:arquitectura`"** (`RegistrosTest`):

* En el código de producción no hay `println`, `print`, `printStackTrace` ni `System.out`/`System.err`.
* `Log.*` solo en los archivos de una lista explícita (hoy, solo `Formula.kt`). Incluso ahí, los argumentos que
  no son la etiqueta no pueden usar variables con nombres de datos personales: `nombre`, `usuario`, `correo`,
  `clave`, `respuesta`, `texto`, `mensaje`, `writing`, `kiwi`, `uid`, `token`… El texto fijo entre comillas no
  cuenta.
* El aviso de `Formula.kt` sale **solo en depuración** (`BuildConfig.DEBUG`) y **sin su contenido**: solo el
  archivo y la línea de JavaScript.
* Para añadir un archivo a la lista hay que tocar `RegistrosTest`, y eso se ve en la revisión del PR.

### Consecuencias

* Bueno: un `Log.d("…", intento.respuesta)` en cualquier módulo hace fallar `check` y dice el archivo y la variable.
* Bueno: sin dependencias nuevas, y la regla trae sus propios tests de ejemplo.
* Malo: se basa en nombres, no en el flujo de datos. Un `Log` fuera de la lista falla siempre, pero dentro de
  la lista una variable con un nombre inocente (`val x = intento.respuesta`) pasaría. Por eso la lista es corta
  y cada entrada se justifica en el PR.
* Neutro: quitar los `Log` con R8 obliga a activar `isMinifyEnabled`, que hoy está en `false`. Es un cambio
  grande que hay que probar en la MatePad, así que queda fuera de esta decisión.

### Confirmación

* `./gradlew :arquitectura:test`: `RegistrosTest` pasa en `main`.
* Al añadir `Log.d("Estudios", "Writing: ${respuestaWriting}")` en `:datos`, falla con
  `datos/src/main/kotlin/com/preparate/datos/Estudios.kt: Log.d(...) (este archivo no puede usar Log)`.

## Pros y contras de las opciones

### Test de Konsist

* Bueno: sigue el patrón del ADR-0017, es rápido y el mensaje de error es claro.
* Malo: analiza texto, no tipos.

### `ForbiddenMethodCall` de detekt

* Bueno: sabe exactamente qué método se llama.
* Malo: necesita resolución de tipos con el classpath de Android, y es lenta y frágil con AGP 9 y Kotlin 2.2.

### Regla propia de detekt

* Bueno: se integra con el informe de detekt.
* Malo: necesita un módulo de reglas y su API. Demasiado para un solo caso.

### R8 con `-assumenosideeffects`

* Bueno: los `Log` desaparecen del APK de release.
* Malo: no evita el problema en depuración, y obliga a activar R8.

## Más información

* Issue #42, épica #36. Modelo de amenazas: `docs/security/threat-model.md`, frontera 4.
