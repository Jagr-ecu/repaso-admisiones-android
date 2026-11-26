---
status: aceptado
date: 2026-11-25
decision-makers: Responsable del proyecto
---

# Tests de arquitectura con Konsist en un módulo `:arquitectura`

## Contexto y problema

Las reglas de módulos y de datos solo estaban escritas en `CLAUDE.md` y en `docs/arquitectura.md`:

* `:dominio` y `:tutor` son Kotlin puro ([ADR-0006](0006-dominio-kotlin-puro.md));
* nadie depende de `:app`;
* toda consulta local filtra por `usuario_id`, porque varios estudiantes comparten el dispositivo;
* las conversaciones con Kiwi no se guardan nunca ([ADR-0009](0009-conversaciones-sin-guardar.md)).

La revisión del PR era lo único que las protegía. Un import de `android.content` en `:dominio` compila sin
problema mientras el módulo tenga Android en el classpath, y una `@Query` sin `usuario_id` funciona hasta que
dos estudiantes usan la misma tablet.

## Factores de la decisión

* Que `./gradlew check` falle y diga qué archivo rompe qué regla.
* Comprobar reglas que cruzan módulos, como la de `:datos` frente a `:tutor` o la de `:app` frente a Room.
* Que `:dominio` siga sin dependencias de test ajenas a su lógica.
* Leer el código fuente, no el bytecode: las `@Query` y los nombres de las columnas están en el código.

## Opciones consideradas

* Herramienta: Konsist, ArchUnit o un script propio con expresiones regulares.
* Lugar: un módulo nuevo `:arquitectura` solo con tests, o los tests dentro de `:dominio`.

## Decisión

* **Konsist 0.18.1** en un módulo nuevo **`:arquitectura`**. Es Kotlin/JVM, tiene solo tests, no depende de
  ningún módulo y nadie depende de él. Sus tests forman parte de `./gradlew check`.
* `ArquitecturaTest` tiene 8 reglas:
  1. `:dominio` y `:tutor` no importan `android.*` ni `androidx.*`.
  2. `:dominio` y `:tutor` no importan Firebase.
  3. `:dominio` no usa código de otros módulos del proyecto.
  4. Nadie depende de `:app`, ni con imports ni con `project(":app")` en Gradle.
  5. Toda `@Query` de un `@Dao` contiene `usuario_id = :…`.
  6. `:datos` y `:sincro` no usan `:tutor`. Así ningún tipo de la conversación puede llegar a Room ni a Firestore.
  7. Ninguna `@Entity` tiene propiedades con nombre o tipo de conversación (`mensaje`, `historial`, `chat`,
     `turno`, `dicho`…).
  8. Los archivos de `:app` que manejan el chat de Kiwi (`MensajeKiwi`, `Turno`, `Dicho`) no importan Room,
     Firestore ni `com.preparate.datos.local`.
* Las reglas 5, 7 y 8 fallan también si no encuentran nada que revisar: un `@Dao`, una `@Entity` o el chat de
  Kiwi. Así no pasan en verde si algo cambia de nombre.

### Consecuencias

* Bueno: las reglas inviolables de módulos y de datos dejan de depender de la memoria del revisor.
* Bueno: el mensaje de error dice la regla, el archivo y la línea de import o la consulta.
* Malo: las reglas 6, 7 y 8 se apoyan en nombres y tipos. Un mensaje de Kiwi guardado como `String` en una
  columna con otro nombre no lo detectan. La revisión del PR y el ADR-0009 siguen siendo necesarios.
* Malo: la regla 5 no comprueba que el parámetro sea el UID de la sesión, solo que la consulta filtre por
  `usuario_id`. Eso lo cubren los tests de `:datos` en el dispositivo.
* Neutro: la nota del Writing con el comentario de Kiwi sí se guarda (`revision` en `intentos`). Es una nota,
  no una conversación, y la regla 7 la permite a propósito.

### Confirmación

* `./gradlew :arquitectura:test` pasa en `main`.
* Al añadir `import android.content.Context` en `:dominio`, falla con
  `dominio/src/main/kotlin/com/preparate/dominio/Estudio.kt: import android.content.Context`.
* Al quitar `usuario_id` de una `@Query`, o al añadir `historialKiwi` a `SesionFila`, falla y nombra el DAO o la
  tabla.

## Pros y contras de las opciones

### Konsist

* Bueno: está hecho para Kotlin, lee el código fuente y entiende las anotaciones y su texto.
* Malo: es una dependencia más (solo de test) y su API todavía no es 1.0.

### ArchUnit

* Bueno: es muy maduro y es el estándar en Java.
* Malo: trabaja sobre bytecode, así que no ve el texto de las `@Query` y necesita compilar los módulos Android.

### Script propio con expresiones regulares

* Bueno: no añade dependencias.
* Malo: es frágil, no entiende la estructura del código y hay que mantenerlo a mano.

### Tests dentro de `:dominio`

* Bueno: no añade módulo.
* Malo: mezcla responsabilidades, y desde `:dominio` sería raro revisar `:datos` o `:app`. Además añade Konsist
  al módulo que más se quiere mantener limpio.

## Más información

* Issue #35, épica #32.
