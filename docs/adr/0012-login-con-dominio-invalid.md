---
status: aceptado
date: 2026-11-06
decision-makers: Responsable del proyecto
---

# Entrar con usuario y clave, con el dominio `@preparate.invalid`

## Contexto y problema

Firebase Auth exige un correo. Los estudiantes son menores y no deben dar un correo real. Varios pueden compartir un dispositivo.

## Factores de la decisión

* Sin correos reales de menores.
* Entrar desde otro dispositivo.
* Sin registro público.

## Opciones consideradas

* Usuario y clave, convertidos por dentro a `usuario@preparate.invalid`
* Inicio anónimo de Firebase
* Correo real de cada familia

## Decisión

Opción elegida: "usuario y clave con `@preparate.invalid`". El dominio `.invalid` está reservado y nunca recibe correos.
Solo hay dos roles, `estudiante` y `admin`, guardados en `usuarios/{uid}`, que solo escribe un admin.

### Consecuencias

* Bueno: el estudiante solo recuerda un usuario y una clave.
* Bueno: escribir progreso exige el rol `estudiante`, así que una cuenta creada desde fuera no accede a nada.
* Malo: no hay recuperación automática de la clave; el admin la cambia con `herramientas/cuentas.py cambiar-clave`.

### Confirmación

Tests de `firestore.rules` (cuenta externa, estudiante que lee a otro, estudiante que cambia su rol).

## Pros y contras de las opciones

### Inicio anónimo

* Malo: no permite entrar desde otro dispositivo, se pierde al desinstalar y el admin no sabe quién es quién. Descartado el 2026-11-09.

### Correo real

* Malo: datos personales innecesarios de menores.

## Más información

* `PLAN-MIGRACION.md`, sección 4, "Cuentas y roles".
* [Issue #13](https://github.com/jagr-ecu/repaso-admisiones-android/issues/13): entrar con Google (fuera del MVP).
