# Plan de migración a Android con Kotlin

Fecha: 2026-11-06. Proyecto de origen: `D:\Archivos\Proyectos\repaso-admisiones`.

## Pendientes para empezar

Estado al 2026-11-09. La migración aún no empieza. La base es el commit `0cdff9f`, en la rama `operaciones-en-cuaderno` del proyecto web, sin subir a GitHub.

**Avances del 2026-11-09**
- Proyecto de Firebase creado: id `repaso-admisiones`, plan gratuito.
- App Android registrada en Firebase con el paquete **`com.preparate.app`**. No se puede cambiar.
- `google-services.json` guardado en la raíz de esta carpeta. Coincide con el paquete. No debe subirse a ningún repositorio público.
- El usuario hizo los pasos de la consola: activar Authentication con Correo/contraseña (sin enlace por correo) y crear Firestore en modo de producción. No revisé la región elegida.
- Se descartó el inicio anónimo. No permite entrar desde otro dispositivo, se pierde al desinstalar y el admin no sabría quién es quién.
- Android Studio y el SDK están instalados. SDK en `C:\Users\Pc\AppData\Local\Android\Sdk`, con platform 36.1 y build-tools 36.0.0. Hay JDK 17 en el sistema y JDK 21 en Android Studio (`jbr`). En la caché de Gradle están Gradle 9.5.0, AGP 9.3.0, Kotlin 2.2.10 y el Compose BOM 2026.02.01.
- **Dispositivo real: Huawei MatePad 11.5 con HarmonyOS 4.3.** Instala APK de Android, pero **no tiene Google Play ni servicios de Google**. Se corrige lo anotado antes ("las tablets tienen Google Play").
- **`minSdk` pasa a 29 (Android 10)**, decidido por el usuario. HarmonyOS no muestra en Ajustes el nivel de API que ve la app.
- **Aviso:** la tablet no debe actualizarse a HarmonyOS NEXT / 5, porque esa versión ya no ejecuta apps de Android. Conviene desactivar las actualizaciones automáticas.
- El usuario pidió no construir nada por ahora. Continuar en otra sesión.

**Avances del 2026-11-09 (tarde): fase 0 hecha**
- El usuario decidió **saltarse el APK de prueba** e ir directo al desarrollo. La compatibilidad con HarmonyOS sin servicios de Google se comprueba en la fase 1, en la MatePad real. `prueba-compatibilidad/` queda fuera de git y se puede borrar.
- Proyecto Gradle creado con los cinco módulos y su `CLAUDE.md`. Usa AGP 9.3.0, Gradle 9.5.0, Kotlin 2.2.10, el Compose BOM 2026.02.01 y el BOM de Firebase 34.4.0.
- `./gradlew :dominio:test :app:assembleDebug` pasa. El plugin `google-services` 4.4.4 **sí funciona** con AGP 9.3.
- `:dominio` ya tiene `TipoSesion` y `ayudaPermitida()`, con sus tests.
- Repositorio git creado, con el remoto `https://github.com/Jagr-ecu/repaso-admisiones-android.git`. `google-services.json` está en `.gitignore`.
- En el proyecto web, el commit `0cdff9f` tiene la etiqueta `base-migracion-android`.
- **El APK vacío se instala y funciona en la MatePad: fase 0 cerrada.** Firebase se inicia al abrir la app sin cerrarla, aunque no haya servicios de Google.
- En el proyecto web se hizo el commit `a4ee32a`, que añade `.codegraph/` al `.gitignore`. Va después de la etiqueta base, así que no la cambia.

**APK de prueba de compatibilidad (descartado el 2026-11-09)**
Está en `prueba-compatibilidad/`, un proyecto Gradle aparte que se borra después. Por eso no va en los cinco módulos. Tiene una sola pantalla, sin Compose, que muestra:
- marca, modelo, versión de Android y nivel de API reales;
- si existen los servicios de Google;
- inicio de sesión o creación de la cuenta `prueba@preparate.invalid`, que también prueba el dominio `.invalid`;
- escritura en `pruebas/{uid}` de Firestore y lectura desde el servidor (`Source.SERVER`).

Para continuar:
- [ ] Antes de abrirlo, poner reglas provisionales en Firestore. En modo de producción todo está denegado:
  `match /pruebas/{uid} { allow read, write: if request.auth != null && request.auth.uid == uid; }`
- [ ] Compilar con `./gradlew assembleDebug` dentro de `prueba-compatibilidad/`. Aún no se ha compilado. No es seguro que el plugin `google-services` 4.4.4 funcione con AGP 9.3. Si falla, se puede iniciar Firebase a mano con `FirebaseOptions`. El BOM de Firebase (34.4.0) no está en la caché, así que hay que descargarlo.
- [ ] Llevar `app/build/outputs/apk/debug/app-debug.apk` a la MatePad (USB o wifi), instalarlo y anotar aquí lo que muestra la pantalla.
- [ ] Si todo funciona, borrar la cuenta de prueba y la colección `pruebas`, y seguir con la fase 0. Si algo falla, replantear la sincronización antes de construir.

**Fase 0: lo debe hacer el usuario**
- [x] Crear el proyecto de Firebase en el plan gratuito, con su cuenta de Google.
- [x] Anotar la marca y la versión de cada tablet: MatePad 11.5, HarmonyOS 4.3. Si hay más tablets, anotarlas también.
- [x] Confirmar que Android Studio y el SDK de Android están instalados.

**Fase 0: lo hace Claude cuando el usuario dé el visto bueno**
- [ ] Probar la compatibilidad con el APK de prueba (ver arriba).
- [ ] Marcar el commit `0cdff9f` con una etiqueta como base de la migración.
- [ ] Crear el repositorio Android con Gradle, los cinco módulos y su `CLAUDE.md`. Usar `applicationId` `com.preparate.app`, `minSdk` 29 y `targetSdk` 36. No subir `google-services.json` al repositorio.

**Otros arreglos en el proyecto web**
- [x] Hacer commit de `.codegraph/` en `.gitignore` (`a4ee32a`).
- [ ] Ampliar `ing.daily-routines.significado-frecuencia`, que solo tiene 4 variantes distintas, antes de publicar el contenido.

## 1. Objetivo y decisiones

Pasar la app "Prepárate" a Android nativo. Debe funcionar sin Internet y sincronizar el progreso gratis.

| Tema | Decisión |
|---|---|
| Lenguaje e interfaz | Kotlin y Jetpack Compose |
| Temas y preguntas | Dentro de la app. Variantes generadas por adelantado con Python |
| Progreso | Room en el dispositivo como fuente de verdad. Firestore como copia en la nube |
| Dispositivos objetivo | Primero tablets, también celulares. Desde Android 10 (sección 5). Tablet real: MatePad 11.5 con HarmonyOS 4.3, sin servicios de Google |
| Cuentas | Login básico con usuario y clave, sin correos a la vista. Por dentro usa Firebase Auth. Cada estudiante tiene su propio progreso |
| Conversaciones con Kiwi | No se guardan en el dispositivo ni en la nube (sección 6) |
| Roles | Solo `estudiante` y `admin`. No hay rol de tutor |
| Dispositivos | Cada estudiante puede entrar con otro dispositivo, y varios pueden compartir uno |
| Panel de admin | Dentro de la misma app y de solo lectura. Muestra el progreso de todos los estudiantes |
| Kiwi | En la app, conectado a LM Studio en el PC. La app comprueba si está activo |
| Bloqueo de Kiwi en pruebas | Vive en la app (cambio aceptado de la regla 3) |
| Costo | Plan gratuito de Firebase. El APK se instala directamente, sin Google Play |

La app web sigue siendo el sitio donde se escribe y valida el contenido. También sigue en uso hasta terminar la fase 7.

## 2. Arquitectura

```text
repaso-admisiones (Python)                    repaso-admisiones-android (Kotlin)
content/ ─► validate-content                  assets/contenido/
         └► exportar-paquete ───────────────► lecciones y variantes (solo lectura)
                                                       │
                                                       ▼
                                              :dominio (Kotlin puro: reglas, verificador,
                                                       cuaderno, selección, repasos)
                                                       │
                       LM Studio ◄── :tutor ───────────┤
                       en el PC                        ▼
                                              :datos (Room: progreso y cola de subida)
                                                       │
                                              :sincro (WorkManager)
                                                       │ con Internet
                                                       ▼
                                              Firestore + Firebase Auth
                                                       │
                                                       ▼
                                              Panel de admin en cualquier dispositivo
```

Módulos de Gradle:

| Módulo | Contenido | Depende de Android |
|---|---|---|
| `:dominio` | Reglas, verificador, cuaderno, selección y repasos | No: se prueba rápido en la JVM |
| `:datos` | Room y lectura del paquete de contenido | Sí |
| `:tutor` | Cliente de LM Studio, prompts, guardia y calificación de Writing | Solo para la red |
| `:sincro` | Subida, descarga y reconstrucción con Firestore | Sí |
| `:app` | Pantallas en Compose y navegación | Sí |

## 3. Contenido dentro de la app

### Medición con el contenido actual

Generé 300 semillas por plantilla con `app/generator/variantes.py` y eliminé repetidos con `huella()`:

| Materia | Plantillas | Variantes distintas |
|---|---|---|
| Matemática | 51 | 10 314 |
| Lengua | 35 | 695 |
| Inglés | 41 | 885 |
| **Total** | **127** | **11 894** |

- **Tamaño:** 9,1 MB en JSON y 0,4 MB comprimido.
- **Tiempo:** 5 segundos en el PC.
- **Errores:** ninguna plantilla falló.
- **Variantes escasas:** 66 plantillas dieron menos de 20. Ya tienen pocas también en la web, así que generarlas por adelantado no les quita variedad.

El tamaño no es un problema. El cálculo real se hará en la fase 2 con el formato definitivo.

### Paquete de contenido

Se añade `exportar-paquete` a `app/cli.py` del proyecto web. Genera:

```text
assets/contenido/
  manifiesto.json          versión del paquete, fecha y commit de origen
  curso.json               materias, secciones y orden
  lecciones/<id>.json
  variantes/<plantilla>.json
```

Cada variante guarda:
- `semilla` y `huella`
- enunciado y respuesta
- respuestas aceptadas
- opciones y fragmentos
- pistas y solución
- errores comunes
- cuenta, si es de cuaderno

Cada plantilla guarda su `plantilla_version`, su tipo y sus datos de verificador.

Reglas del paquete:
- La exportación falla si `validate-content` falla.
- Solo publica contenido con `estado: revisado`.
- `--borradores` queda solo para desarrollo.
- Cada plantilla va en su propio archivo y se carga cuando hace falta.
- Actualizar contenido significa publicar un APK nuevo. El progreso no se toca.

**Aviso:** hoy las 48 lecciones están en `borrador`. Antes de la versión real hay que revisarlas y cambiar su estado.

### Qué se porta y qué no

| Código actual | Destino |
|---|---|
| `generator/expresiones.py`, generación en `variantes.py` | Se queda en Python. No hace falta intérprete en Kotlin |
| `generator/verificador.py` | Se porta. Decide `2/4 = 1/2`, `0,5 = 0.5`, mayúsculas y tildes. Las fracciones usan `BigInteger` |
| `generator/cuaderno.py` | Se genera en Python. Se porta solo la revisión de casillas |
| `scheduler/seleccion.py` | Se porta, pero elige entre variantes ya generadas |
| SymPy | No se lleva a Android |

## 4. Progreso y sincronización

### Base local (Room)

Las tablas parten de `app/db/migraciones/`: `sesiones`, `intentos`, `memoria` y `puntos`, más una tabla nueva `cola_subida`.

Cambios frente al esquema actual:
- **IDs:** `sesiones` e `intentos` pasan de números consecutivos a UUID. Así dos dispositivos no repiten el mismo ID.
- **Usuario:** `usuario_id` pasa a ser el UID de Firebase, y `cola_subida` también lo guarda. Cada consulta filtra por usuario, porque en un dispositivo puede haber varios estudiantes.
- **Tablas que no pasan:** `usuarios` lo reemplaza Firebase Auth, y `fragmentos` deja de hacer falta. `mensajes_tutor` se elimina porque las conversaciones no se guardan (sección 6).
- **Fechas:** siguen en UTC. Los días se cuentan en la zona local, como hoy.

### Firestore

```text
usuarios/{uid}                      nombre, usuario y rol; solo lo escribe un admin
estudiantes/{uid}/resumen/panel     lo que el admin ve primero
estudiantes/{uid}/sesiones/{uuid}
estudiantes/{uid}/intentos/{uuid}
estudiantes/{uid}/puntos/{motivo_referencia}
```

### Reglas de sincronización

1. Todo se escribe primero en Room y en la cola de subida, dentro de la misma transacción.
2. WorkManager sube la cola por lotes cuando hay red y reintenta si falla. Solo sube lo del usuario con sesión iniciada, porque Firestore rechaza escribir en nombre de otro.
3. Subir dos veces no duplica nada, porque cada documento usa su ID estable.
4. Al abrir la app con Internet, se baja lo que cambió desde la última sincronización.
5. La regla de unión es por campo, porque un intento se crea al mostrarse y se completa al responder:
   - gana el intento ya respondido sobre el no respondido;
   - gana la sesión cerrada sobre la abierta;
   - una nota de Writing nunca se borra con un valor vacío.
6. `puntos` conserva su clave única `(usuario, motivo, referencia)` como ID del documento.
7. `memoria` no se sincroniza. Se recalcula en orden desde los intentos, para que un dispositivo atrasado no pise lo nuevo.
8. El resumen del panel se recalcula después de sincronizar y se sube como un solo documento.

### Otro dispositivo

| Caso | Comportamiento |
|---|---|
| Primer inicio de sesión | Pide Internet, descarga el historial y reconstruye `memoria` |
| Tema abierto en otro dispositivo | Si ya se subió, se continúa. Si no, se empieza uno nuevo |
| Prueba abierta en dos dispositivos sin conexión | Se conservan las dos. La sección queda aprobada si una llega a 7 y el panel muestra ambas |
| Cerrar sesión con datos sin subir | Avisa, pide subirlos primero y nunca los borra en silencio |
| Dos estudiantes comparten un dispositivo | Cada uno ve solo lo suyo. Lo que no se subió de uno espera hasta que vuelva a entrar |

### Cuentas y roles

| Acción | Estudiante | Admin |
|---|---|---|
| Estudiar y guardar progreso | Sí, el suyo | No |
| Ver progreso | Solo el suyo | El de todos, sin poder modificarlo |
| Crear estudiantes | No | Sí, desde la app |
| Cambiar su propia clave | Sí | Sí |
| Cambiar una clave olvidada | No | Sí, con un script en el PC |

**Primer admin:** se crea a mano en la consola de Firebase, con su documento en `usuarios/{uid}`. Los demás admins usan el mismo proceso.

**Crear estudiantes:**
- El admin escribe nombre, usuario y clave en una pantalla sencilla, y no hay registro público.
- La app crea la cuenta con una segunda instancia de `FirebaseApp`. Así el admin no pierde su sesión y no hacen falta Cloud Functions ni un plan de pago.
- Después, el admin escribe `usuarios/{uid}` con el rol `estudiante`. Si eso falla, la pantalla permite reintentar.

**Entrar:** todos escriben solo usuario y clave, sin ningún correo real. Firebase Auth exige un formato de correo, así que la app convierte `ana` en `ana@preparate.invalid`. Ese dominio está reservado y nunca recibe mensajes.

**Usuarios:** de 3 a 20 caracteres, con minúsculas, números, `.`, `_` y `-`, sin tildes, `ñ` ni espacios. Firebase garantiza que no se repitan. La clave tiene al menos 6 caracteres, el mínimo de Firebase.

**Clave olvidada:** sin correo no hay recuperación automática, y la app no puede cambiar la clave de otra persona. Por eso el admin ejecuta en el PC `herramientas/cambiar_clave.py <usuario>`, que usa el SDK de administración de Firebase. La credencial de ese script se queda solo en el PC: nunca va en el APK ni en git.

**Fuera de esta versión:** editar roles desde la app y borrar estudiantes. Se hace desde la consola.

### Reglas de seguridad

Como punto de partida:

```text
function rol() {
  return get(/databases/$(database)/documents/usuarios/$(request.auth.uid)).data.rol;
}
match /usuarios/{uid} {
  allow read: if request.auth != null && (request.auth.uid == uid || rol() == 'admin');
  allow write: if request.auth != null && rol() == 'admin';
}
match /estudiantes/{uid}/{doc=**} {
  allow read: if request.auth != null && (request.auth.uid == uid || rol() == 'admin');
  allow write: if request.auth != null && request.auth.uid == uid && rol() == 'estudiante';
}
```

Como la app puede crear cuentas, alguien externo también podría crear una con la API pública de Firebase. Por eso escribir progreso exige el rol `estudiante`: una cuenta sin documento en `usuarios` no puede leer ni escribir nada.

Estas reglas se prueban con el emulador de Firebase, con estos casos:
- un estudiante intenta leer a otro;
- un estudiante intenta cambiar su rol;
- un admin intenta escribir progreso;
- una cuenta externa intenta leer o escribir.

Los datos son de menores. Por eso no hay registro público y se usa solo un nombre sin apellidos. Si los estudiantes son de otras familias, avisa a sus responsables de que el admin ve el progreso, incluidos los textos de Writing, y de que eso se guarda en Firebase.

### Límites gratuitos

El plan Spark ofrece 1 GiB, 50 000 lecturas, 20 000 escrituras y 20 000 borrados al día, para todo el proyecto y no por estudiante.

**Estimación sin medir:** con unos 100 ejercicios diarios, un estudiante escribe alrededor de 250 documentos, así que caben unas decenas de estudiantes activos al día. Las reglas también gastan lecturas al consultar el rol, y un admin consume una lectura por estudiante al abrir la lista, más las de cada detalle. Si se pasa el límite, Firestore rechaza operaciones hasta el reinicio diario; la app sigue funcionando con Room y sube después.

## 5. Interfaz

| Pantalla actual (`web/src`) | Pantalla en Compose |
|---|---|
| `routes/entrar` | Inicio de sesión |
| `routes/+page` | Elegir materia |
| `routes/materia/[id]` | Camino de la materia |
| `routes/burbuja/[id]`, `Practica`, `Respuesta`, `Enunciado` | Tema |
| `Cuaderno` | Cuaderno, con cuadrícula propia y teclado numérico |
| `routes/prueba/[id]` | Prueba y examen final, sin tutor |
| `routes/resultado/[id]` | Resultado |
| `routes/repaso/[materia]` | Repaso |
| `routes/panel`, `GraficoUso` | Panel de admin: lista de estudiantes y detalle de cada uno |
| (nueva) | Crear estudiante, solo para admin |
| (nueva) | Cambiar mi clave, para todos |
| `ChatTutor`, `Pensando` | Kiwi |
| `Mascota`, `Celebracion` | Vectores y animación propios |

### Tablets y celulares

Se diseña primero para tablet y luego se adapta al celular con una sola base de pantallas, usando `WindowSizeClass`.

| Parte | Tablet | Celular |
|---|---|---|
| Orientación | Vertical y horizontal | Solo vertical |
| Contenido | Ancho máximo, para que las líneas no se estiren | Una columna |
| Kiwi | Panel junto a la pregunta | Hoja que sube desde abajo |
| Cuaderno | Cuadrícula completa | Desplazamiento lateral si la cuenta no cabe |
| Fórmulas anchas | Se ven completas si caben | Desplazamiento lateral, sin encoger hasta que no se lean |

Las tablets deben admitir las dos orientaciones porque, con `targetSdk` 36, Android 16 ignora el bloqueo de orientación en pantallas grandes.

Versiones de Android:

| Ajuste | Valor | Motivo |
|---|---|---|
| `minSdk` | 29 (Android 10) | Cambiado el 2026-11-09, antes era 31. La MatePad usa HarmonyOS 4.3, que es compatible con un nivel de Android anterior a 12 |
| `targetSdk` | 36 (Android 16) | Es reciente y no obliga aún a pedir el permiso de red local. Como el APK no va a Google Play, no hay que subirlo por su política |
| `compileSdk` | La última estable | Permite usar las librerías actuales |

Pruebas mínimas en cada fase con pantallas:
- celular de 360×800 dp en Android 10;
- tablet de 800×1280 dp, vertical y horizontal, en Android 10 y 16;
- la MatePad real, sin servicios de Google;
- teclado abierto en el cuaderno y en las respuestas;
- la multiplicación más larga y la fórmula más ancha, en el celular.

### Fórmulas

El contenido usa LaTeX avanzado: `\begin{array}`, `\underbrace`, `\textcolor` y `\textbf`.

**Decisión:** usar KaTeX real dentro de un WebView, con sus archivos incluidos en la app. Funciona sin Internet y dibuja las fórmulas igual que hoy.

- Un texto sin `$` usa `Text` de Compose.
- Un texto con fórmula usa un componente `TextoConFormulas`, que reutiliza la lógica de `web/src/lib/texto.ts`.
- Una lección completa puede ir en un solo WebView.
- Hay librerías nativas para Compose, pero no se comprobó que cubran esos comandos y el costo de un fallo es enseñar mal una fórmula.

En la fase 1 se mide si una lista con varios WebView va lenta en el dispositivo real.

## 6. Kiwi

La app llama directamente a LM Studio en el PC.

- **Comprobación:** `GET /v1/models` con 2 s de espera, como hoy, y hasta 120 s para la respuesta.
- **Sin conexión con Kiwi:** aviso amable y pistas escritas. La pista de la lista siempre sale primero.
- **Dirección:** el admin la configura en los ajustes de cada dispositivo. Conviene fijar la IP del PC en el router.
- **LM Studio:** debe aceptar conexiones de la red local.
- **Tráfico HTTP:** Android bloquea HTTP sin cifrar por defecto, así que hay que permitirlo en la configuración de seguridad de red. La sincronización con Firebase sigue por HTTPS.
- **Permiso de red local:** con `targetSdk` 36 no hace falta pedirlo. Al subir a 37 o más, habrá que pedir `ACCESS_LOCAL_NETWORK` y manejar que se niegue; sin él, la conexión a LM Studio se queda esperando hasta agotar el tiempo.
- **Red:** los estudiantes se conectan al wifi de la casa donde está el PC. Fuera de esa red, Kiwi aparece como no disponible.
- **Varios estudiantes a la vez:** comparten un solo modelo en el PC, así que las respuestas pueden tardar más.

Código a portar:

| Código actual | Nota |
|---|---|
| `tutor/guardia.py` | **Crítico.** Es la red de seguridad de la regla 2. Se porta con todos sus casos de prueba |
| `tutor/prompts.py` | Se exporta o se copia sin cambios |
| `tutor/servicio.py` | Conversación, pistas y calificación de Writing. Se quita todo uso de `mensajes_tutor` |
| `tutor/cliente.py` | Cliente HTTP y parámetros de muestreo |
| `tutor/rag.py` | **No se porta en la primera versión** |

**Por qué no hace falta RAG:** el código actual envía la lección completa si el material del concepto tiene menos de 2 500 caracteres. Medido hoy, solo 1 de 48 conceptos pasa ese límite (`matematica`, 2 589 caracteres). Por eso la app envía el material del concepto y sube ese límite lo justo, sin embeddings ni `sqlite-vec`. La fase 6 comprueba que Kiwi siga respondiendo bien con esa lección.

**Writing:** la calificación corre en el dispositivo del estudiante, cuando Kiwi está activo. Un Writing hecho fuera de esa red queda pendiente y se califica solo al volver. Esto puede retrasar su nota y la apertura del examen final de Inglés. El admin ve los pendientes de cada estudiante en el panel, pero no los califica desde otro dispositivo.

### Conversaciones sin guardar

Hoy `mensajes_tutor` no sirve solo para el panel: también da a Kiwi su historial, evita repetir pistas y conserva su primer comentario tras un fallo. En Android, esos datos viven solo en la memoria de la pantalla.

| Dato | Dónde queda |
|---|---|
| Mensajes del estudiante y de Kiwi | Solo en memoria, mientras dure ese ejercicio. Nunca en Room ni en Firestore |
| Pistas de Kiwi ya vistas | Solo en memoria |
| Pistas escritas usadas | Solo el contador `pistas_usadas` del intento, que ya existe |
| Texto de Writing, nota y comentario de la rúbrica | Se guardan en `intentos`, porque son el trabajo calificado y no una conversación |

**Consecuencias aceptadas:**
- Si el estudiante sale del ejercicio o se cierra la app, la conversación se pierde.
- Al volver, Kiwi empieza de cero y podría repetir una pista.
- El admin no puede revisar si Kiwi dio una respuesta. La guardia es la protección, y por eso no se reduce ninguna de sus pruebas.

**Fuera de la app:** LM Studio recibe cada mensaje para responder. Hay que revisar si guarda registros en el PC y desactivarlos o limpiarlos, para cumplir del todo con esta decisión.

## 7. Reglas del proyecto en Android

| Regla | Cómo se cumple |
|---|---|
| 1. El código calcula la respuesta | Python la calcula al exportar y `:dominio` la verifica. Kiwi nunca la calcula |
| 2. Kiwi no revela la respuesta | Prompt y guardia portada, antes de mostrar cada texto |
| 3. Kiwi bloqueado en pruebas | **Cambia:** ver el texto nuevo abajo |
| 4. Materias separadas | La selección filtra por materia. Se prueba en `:dominio` |
| 5. Nada hardcodeado | Todo sale del paquete de contenido |
| 6. Reglas del curso | `domain/reglas.py` y `progreso.py` portados con los mismos casos |
| 7. Español y lenguaje sencillo | Textos de interfaz en `strings.xml`, copiados de la web |
| 8. Sin LM Studio la app sigue | La práctica y las pruebas no dependen de la red |
| 9. Puntos y aparición de Kiwi | Lógica portada, con pista local antes de cualquier llamada |
| 10. Tres errores y repetición | Lógica portada. Se repite la misma variante por su semilla |

Texto propuesto para la regla 3 en el `CLAUDE.md` del proyecto Android:

> 3. En pruebas de sección y examen final no hay tutor ni pistas. El bloqueo vive en `:dominio`: `ayudaPermitida(tipo)` es la única puerta, y `:tutor` la consulta antes de cualquier llamada. Hay un test para cada tipo de sesión.

El `CLAUDE.md` del proyecto Android también debe cambiar "UN estudiante" por varios estudiantes con cuenta propia, y "adulto" por "admin".

**Con varios estudiantes, estos dos riesgos pesan más:** el bloqueo de Kiwi vive en la app y el reloj de las pruebas usa la hora del dispositivo, que se puede cambiar. Se aceptan porque esta app prepara para el examen y sus notas no son oficiales. Si esas notas llegaran a tener consecuencias reales, habría que revisarlo.

## 8. Fases

Cada fase termina con sus pruebas pasando. El tamaño es relativo, no una promesa de fechas.

### Fase 0: preparar el punto de partida (pequeña)
- Confirmar en git los cambios pendientes del proyecto web.
- Marcar ese commit con una etiqueta como base de la migración.
- Crear el repositorio Android con Gradle, los cinco módulos y un `CLAUDE.md` propio.
- Configurar `minSdk` 29 y `targetSdk` 36.
- Anotar marca y versión de Android de cada dispositivo real. La MatePad 11.5 tiene HarmonyOS 4.3 y no tiene Google Play.
- Crear el proyecto de Firebase en plan Spark.

**Listo cuando:** un APK vacío se instala en el dispositivo del estudiante.

### Fase 1: prueba pequeña y decisión de seguir (pequeña)
Es una prueba de punta a punta, con código que se puede desechar. Usa un solo tema de Matemática que tenga fórmulas.
- El tema se juega en modo avión con una lista corta de variantes.
- KaTeX en WebView se ve bien y no va lento.
- Un intento llega a Firestore al recuperar Internet y un admin lo lee desde otro dispositivo.
- La app comprueba LM Studio y recibe una respuesta de Kiwi por el wifi de casa.
- Se crea una cuenta solo con usuario y clave, se entra con ella y el script del PC cambia su clave en el plan Spark.

**Listo cuando:** los cinco puntos funcionan en el dispositivo real. Si alguno falla, se revisa el plan antes de seguir.

### Fase 2: paquete de contenido y casos de referencia (mediana)
En el proyecto web:
- Añadir `exportar-paquete` y sus tests.
- Añadir `exportar-referencia`, que genera casos en JSON para comprobar Kotlin contra Python: verificador, cuaderno, guardia, notas, desbloqueo y puntos.

**Listo cuando:** Android lee las 127 plantillas y sus variantes.

### Fase 3: dominio en Kotlin (grande)
Portar a `:dominio` estos archivos, con unas 1 500 líneas de Python en total:
- `domain/reglas.py`
- `domain/progreso.py`
- `domain/estudio.py`, separando la lógica del acceso a la base
- `scheduler/seleccion.py` y `scheduler/memoria.py`
- `generator/verificador.py`
- la revisión de `generator/cuaderno.py`

Para los repasos, evaluar primero la librería oficial [FSRS-Kotlin](https://github.com/open-spaced-repetition/awesome-fsrs) (MIT, FSRS-6). Hay que comprobar su mantenimiento, sus parámetros y que funcione sin dispersión de fechas, como `enable_fuzzing=False`.

**Listo cuando:** pasan todos los casos de referencia y los tests de `test_reglas.py`, `test_pruebas.py` y `test_cuaderno.py` portados.

### Fase 4: datos y pantallas de estudio (grande)
- Crear Room, con migraciones desde la primera versión.
- Crear las pantallas de materia, camino, tema, cuaderno, prueba, examen, resultado y repaso.
- Reanudar un tema o prueba tras cerrar la app.
- Probar a tamaño tablet, con teclado abierto y sin Internet.

**Listo cuando:** se juegan las tres materias de principio a fin en modo avión, salvo la calificación de Writing.

### Fase 5: cuentas, sincronización y panel de admin (grande)
- Añadir Firebase Auth, los roles `estudiante` y `admin`, y reglas de seguridad probadas en el emulador.
- Añadir las pantallas de crear estudiante y cambiar la clave propia, y el script `herramientas/cambiar_clave.py`.
- Añadir cola de subida por usuario, descarga y reconstrucción de `memoria`.
- Portar el panel desde `domain/panel.py`, con lista de estudiantes y detalle de cada uno.

Pruebas:
- Pasar días sin Internet y luego sincronizar.
- Cerrar la app durante una subida.
- Entrar en un segundo dispositivo.
- Usar dos dispositivos sin conexión y unirlos.
- Crear dos estudiantes, usar los dos en el mismo dispositivo y comprobar que no se mezcla nada.
- Cambiar una clave con el script y entrar con la nueva.
- Intentar crear un usuario repetido o con caracteres no válidos.
- Intentar escribir progreso como admin y leer a otro estudiante, que debe fallar.

**Listo cuando:** cada estudiante ve solo lo suyo, sus dispositivos coinciden y el panel de admin muestra bien a todos.

### Fase 6: Kiwi (mediana)
- Añadir `:tutor`, los ajustes de dirección y el permiso de red local.
- Portar la guardia y calificar Writing, incluidos los pendientes.
- Probar con LM Studio encendido, apagado y apagándose a mitad de una respuesta.
- Insistir en pedir la respuesta y comprobar que Kiwi nunca la dé.
- Comprobar que Kiwi no aparece en pruebas ni en el examen.
- Comprobar que ninguna conversación queda en Room ni en Firestore, y que la app sigue bien al salir y volver a un ejercicio.
- Revisar los registros de LM Studio en el PC.

**Listo cuando:** todos esos casos pasan, incluido lo que aún falta en la web: Writing y el examen final de Inglés.

### Fase 7: traspaso y uso real (pequeña a mediana)
- Crear las cuentas reales: el rol `adulto` de la web pasa a ser `admin`.
- Crear un script que suba el progreso real de `data/app.db` a la cuenta del estudiante actual, con IDs nuevos y `memoria` recalculada. No sube `mensajes_tutor`.
- Respaldar `data/app.db` antes y comprobar que notas, puntos y temas coinciden después.
- Revisar el contenido en `borrador` y publicarlo.
- Generar un APK firmado e instalarlo.
- Adaptar la skill `estudiante-de-prueba`, que hoy usa Chrome, para probar en el dispositivo o el emulador.
- Dejar de usar la web para estudiar, para que no se creen dos historiales distintos.

**Listo cuando:** los estudiantes terminan una semana real sin pérdidas de progreso.

**Importante:** guarda la clave de firma del APK en un lugar seguro y con copia. Sin ella no se puede instalar una actualización encima de la versión anterior.

## 9. Riesgos

| Riesgo | Consecuencia | Cómo se reduce |
|---|---|---|
| Kotlin y Python corrigen distinto | Una respuesta correcta se marca como mala | Casos de referencia generados por Python |
| La guardia portada deja pasar la respuesta | Se rompe la regla 2 | Portar todos los tests de `test_tutor.py` y añadir casos de referencia |
| WebView va lento | El tema se siente pesado | Medir en la fase 1 y usar `Text` cuando no haya fórmulas |
| Datos sin subir en un dispositivo perdido | Se pierde lo reciente | Subir al terminar cada tema y mostrar la última sincronización en ajustes |
| Se pierde la clave de firma | No se puede actualizar sin reinstalar | Copia de seguridad, y el progreso se recupera desde Firestore |
| Un estudiante ve o modifica datos de otro | Se exponen datos de menores | Reglas de seguridad probadas en el emulador, y consultas locales filtradas por usuario |
| Se comparte la clave del admin | Se ve el progreso de todos | Clave fuerte, propia de cada admin, y documentos de rol editables solo por admin |
| Se filtra la credencial del script de claves | Alguien podría controlar todas las cuentas | Guardarla solo en el PC, fuera de git, y revocarla si se expone |
| El celular no tiene espacio para una cuenta o fórmula | El estudiante no ve bien el ejercicio | Desplazamiento lateral y pruebas con los casos más anchos |
| Muchos estudiantes agotan la cuota diaria | La subida se pausa hasta el día siguiente | Room sigue funcionando. Vigilar el uso en la consola de Firebase |
| Cambia la IP del PC | Kiwi parece apagado | IP fija en el router y ajuste visible para el admin |
| Cambian los límites de Firebase | La sincronización se detiene un tiempo | Room sigue funcionando. Revisar `firebase.google.com/pricing` en la fase 0 |
| Web y app divergen durante la migración | Dos historiales distintos | No estudiar en la app hasta la fase 7 |

## 10. Por verificar

Estos puntos no se pudieron confirmar y se resuelven en la fase indicada:

| Punto | Fase |
|---|---|
| Rendimiento de KaTeX en WebView en el dispositivo real | 1 |
| Marca y versión de Android de los dispositivos reales | 0 |
| Que `minSdk` 29 funcione con las versiones de Compose y Firebase elegidas (no encontré una fuente oficial de sus mínimos) | 0 |
| Que Firebase Auth y Firestore funcionen en HarmonyOS 4.3 sin servicios de Google (APK de prueba) | 0 |
| Que Firebase Auth acepte `@preparate.invalid` | 1 |
| Que el script cambie claves en el plan Spark (no encontré confirmación oficial) | 1 |
| Si LM Studio guarda registros de los mensajes en el PC | 6 |
| Estado de FSRS-Kotlin y si sus resultados coinciden con `py-fsrs` | 3 |
| Que Kiwi responda bien sin RAG en el concepto más largo | 6 |
| Que crear cuentas con una segunda instancia funcione en el plan Spark sin cerrar la sesión del admin | 1 |
| Consumo real de lecturas y escrituras por estudiante | 5 |

## Fuentes
- [Precios de Firebase](https://firebase.google.com/pricing/?authuser=2)
- [Permiso de red local en Android](https://developer.android.com/privacy-and-security/local-network-permission?authuser=0)
- [Cambios de orientación en Android 16](https://android-developers.googleblog.com/2025/01/orientation-and-resizability-changes-in-android-16.html?m=1)
- [Firebase Auth en equipos sin Google Play](https://firebase.blog/posts/2020/10/firebase-auth-android-non-gms)
- [Error de FirebaseUI en Huawei](https://github.com/firebase/FirebaseUI-Android/issues/1579)
- [Lista de implementaciones de FSRS](https://github.com/open-spaced-repetition/awesome-fsrs)
- [FSRS-Kotlin](https://raw.githubusercontent.com/open-spaced-repetition/fsrs-kotlin/main/README.md)
