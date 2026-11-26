---
status: aceptado
date: 2026-11-26
decision-makers: Responsable del proyecto
---

# Claves de 8 caracteres y sesión de admin que se cierra al salir de la app

## Contexto y problema

El modelo de amenazas (`docs/security/threat-model.md`) deja tres riesgos en las cuentas (issue #60):

* **F1-S:** adivinar la clave de un estudiante. Las claves podían tener 6 caracteres, el mínimo de Firebase.
* **F4-E y F4-S:** los dispositivos se comparten. Si un adulto deja abierta la sesión de admin, un estudiante
  puede abrir el panel y ver el progreso y los Writing de sus hermanos, o crear cuentas. Además, la sesión de
  admin se recuperaba sola al abrir la app.

## Factores de la decisión

* Los estudiantes tienen 11–12 años: la clave tiene que poder recordarse.
* Las cuentas que ya existen no pueden quedarse fuera.
* La sesión del estudiante no debe cerrarse cada vez que cambia de app: perdería el ritmo del tema.
* Sin dependencias nuevas.

## Opciones consideradas

* **Longitud mínima:** 6 (la de Firebase), 8, o 10 o más con mayúsculas y números.
* **Aplicarla:** solo a claves nuevas o cambiadas, o también al entrar.
* **Sesión de admin:** cerrarla al pasar a segundo plano, cerrarla tras unos minutos sin uso, o pedir un PIN
  para abrir el panel.

## Decisión

* **8 caracteres como mínimo** para claves **nuevas o cambiadas**: `Usuario.CLAVE_MINIMA` en `:dominio` (lo usan
  la app al crear estudiantes y al cambiar la clave) y `CLAVE_MINIMA` en `herramientas/cuentas.py`. **Entrar**
  no comprueba la longitud: las claves antiguas de 6 o 7 caracteres siguen funcionando hasta que se cambien. Los
  textos de la app toman el número de la constante (`%1$d`), así que no se desfasan.
* **La sesión de admin se cierra cuando la app pasa a segundo plano** (otra app, el botón de inicio, la pantalla
  apagada), en `MainActivity.onStop`, salvo que sea un giro de pantalla. Tampoco se recupera al abrir la app:
  si Android mató el proceso con el admin dentro, hay que entrar otra vez.
* **La sesión del estudiante no cambia:** sigue abierta, como hasta ahora.
* **En la consola de Firebase**, Authentication → Settings → *User actions*: activar la
  **protección contra la enumeración de correos** (*Email enumeration protection*). Así un atacante no puede
  saber si un usuario existe por el mensaje de error. Es un paso manual: está en la lista de comprobación de
  `docs/security/threat-model.md` y en el runbook cuando exista (fase 7).

### Consecuencias

* Bueno: un estudiante que coge la tablet después de un adulto nunca encuentra el panel de admin abierto.
* Bueno: las claves nuevas son más difíciles de adivinar, sin dejar fuera a nadie.
* Malo: el admin tiene que volver a entrar cada vez que sale de la app, aunque sea un momento (por ejemplo, para
  mirar la IP del PC mientras configura a Kiwi). Se acepta: el admin entra poco y la seguridad de los datos
  de menores pesa más.
* Malo: las claves cortas de antes siguen valiendo hasta que se cambien. Conviene cambiarlas con
  `cuentas.py cambiar-clave` antes del uso real (fase 7).
* Neutro: el límite de intentos de Firebase Auth sigue siendo la protección principal contra la fuerza bruta.

### Confirmación

* `python herramientas/cuentas.py crear-admin …` con una clave de 6 caracteres: "La clave necesita al menos 8
  caracteres."
* En el emulador, con el admin de pruebas: girar la pantalla mantiene el panel; ir al inicio y volver, o matar el
  proceso y abrir la app, muestra la pantalla de entrar. Con el estudiante de pruebas, ir al inicio y volver
  mantiene su sesión.

## Pros y contras de las opciones

### 8 caracteres, solo para claves nuevas

* Bueno: mejora clara frente a 6, y todavía se puede recordar a los 11 años.
* Malo: no obliga a cambiar las claves que ya existen.

### 10 o más, con mayúsculas y números

* Bueno: aún más difícil de adivinar.
* Malo: los niños la apuntarían en un papel junto a la tablet, que es peor.

### Cerrar tras unos minutos sin uso

* Bueno: el admin podría salir un momento sin perder la sesión.
* Malo: deja una ventana en la que el panel queda abierto en un dispositivo compartido, y necesita un temporizador.

### PIN para abrir el panel

* Bueno: el admin no vuelve a escribir la clave completa.
* Malo: otro secreto que guardar en el dispositivo, y otra pantalla que construir y probar.

## Más información

* Issue #60, épica #36.
