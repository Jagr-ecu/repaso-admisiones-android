---
status: aceptado
date: 2026-11-06
decision-makers: Responsable del proyecto
---

# Firebase Auth y Firestore en el plan gratuito, sin Cloud Functions

## Contexto y problema

Hacen falta cuentas y una copia en la nube del progreso, sin presupuesto y sin mantener un servidor.

## Factores de la decisión

* Costo cero.
* Sin servidor propio que operar.
* Funcionar en un dispositivo sin servicios de Google.

## Opciones consideradas

* Firebase en el plan Spark
* Backend propio (por ejemplo, Spring Boot con PostgreSQL)
* Supabase

## Decisión

Opción elegida: "Firebase en el plan Spark". Los estudiantes se crean desde la app con una segunda instancia de
`FirebaseApp`, para no cerrar la sesión del admin y no necesitar Cloud Functions. Las claves olvidadas se cambian
con `herramientas/cuentas.py` y el SDK de administración, desde el PC.

### Consecuencias

* Bueno: sin costo ni servidor.
* Bueno: las reglas de Firestore se prueban con su emulador.
* Malo: cuota diaria compartida (50 000 lecturas, 20 000 escrituras). Si se agota, la subida espera al día siguiente.
* Malo: la credencial del SDK de administración es muy sensible y solo vive en el PC.

### Confirmación

`npm test` en `pruebas-reglas/`. Uso de cuota en la consola de Firebase.

## Pros y contras de las opciones

### Backend propio

* Bueno: control total.
* Malo: hosting, seguridad y operación para un único desarrollador.

### Supabase

* Bueno: PostgreSQL y reglas por fila.
* Malo: no se comprobó en HarmonyOS; el plan gratuito pausa proyectos inactivos.

## Más información

`PLAN-MIGRACION.md`, sección 4, "Cuentas y roles" y "Límites gratuitos".
