---
status: aceptado
date: 2026-11-25
decision-makers: Responsable del proyecto
---

# Formato con Spotless y ktlint, y análisis estático con detekt

## Contexto y problema

No había formato automático ni análisis estático. Con ktlint 1.7.1 y el estilo de `.editorconfig` salían 1 570
infracciones en 50 archivos, y cada PR mezclaba cambios de verdad con cambios de estilo. La fase 3 (épica #32)
pide que `./gradlew check` revise el formato y los olores de código antes de cada PR y, después, en el CI.

## Factores de la decisión

* Una sola orden, `./gradlew check`, en local y en el CI.
* El estilo vive en `.editorconfig`, que también lee Android Studio.
* No bloquear el trabajo por la deuda que ya existía.
* Que las funciones `@Composable` en PascalCase no cuenten como error.

## Opciones consideradas

* Spotless con ktlint, más detekt con baseline
* El plugin `org.jlleitschuh.gradle.ktlint`, más detekt
* Solo detekt con su conjunto de reglas `formatting` (que envuelve ktlint)
* Formatear solo los archivos nuevos o tocados (`ratchetFrom`), en vez de todo el código

## Decisión

Opción elegida: "Spotless con ktlint, más detekt con baseline", aplicando el formato a todo el código de una vez.

* **Spotless 8 con ktlint 1.8.0** para `*.kt` y `*.kts`, configurado en el `build.gradle.kts` raíz.
  `.editorconfig` fija `ktlint_code_style = android_studio`, 120 columnas y
  `ktlint_function_naming_ignore_when_annotated_with = Composable`.
* **detekt 1.23.8** en todos los módulos, con `buildUponDefaultConfig` y los ajustes de
  `config/detekt/detekt.yml`. Los avisos que ya existían (118) quedan en el `detekt-baseline.xml` de cada módulo.
  Como detekt 1.23 está compilado con Kotlin 2.0, su configuración usa esa versión de Kotlin y no la del
  proyecto (2.2).
* Las dos herramientas forman parte de `./gradlew check`.
* De paso, `gradlew` tiene permiso de ejecución en git (`100755`), que el CI en Linux necesita.

### Consecuencias

* Bueno: los PR ya no traen ruido de estilo. `./gradlew spotlessApply` lo corrige casi todo.
* Bueno: un solo estilo en todo el código, sin mezclas.
* Malo: el PR del formato es grande (unos 45 archivos). Es solo formato, y después del squash su commit va a
  `.git-blame-ignore-revs` para que `git blame` siga siendo útil.
* Malo: el baseline esconde deuda (funciones largas, `catch` genéricos, clases con muchas funciones). Hay un
  issue para ir reduciéndolo.
* Malo: Spotless no escribe un archivo mientras le quede un error que ktlint no corrige solo (por ejemplo una
  cadena de más de 120 columnas). Hay que partir esa línea a mano y volver a ejecutar `spotlessApply`.
* Neutro: cuando salga detekt 2 estable con soporte para Kotlin 2.2, se sube y se quita el ajuste de versión.

### Confirmación

* `./gradlew spotlessCheck` falla con un archivo mal formateado, y `./gradlew spotlessApply` lo corrige.
* `./gradlew detekt` pasa en `main` gracias al baseline y falla con un aviso nuevo.
* Las dos van dentro de `./gradlew check`, que será un check obligatorio del PR en la fase 5.

## Pros y contras de las opciones

### Spotless con ktlint, más detekt con baseline

* Bueno: Spotless es el estándar de facto, formatea también los `.kts` y más adelante puede formatear XML o
  Markdown.
* Bueno: detekt encuentra lo que ktlint no busca (complejidad, excepciones tragadas, números mágicos).
* Malo: son dos herramientas y dos configuraciones.

### Plugin `org.jlleitschuh.gradle.ktlint`

* Bueno: se integra directamente con ktlint.
* Malo: tiene menos mantenimiento que Spotless y solo sirve para Kotlin.

### Solo detekt con `formatting`

* Bueno: una sola herramienta.
* Malo: el conjunto `formatting` va por detrás de ktlint y detekt 2 lo cambia. Además mezcla formato y olores
  en el mismo informe.

### Formatear solo lo nuevo o lo tocado (`ratchetFrom`)

* Bueno: no hay un PR grande de formato.
* Malo: los PR siguientes se llenan de cambios de formato en archivos que se tocan por otra razón. En el CI
  depende de tener el historial de git completo.

## Más información

* Issue #33, épica #32.
* Regenerar el baseline (solo si se acepta deuda nueva a propósito): `./gradlew detektBaseline`.
