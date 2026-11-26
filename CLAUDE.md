# Prepárate para Android — Examen de admisión 8vo EGB

## Proyecto
App Android nativa para preparar a varios estudiantes de 11–12 años para el examen de admisión.
Cada estudiante tiene su propia cuenta y su propio progreso.
Es la migración de la web `D:\Archivos\Proyectos\repaso-admisiones`. La base es el commit `0cdff9f`, etiquetado como `base-migracion-android`.
El plan completo está en `PLAN-MIGRACION.md`.
Todo va dentro de un **curso**. Hoy solo existe **Admisiones** (`id: admisiones`), con tres materias independientes: Lengua y Literatura, Matemática e Inglés. Más adelante se pueden añadir otros cursos.
Cada materia tiene un camino de secciones, temas de menos de 5 minutos, una prueba por sección y un examen final de 20 minutos como máximo.
Objetivo pedagógico: que el estudiante razone, no que memorice respuestas.

## Stack
- Kotlin y Jetpack Compose, con `minSdk` 29 y `targetSdk` 36.
- Progreso en Room: el dispositivo es la fuente de verdad. Firestore y Firebase Auth guardan la copia en la nube, en el plan gratuito.
- El contenido va dentro del APK, en `assets/contenido/`, generado con `exportar-paquete` del proyecto web.
- Kiwi, el tutor, usa LM Studio en el PC de la casa (API compatible con OpenAI) por la red local.
- Fórmulas con KaTeX dentro de un WebView, con sus archivos incluidos en la app.
- Dispositivo real: Huawei MatePad 11.5 con HarmonyOS 4.3, **sin servicios de Google**. No debe actualizarse a HarmonyOS 5.

## Módulos
dominio/   Kotlin puro: reglas, verificador, cuaderno, selección, repasos, guardia de Kiwi y nota del Writing. Sus tests corren en la JVM
datos/     Room y lectura del paquete de contenido
tutor/     Kotlin puro (sin Android): cliente de LM Studio, prompts y servicio de Kiwi. Tests en la JVM
sincro/    subida, descarga y reconstrucción con Firestore
app/       pantallas Compose y navegación
arquitectura/  solo tests: reglas de módulos y de datos con Konsist (`./gradlew :arquitectura:test`)

## Comandos
- Usar el JDK de Android Studio: `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`
- Tests del dominio: `./gradlew :dominio:test`
- Tests de Kiwi (puerta de ayuda por tipo de sesión, guardia, Writing): `./gradlew :tutor:test`
- APK de depuración: `./gradlew :app:assembleDebug`, que deja el archivo en `app/build/outputs/apk/debug/app-debug.apk`
- Todos los tests: `./gradlew test`
- Antes de cada PR: `./gradlew check` (formato con Spotless/ktlint, detekt, lint, tests y cobertura mínima de 70 % en `:dominio` y `:tutor` con Kover; informe con `./gradlew koverHtmlReport`). `./gradlew spotlessApply` corrige el formato; `./gradlew detektBaseline` regenera el baseline, solo si se acepta deuda a propósito
- APK firmado: `./gradlew :app:assembleRelease`. Necesita `keystore.properties` en la raíz (ver `app/build.gradle.kts`); sin él, sale sin firmar
- Traspasar el progreso de la web a una cuenta: `python herramientas/traspasar.py --usuario <usuario>` (solo muestra) y luego con `--subir`
- Tests en el dispositivo (Room, sincronización y migraciones), con el emulador abierto: `./gradlew :datos:connectedDebugAndroidTest`
- Reglas de Firestore con su emulador: `npm test` dentro de `pruebas-reglas/` (ver su README)
- Regenerar el contenido y los casos de referencia, desde la carpeta del proyecto web:
  `.venv/Scripts/python.exe -m app.cli exportar-paquete ../repaso-admisiones-android/app/src/main/assets/contenido --borradores`
  `.venv/Scripts/python.exe -m app.cli exportar-referencia ../repaso-admisiones-android/dominio/src/test/resources/referencia`
  Mientras todo el contenido esté en borrador, el paquete se genera con `--borradores`. El formato está en `docs/paquete-android.md` del proyecto web.

## Secretos (nunca en git)
- `app/google-services.json`, la configuración de Firebase.
- La clave de firma del APK (`*.jks`) y `keystore.properties`. Guárdala con copia: sin ella no se puede actualizar la app.
- La credencial del SDK de administración que usa `herramientas/cuentas.py` (`crear-admin`, `cambiar-clave`). Solo se guarda en el PC.

## Reglas inviolables
1. La respuesta correcta la calcula siempre el código: Python al exportar y `:dominio` al verificar. Nunca el LLM.
2. Kiwi nunca revela la respuesta. La guardia portada revisa cada texto antes de mostrarlo. Las pistas son ilimitadas y no restan puntos.
3. En pruebas de sección y examen final no hay tutor ni pistas. El bloqueo vive en `:dominio`: `ayudaPermitida(tipo)` es la única puerta, y `:tutor` la consulta antes de cualquier llamada. Hay un test para cada tipo de sesión.
4. Las materias nunca se mezclan, ni siquiera en los repasos.
5. Ninguna pregunta va hardcodeada: todo sale del paquete de contenido.
6. Reglas del curso:
   - nota sobre 10 y mínimo 7 para aprobar;
   - todas las secciones están abiertas y se empieza por cualquiera;
   - dentro de cada sección los temas van en orden;
   - la prueba de la sección se abre al terminar todos sus temas;
   - sin rachas y sin audio.
7. Toda la interfaz en español, con textos para 11–12 años: frases cortas y sin jerga. Los textos van en `strings.xml`.
8. Sin LM Studio ni Internet la app sigue funcionando. La práctica y las pruebas no dependen de la red.
9. Puntos por completar temas, nunca por velocidad. Tras responder, Kiwi solo aparece si el estudiante falló o en respuestas abiertas (Writing). Tras un fallo, la pista de la lista sale primero, sin esperar a Kiwi.
10. En un tema se permiten 3 errores. El 4.º lo corta sin puntos y hay que empezarlo de nuevo. Lo fallado se vuelve a preguntar al final del tema con la misma variante, por su semilla.

## Cuentas y datos
- Solo hay dos roles: `estudiante` y `admin`. No hay registro público.
- Se entra con usuario y clave. Por dentro, `ana` se convierte en `ana@preparate.invalid`.
- Toda consulta local filtra por `usuario_id`, el UID de Firebase, porque varios estudiantes pueden compartir un dispositivo.
- Los cursos nunca se mezclan. Los ids de temas y plantillas solo son únicos dentro de su curso, así que todo el progreso guarda también el `curso_id`.
- Las conversaciones con Kiwi no se guardan nunca, ni en Room ni en Firestore.
- Los datos son de menores: solo se guarda el nombre, sin apellidos.

## Convenciones
- Tipado completo. La lógica pura va en `:dominio`, sin dependencias de Android.
- Todo lo que se porta de Python se comprueba con los casos de referencia que genera el proyecto web (`exportar-referencia`).
- Cada intento guarda la semilla de su variante para poder reconstruirla.
- Se diseña primero para tablet y luego se adapta al celular con `WindowSizeClass`.
- La interfaz se inspira en Duolingo **sin** copiar su marca: mascota y paleta propias.
- Ramas `feature|fix|chore|docs|refactor/<issue>-<slug>`, Conventional Commits (tipo en inglés, descripción en español) y PR con squash. Detalle en `CONTRIBUTING.md`. `npm install` en la raíz activa los hooks de commit y push.
