# Modelo de amenazas (STRIDE)

Estado: vigente. Revisado el 2026-11-25. Se revisa cuando cambia una frontera de confianza (un servicio nuevo, un
rol nuevo, otra forma de instalar la app) y, como mínimo, antes de cada versión MAJOR.

## Alcance y activos

La app la usan varios **menores de 11–12 años**, en dispositivos de la familia. No hay servidor propio: el
dispositivo es la fuente de verdad y Firebase guarda una copia ([arquitectura](../arquitectura.md)).

| Activo | Dónde vive | Por qué importa |
|---|---|---|
| Progreso de cada estudiante (sesiones, intentos, puntos, notas) | Room y Firestore | Datos de menores. Las notas guían el estudio |
| Textos de Writing | Room y Firestore (`intentos.respuesta`) | Texto libre escrito por un menor: puede contener datos personales |
| Identidad (nombre sin apellidos, usuario) | Firestore `usuarios/` y Firebase Auth | Identifica a un menor |
| Claves de las cuentas | Firebase Auth (solo el hash) | Dan acceso al progreso |
| Conversaciones con Kiwi | Solo en memoria ([ADR-0009](../adr/0009-conversaciones-sin-guardar.md)) | Texto libre de un menor |
| Credencial del SDK de administración | Solo el PC (`herramientas/credencial-firebase.json`, fuera de git) | Acceso total a Auth y Firestore |
| Clave de firma del APK | Fuera de git, con copia | Quien la tenga puede publicar una "actualización" falsa |
| Integridad de la respuesta correcta | Paquete de contenido en el APK y `:dominio` | Regla inviolable 1 |

**Atacantes considerados**

| Atacante | Ejemplo | Capacidad |
|---|---|---|
| A1. Internet | Cualquiera con la configuración pública de Firebase del APK | Llamar a las API públicas de Auth y Firestore |
| A2. Alguien en la wifi de casa | Una visita o un dispositivo comprometido | Ver y alterar tráfico sin cifrar de la red local |
| A3. Otro estudiante de la familia | Un hermano en la misma tablet | Usar el dispositivo y lo que esté con la sesión abierta |
| A4. El propio estudiante | Quiere la respuesta o una nota mejor | Conocimientos técnicos básicos, acceso físico a su dispositivo |
| A5. Cadena de suministro | Una dependencia de Gradle o npm comprometida | Código que se ejecuta al compilar o dentro del APK |

Fuera de alcance: un atacante con acceso de administrador al PC de la casa o a la cuenta de Google del proyecto
de Firebase. Con eso controla todo y ninguna medida de la app lo frena.

**Escala**: Impacto y Probabilidad en Alto / Medio / Bajo. "Pendiente" enlaza el issue que lo resuelve. "Aceptado"
enlaza el ADR que lo justifica.

## Frontera 1: app ↔ Firestore y Firebase Auth

Por Internet y con HTTPS. La configuración de Firebase (`google-services.json`) va dentro del APK y es pública por
diseño. Lo que protege los datos son Auth y [`firestore.rules`](../../firestore.rules), probadas en
[`pruebas-reglas/`](../../pruebas-reglas/).

| STRIDE | Amenaza | Imp. | Prob. | Mitigación existente | Pendiente / aceptado |
|---|---|---|---|---|---|
| **S** | A1 crea una cuenta con la API pública de Auth y lee o escribe datos | Alto | Media | Sin documento en `usuarios/` no se puede leer ni escribir nada (`firestore.rules`, probado). Sin registro público en la app ([ADR-0012](../adr/0012-login-con-dominio-invalid.md)) | — |
| **S** | A1 adivina la clave de un estudiante (fuerza bruta o claves cortas) | Alto | Baja | Límite de intentos de Firebase Auth. Los usuarios no son correos reales | Pendiente: protección contra enumeración de correos y longitud mínima de clave en Auth ([#60](https://github.com/Jagr-ecu/repaso-admisiones-android/issues/60)) |
| **T** | A4 sube notas o puntos falsos a su propio progreso | Bajo | Media | El estudiante solo escribe en `estudiantes/{su uid}` y nunca borra | Aceptado: las notas no son oficiales ([ADR-0008](../adr/0008-ayuda-permitida-unica-puerta.md)) |
| **T** | A4 se cambia el rol a `admin` | Alto | Baja | Solo un admin escribe `usuarios/`, y solo con `rol == 'estudiante'`. Los admins se crean desde la consola o con `cuentas.py` | — |
| **R** | Un estudiante niega haber escrito un Writing o un admin niega un cambio de perfil | Bajo | Baja | Cada intento guarda fechas y semilla; los admins se crean solo desde el PC | Aceptado: sin registros de auditoría en el plan Spark ([ADR-0003](../adr/0003-firebase-plan-gratuito.md)) |
| **I** | A1 o A3 lee el progreso o los Writing de otro estudiante | Alto | Baja | `esElEstudiante(uid)` o `esAdmin()` para leer, probado con el emulador. HTTPS (y, tras #38, HTTP prohibido hacia Firebase) | Pendiente: HTTP prohibido hacia Firebase y Google ([#38](https://github.com/Jagr-ecu/repaso-admisiones-android/issues/38)) |
| **I** | El texto de un Writing incluye datos personales y queda en la nube | Medio | Media | Solo lo leen el estudiante y el admin | Aceptado: el Writing es la tarea y debe calificarse ([ADR-0009](../adr/0009-conversaciones-sin-guardar.md) solo excluye el chat) |
| **D** | A4 (o un fallo de la app) agota la cuota diaria gratuita de Firestore | Medio | Baja | Subida por lotes desde la cola, sin reintentos agresivos (WorkManager). La app funciona sin la nube (regla 8) | Aceptado ([ADR-0002](../adr/0002-room-fuente-de-verdad.md), [ADR-0003](../adr/0003-firebase-plan-gratuito.md)) |
| **E** | Una cuenta de estudiante consigue permisos de admin | Alto | Baja | El rol se lee en el servidor (`get(usuarios/uid)`), no del cliente. Tests de reglas por rol | — |

## Frontera 2: app ↔ LM Studio (red local)

Por HTTP sin cifrar, dentro de la wifi de casa. LM Studio no tiene HTTPS y su IP la elige el admin.

| STRIDE | Amenaza | Imp. | Prob. | Mitigación existente | Pendiente / aceptado |
|---|---|---|---|---|---|
| **S** | A2 se hace pasar por LM Studio y envía textos a un menor | Medio | Baja | La guardia revisa cada texto antes de mostrarlo (regla 2). La app solo habla con la dirección que escribió el admin | Pendiente: solo direcciones de red local ([#38](https://github.com/Jagr-ecu/repaso-admisiones-android/issues/38)) |
| **S** | El admin escribe por error un servidor de Internet y el chat sale de casa sin cifrar | Medio | Baja | — | Pendiente: `ClienteKiwi` rechaza direcciones que no son locales ([#38](https://github.com/Jagr-ecu/repaso-admisiones-android/issues/38)) |
| **T** | A2 altera la respuesta de Kiwi para revelar la solución | Bajo | Baja | La guardia bloquea textos que revelan la respuesta. La nota la calcula `:dominio`, nunca el modelo (regla 1) | — |
| **T** | El texto de Kiwi (o de un LM Studio falso) inyecta HTML o JavaScript en el WebView de fórmulas | Medio | Baja | `formulas.js` escapa `& < > "` fuera de las fórmulas. KaTeX corre con `trust: false` (sin `\href` ni `\includegraphics`). La interfaz JavaScript solo expone `altura(px)` | — |
| **R** | No queda registro de lo que Kiwi le dijo a un menor | Bajo | Media | La guardia filtra cada texto | Aceptado: las conversaciones no se guardan a propósito ([ADR-0009](../adr/0009-conversaciones-sin-guardar.md)) |
| **I** | A2 lee en la wifi las preguntas del estudiante y las respuestas de Kiwi | Medio | Baja | El chat no lleva el nombre del estudiante y no se guarda | Aceptado: HTTP en la red local ([ADR-0007](../adr/0007-kiwi-con-lm-studio-local.md)). Lo formaliza [#38](https://github.com/Jagr-ecu/repaso-admisiones-android/issues/38) |
| **I** | La API de LM Studio queda abierta a cualquier dispositivo de la wifi | Bajo | Media | Solo escucha en la red local | Aceptado: red de casa ([ADR-0007](../adr/0007-kiwi-con-lm-studio-local.md)). Recomendación en el runbook: firewall de Windows solo en el perfil "Privada" |
| **D** | LM Studio apagado, lento o caído | Bajo | Alta | La app sigue sin Kiwi (regla 8): comprobación de 2 s, Kiwi "descansa" 5 min, Writing queda pendiente. Probado en `ClienteKiwiTest` | — |
| **E** | A4 usa *prompt injection* para que Kiwi dé la respuesta, o para tener ayuda en una prueba | Medio | Media | `ayudaPermitida(tipo)` corta la llamada antes de la red en pruebas y examen (regla 3, un test por tipo). La guardia revisa la salida | — |

## Frontera 3: scripts del PC ↔ Firebase

`herramientas/cuentas.py` y `traspasar.py` usan el SDK de administración, que se salta las reglas de Firestore.

| STRIDE | Amenaza | Imp. | Prob. | Mitigación existente | Pendiente / aceptado |
|---|---|---|---|---|---|
| **S** | Alguien usa la credencial del SDK de administración robada | Alto | Baja | Solo en el PC, en `.gitignore` (`herramientas/credencial*.json`) | Pendiente: Gitleaks en el PR, antes del commit y en todo el historial ([#39](https://github.com/Jagr-ecu/repaso-admisiones-android/issues/39)) |
| **T** | Un traspaso equivocado pisa el progreso real de un estudiante | Medio | Baja | `traspasar.py` solo muestra por defecto; hace falta `--subir`. Ids estables y unión por reglas (`Union.kt`) | — |
| **R** | No se sabe quién creó o cambió una cuenta | Bajo | Baja | Solo hay un admin con acceso al PC | Aceptado ([ADR-0003](../adr/0003-firebase-plan-gratuito.md)) |
| **I** | La credencial o las claves de prueba acaban en el repositorio público | Alto | Media | `.gitignore`. Historial reescrito para quitar datos personales | Pendiente: Gitleaks ([#39](https://github.com/Jagr-ecu/repaso-admisiones-android/issues/39)) |
| **I** | La clave de firma del APK o `keystore.properties` se filtran | Alto | Baja | Fuera de git, con copia | Pendiente: reglas de Gitleaks para `keystore.properties` ([#39](https://github.com/Jagr-ecu/repaso-admisiones-android/issues/39)) |
| **D** | Un script con un bucle agota la cuota diaria | Bajo | Baja | Se ejecutan a mano, con confirmación | — |
| **E** | El SDK de administración se salta `firestore.rules` | Alto | Baja | Es su propósito. Solo vive en el PC del admin | Aceptado: fuera de alcance (control del PC) |

## Frontera 4: estudiante ↔ su dispositivo

El dispositivo puede ser compartido por varios estudiantes y no tiene servicios de Google
([ADR-0004](../adr/0004-sin-servicios-de-google.md)).

| STRIDE | Amenaza | Imp. | Prob. | Mitigación existente | Pendiente / aceptado |
|---|---|---|---|---|---|
| **S** | A3 usa la sesión abierta de otro estudiante | Medio | Media | Botón para salir, que avisa si hay datos sin subir | Aceptado: dispositivos de la familia. La sesión de admin se trata en [#60](https://github.com/Jagr-ecu/repaso-admisiones-android/issues/60) |
| **T** | A4 se salta el bloqueo de Kiwi en una prueba o el reloj del examen | Bajo | Baja | Un único punto de control en `:dominio` | Aceptado: las notas no son oficiales ([ADR-0008](../adr/0008-ayuda-permitida-unica-puerta.md)) |
| **T** | A4 cambia el paquete de contenido para ver las respuestas | Bajo | Baja | El paquete va dentro del APK firmado | Aceptado ([ADR-0005](../adr/0005-contenido-dentro-del-apk.md), [ADR-0008](../adr/0008-ayuda-permitida-unica-puerta.md)) |
| **T** | Alguien instala un APK modificado como si fuera una actualización | Alto | Baja | Android rechaza una actualización con otra firma. Clave de firma fuera de git | Pendiente: APK firmado solo desde el Environment `produccion` (épica CI/CD, #43) |
| **R** | — | — | — | Sin consecuencias: no hay transacciones entre usuarios | — |
| **I** | A3 ve el progreso de otro estudiante en la misma tablet | Medio | Media | Toda consulta de Room filtra por `usuario_id`, comprobado por `ArquitecturaTest` ([ADR-0017](../adr/0017-tests-de-arquitectura-con-konsist.md)) | — |
| **I** | Los datos de Room salen del dispositivo en una copia de seguridad del sistema | Medio | Media | — | Pendiente: excluir la base de las copias (`allowBackup`, `dataExtractionRules`) ([#61](https://github.com/Jagr-ecu/repaso-admisiones-android/issues/61)) |
| **I** | Un registro (`Log`) imprime un Writing, un mensaje de Kiwi o un nombre | Medio | Media | Un solo `Log` en el código (consola del WebView de fórmulas) | Pendiente: prohibido por test y corregido el de `Formula.kt` ([#42](https://github.com/Jagr-ecu/repaso-admisiones-android/issues/42)) |
| **D** | Varios estudiantes llenan el almacenamiento de un dispositivo | Bajo | Baja | Datos pequeños; la cola se vacía al subir | — |
| **E** | A4 consigue el panel de admin con la sesión de un adulto abierta | Medio | Baja | Las reglas no dejan al admin escribir progreso, solo leerlo y crear estudiantes | Pendiente: cerrar la sesión de admin al salir de la app ([#60](https://github.com/Jagr-ecu/repaso-admisiones-android/issues/60)) |

## Transversal: cadena de suministro y repositorio público (A5)

| Amenaza | Imp. | Prob. | Mitigación existente | Pendiente |
|---|---|---|---|---|
| Una dependencia con una vulnerabilidad conocida llega al APK | Alto | Media | Dependabot (`.github/dependabot.yml`) | Revisión de dependencias en el PR y CodeQL ([#40](https://github.com/Jagr-ecu/repaso-admisiones-android/issues/40)) |
| No se sabe qué versiones lleva un APK concreto | Medio | Media | Versiones fijadas en `libs.versions.toml` | SBOM CycloneDX ([#41](https://github.com/Jagr-ecu/repaso-admisiones-android/issues/41)) |
| Una GitHub Action comprometida (por ejemplo, una etiqueta movida) | Alto | Baja | — | Acciones fijadas por SHA ([#40](https://github.com/Jagr-ecu/repaso-admisiones-android/issues/40)) |
| Un secreto publicado en el repositorio público | Alto | Media | `.gitignore`, historial reescrito, `SECURITY.md` | Gitleaks ([#39](https://github.com/Jagr-ecu/repaso-admisiones-android/issues/39)) |
| El código minimizado no se usa y el APK es fácil de leer | Bajo | Alta | Las respuestas no dan ventaja: la nota no es oficial | Aceptado por ahora: activar R8 se evaluará con la MatePad ([ADR-0008](../adr/0008-ayuda-permitida-unica-puerta.md)) |

## Resumen de pendientes

| Issue | Mitiga |
|---|---|
| [#38](https://github.com/Jagr-ecu/repaso-admisiones-android/issues/38) HTTP solo hacia la red local | F1-I, F2-S |
| [#39](https://github.com/Jagr-ecu/repaso-admisiones-android/issues/39) Gitleaks | F3-S, F3-I, A5 |
| [#40](https://github.com/Jagr-ecu/repaso-admisiones-android/issues/40) CodeQL y revisión de dependencias | A5 |
| [#41](https://github.com/Jagr-ecu/repaso-admisiones-android/issues/41) SBOM | A5 |
| [#42](https://github.com/Jagr-ecu/repaso-admisiones-android/issues/42) Registros sin datos personales | F4-I |
| [#60](https://github.com/Jagr-ecu/repaso-admisiones-android/issues/60) Endurecer Firebase Auth y la sesión de admin | F1-S, F4-S, F4-E |
| [#61](https://github.com/Jagr-ecu/repaso-admisiones-android/issues/61) Excluir los datos de las copias de seguridad | F4-I |
| #43 (épica CI/CD) APK firmado con aprobación | F4-T |
