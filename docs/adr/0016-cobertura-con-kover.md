---
status: aceptado
date: 2026-11-25
decision-makers: Responsable del proyecto
---

# Cobertura con Kover: mínimo de 70 % de líneas en `:dominio` y `:tutor`

## Contexto y problema

Las reglas que no se pueden romper (verificador, guardia de Kiwi, `ayudaPermitida`, reglas del curso, nota del
Writing) viven en `:dominio` y `:tutor`, en Kotlin puro y con tests en la JVM. Nada impedía que un PR añadiera
lógica sin tests. Al medir con Kover, `:dominio` tenía 85 % de líneas cubiertas y `:tutor` 60 %, porque
`ClienteKiwi`, el cliente HTTP de LM Studio, estaba al 0 %.

## Factores de la decisión

* Que `./gradlew check` falle si la cobertura baja, en local y en el CI.
* Probar justo lo que toca la red: la regla 8 dice que sin LM Studio la app sigue funcionando.
* No añadir dependencias de test que no hacen falta.
* Medir donde los tests son rápidos y fiables, es decir, en la JVM.

## Opciones consideradas

* Para medir: Kover o JaCoCo.
* Para `:tutor`: probar `ClienteKiwi` con el servidor HTTP del JDK (`com.sun.net.httpserver`), excluir
  `ClienteKiwi` de la medición o bajar el mínimo de `:tutor` a 60 %.
* Para el alcance: solo `:dominio` y `:tutor`, o también los módulos Android.

## Decisión

* **Kover 0.9.9** en `:dominio` y `:tutor`. Lo aplica cada módulo en sus `plugins`, y la regla vive en el
  `build.gradle.kts` raíz: todo módulo que use Kover exige un **mínimo de 70 % de líneas cubiertas**.
* El mínimo se cambia con una propiedad, sin tocar el código: `./gradlew koverVerify -Pcobertura.minima=80`.
* `koverVerify` forma parte de `./gradlew check`.
* `ClienteKiwi` se prueba contra un servidor HTTP local de verdad (`ClienteKiwiTest`, 14 tests). Cubre la
  petición que se envía, el esquema JSON, quitar el `<think>`, los modelos de embeddings, los errores HTTP y de
  JSON, y LM Studio apagado, con la espera de 5 minutos antes de volver a intentarlo.
* Los módulos Android (`:datos`, `:sincro`, `:app`) quedan fuera por ahora. Sus tests importantes corren en el
  dispositivo, y Kover no mide los tests instrumentados.

Con esto, `:dominio` está al 85,7 % y `:tutor` al 97,3 %.

### Consecuencias

* Bueno: un PR que añada lógica sin tests en `:dominio` o `:tutor` no pasa `check`.
* Bueno: el comportamiento sin LM Studio (regla 8) queda probado de verdad, sin dependencias nuevas.
* Malo: la cobertura de líneas no dice si los tests comprueban lo correcto. Los criterios de aceptación y los
  casos de referencia siguen siendo la prueba principal.
* Malo: los tests de `ClienteKiwi` abren un puerto local. Escuchan solo en `127.0.0.1` y en un puerto que elige
  el sistema, así que no chocan con nada.
* Neutro: el mínimo es por módulo, no global. Así un módulo muy cubierto no tapa a otro.

### Confirmación

* `./gradlew koverVerify` pasa en `main`.
* `./gradlew :dominio:koverVerify -Pcobertura.minima=90` falla con
  `lines covered percentage is 85.684000, but expected minimum is 90`.
* Los informes están en `<módulo>/build/reports/kover/` (`koverHtmlReport`, `koverXmlReport`). El XML servirá
  al CI de la fase 5.

## Pros y contras de las opciones

### Kover

* Bueno: es de JetBrains, entiende Kotlin (funciones `inline`, clases de datos) y se configura con poco.
* Malo: no mide los tests instrumentados de Android.

### JaCoCo

* Bueno: es el estándar en Java y AGP lo trae para los tests instrumentados.
* Malo: cuenta como no cubierto código que genera el compilador de Kotlin, y se configura con más trabajo.

### Probar `ClienteKiwi` con `com.sun.net.httpserver`

* Bueno: prueba el código de red real (`HttpURLConnection`, códigos HTTP, tiempos de espera) sin dependencias.
* Malo: es más trabajo que excluirlo.

### Excluir `ClienteKiwi` o bajar el mínimo de `:tutor` a 60 %

* Bueno: no cuesta nada.
* Malo: deja sin probar justo la parte que sostiene la regla 8.

## Más información

* Issue #34, épica #32.
