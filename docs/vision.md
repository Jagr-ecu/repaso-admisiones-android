# Visión y alcance

Estado: vigente. Fuente: `PLAN-MIGRACION.md` y `CLAUDE.md`. Las decisiones están en [`adr/`](adr/README.md).

## Visión

**Prepárate** ayuda a estudiantes de 11 a 12 años a preparar el examen de admisión a 8.º de EGB.
Cada estudiante avanza a su ritmo por temas cortos, con un tutor (Kiwi) que le ayuda a **razonar**
sin darle la respuesta. La app funciona sin Internet en una tablet de la casa y guarda el progreso
de cada estudiante en la nube, gratis.

## Problema

- La versión web necesita el PC encendido y un navegador: no sirve para estudiar en la tablet fuera del escritorio.
- Varios estudiantes comparten dispositivos y cada uno necesita su propio progreso.
- La tablet real (Huawei MatePad 11.5, HarmonyOS 4.3) **no tiene servicios de Google**.
- No hay presupuesto: todo debe caber en planes gratuitos.

## Usuarios

| Rol | Qué hace |
|---|---|
| Estudiante | Estudia temas, hace pruebas de sección y el examen final, habla con Kiwi en la práctica, cambia su clave |
| Admin | Crea estudiantes, ve el progreso de todos (solo lectura), configura la dirección de Kiwi, cambia su clave |

No hay registro público ni otros roles.

## Casos de uso principales

1. **Entrar** con usuario y clave, en cualquier dispositivo.
2. **Elegir curso y materia** (hoy: Admisiones → Lengua y Literatura, Matemática, Inglés).
3. **Estudiar un tema**: explicación, ejercicios, pista escrita tras un fallo, Kiwi si está disponible.
4. **Hacer la prueba de sección** al terminar sus temas, sin tutor ni pistas.
5. **Hacer el examen final** de la materia, con reloj de 20 minutos.
6. **Repasar** lo que toca según el repaso espaciado.
7. **Escribir un Writing** y recibir nota y comentario de Kiwi.
8. **Estudiar sin Internet** y sincronizar al volver la conexión.
9. **Admin: crear un estudiante** y **ver su progreso** desde cualquier dispositivo.

## Alcance del MVP (versión 1)

Incluido, y hecho en código en las fases 0–6 de `PLAN-MIGRACION.md`:

- Un curso (Admisiones) con tres materias, todo el contenido dentro del APK.
- Camino de secciones, temas, pruebas, examen final y repasos con las reglas del curso.
- Progreso en Room, cola de subida y sincronización con Firestore.
- Cuentas `estudiante` y `admin`, panel de admin de solo lectura.
- Kiwi con LM Studio en la red local, guardia y calificación de Writing.
- Diseño para tablet y celular.
- Traspaso del progreso desde la web y APK firmado.

El MVP se da por cerrado con la fase 7: los estudiantes usan la app **una semana real sin perder progreso**
([issue #11](https://github.com/jagr-ecu/repaso-admisiones-android/issues/11)).

Fuera del MVP (backlog con la etiqueta `enhancement`):

- Entrar con Google, rol de profesor o padre, acceso a cursos por estudiante.
- Respaldo y exportación del progreso, estadísticas por plantilla, reportar preguntas.
- Contenido en una base de datos en lugar del APK.
- Editar roles o borrar estudiantes desde la app (se hace en la consola de Firebase).
- RAG para Kiwi, publicación en Google Play.

## Requisitos funcionales

| Id | Requisito |
|---|---|
| RF-01 | La nota es sobre 10 y se aprueba con 7. |
| RF-02 | Todas las secciones están abiertas. Dentro de una sección, los temas van en orden. |
| RF-03 | La prueba de sección se abre al terminar todos sus temas. |
| RF-04 | En un tema se permiten 3 errores. El 4.º lo corta sin puntos. Lo fallado se repite al final con la misma variante (misma semilla). |
| RF-05 | Los puntos se ganan por completar temas, nunca por velocidad. Sin rachas ni audio. |
| RF-06 | Tras un fallo, la pista escrita sale primero. Kiwi solo aparece tras un fallo o en Writing. |
| RF-07 | En pruebas de sección y examen final no hay tutor ni pistas. |
| RF-08 | Kiwi nunca revela la respuesta. La respuesta correcta la calcula siempre el código. |
| RF-09 | Las materias y los cursos nunca se mezclan, ni en los repasos. |
| RF-10 | El examen final dura como máximo 20 minutos. |
| RF-11 | Cada estudiante ve solo su progreso. El admin ve el de todos, sin modificarlo. |
| RF-12 | Ninguna pregunta va escrita en el código: todo sale del paquete de contenido. |

## Requisitos no funcionales

### Seguridad y privacidad (datos de menores)

| Id | Requisito | Cómo se comprueba |
|---|---|---|
| RNF-S1 | Solo se guarda el nombre del estudiante, sin apellidos. | Revisión del modelo de datos |
| RNF-S2 | Las conversaciones con Kiwi no se guardan nunca, ni en Room ni en Firestore. | Revisión de código y del esquema de Room |
| RNF-S3 | Un estudiante no puede leer ni escribir datos de otro. Una cuenta sin rol no accede a nada. | Tests de `firestore.rules` con el emulador (`pruebas-reglas/`) |
| RNF-S4 | Toda consulta local filtra por `usuario_id`. | Tests de `:datos` |
| RNF-S5 | Ningún secreto en git: `google-services.json`, clave de firma, `keystore.properties`, credencial del SDK de administración. | `.gitignore` y escaneo de secretos (fase 4) |
| RNF-S6 | El tráfico con Firebase va por HTTPS. HTTP solo se usa hacia LM Studio en la red local. | `network_security_config.xml`. **Hoy** permite HTTP en toda la app (`base-config`), porque la IP del PC la elige el admin; se revisa en la fase 4 |

### Disponibilidad y funcionamiento sin conexión

| Id | Requisito |
|---|---|
| RNF-D1 | Sin Internet ni LM Studio, la práctica, las pruebas y el examen funcionan. |
| RNF-D2 | Nada se borra en silencio: al salir con cambios sin subir, la app avisa. |
| RNF-D3 | Subir dos veces no duplica nada (ids estables). |
| RNF-D4 | Si se agota la cuota diaria de Firestore, la app sigue con Room y sube después. |

### Rendimiento

| Id | Requisito |
|---|---|
| RNF-R1 | Un tema dura menos de 5 minutos. |
| RNF-R2 | Las fórmulas se dibujan sin Internet y sin que el tema se sienta pesado en la MatePad. |
| RNF-R3 | Kiwi: comprobación de disponibilidad en 2 s como máximo y respuesta en 120 s como máximo, sin bloquear "Siguiente". |

### Compatibilidad

| Id | Requisito |
|---|---|
| RNF-C1 | Android 10 o superior (`minSdk` 29), `targetSdk` 36. |
| RNF-C2 | Funciona sin servicios de Google (HarmonyOS 4.3). |
| RNF-C3 | Tablet en vertical y horizontal; celular en vertical. |
| RNF-C4 | Interfaz en español, con frases cortas para 11–12 años. Textos en `strings.xml`. |

### Mantenibilidad

| Id | Requisito |
|---|---|
| RNF-M1 | La lógica pura vive en `:dominio`, sin Android, con tests en la JVM. |
| RNF-M2 | Todo lo portado de Python se compara con los casos de referencia del proyecto web. |
| RNF-M3 | Cada cambio de esquema de Room lleva su migración probada. |

### Observabilidad

No se envía telemetría ni registros de errores a servicios externos, porque los usuarios son menores.
La observabilidad se limita a:

- el contador de respuestas sin subir y el estado de la nube en la pantalla de inicio;
- el panel de admin (última actividad, minutos, Writing pendientes);
- el uso de cuota en la consola de Firebase.

### Costo

| Id | Requisito |
|---|---|
| RNF-K1 | Plan Spark de Firebase: 50 000 lecturas, 20 000 escrituras y 20 000 borrados al día para todo el proyecto. |
| RNF-K2 | Sin Cloud Functions ni servicios de pago. El APK se instala directamente, sin Google Play. |

## Supuestos

- Los estudiantes usan el wifi de la casa donde está el PC con LM Studio. Fuera de esa red, Kiwi no está disponible.
- El PC tiene una IP fija en el router.
- Pocos estudiantes (decenas como mucho) caben en el plan gratuito.
- Las notas de la app no son oficiales: preparan para el examen, no lo sustituyen.
- La tablet no se actualiza a HarmonyOS 5, que ya no ejecuta apps de Android.
- La web sigue siendo el lugar donde se escribe y valida el contenido.

## Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Kotlin y Python corrigen distinto | Una respuesta correcta se marca mal | Casos de referencia generados por Python |
| La guardia deja pasar la respuesta | Se rompe la regla 2 | 7 700 casos de referencia, también con el motor de Android |
| Un estudiante ve datos de otro | Exposición de datos de menores | Reglas probadas en el emulador, consultas filtradas por usuario |
| Se pierde la clave de firma | No se puede actualizar la app | Copia de seguridad ([issue #6](https://github.com/jagr-ecu/repaso-admisiones-android/issues/6)); el progreso se recupera de Firestore |
| Se filtra la credencial del SDK de administración | Control de todas las cuentas | Solo en el PC, fuera de git; revocarla si se expone |
| Se agota la cuota de Firebase | La subida se pausa hasta el día siguiente | Room sigue funcionando; vigilar la consola |
| Cambia la IP del PC | Kiwi parece apagado | IP fija y ajuste visible para el admin |
| El bloqueo de Kiwi y el reloj viven en el dispositivo | Un estudiante podría saltárselos | Aceptado: las notas no son oficiales ([ADR-0008](adr/0008-ayuda-permitida-unica-puerta.md)) |
| La tablet se actualiza a HarmonyOS 5 | La app deja de funcionar | Desactivar las actualizaciones automáticas |
| Web y app divergen durante la migración | Dos historiales distintos | No estudiar en la app hasta el traspaso de la fase 7 |
