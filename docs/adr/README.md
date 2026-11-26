# Registro de decisiones (ADR)

Formato [MADR](https://adr.github.io/madr/). Para una decisión nueva, copia [`plantilla.md`](plantilla.md)
con el siguiente número y ábrela en un PR. Un ADR aceptado no se edita: si la decisión cambia, se escribe uno
nuevo y el anterior pasa a "reemplazado por".

| Nº | Decisión | Estado | Fecha |
|---|---|---|---|
| [0000](0000-registrar-decisiones-con-adr.md) | Registrar las decisiones con ADR en formato MADR | Aceptado | 2026-11-24 |
| [0001](0001-kotlin-y-compose-nativo.md) | App Android nativa con Kotlin y Jetpack Compose | Aceptado | 2026-11-06 |
| [0002](0002-room-fuente-de-verdad.md) | Room como fuente de verdad y Firestore como copia | Aceptado | 2026-11-06 |
| [0003](0003-firebase-plan-gratuito.md) | Firebase Auth y Firestore en el plan gratuito | Aceptado | 2026-11-06 |
| [0004](0004-sin-servicios-de-google.md) | Sin servicios de Google y APK instalado a mano | Aceptado | 2026-11-09 |
| [0005](0005-contenido-dentro-del-apk.md) | Contenido generado en Python dentro del APK | Aceptado | 2026-11-06 |
| [0006](0006-dominio-kotlin-puro.md) | `:dominio` en Kotlin puro con casos de referencia | Aceptado | 2026-11-06 |
| [0007](0007-kiwi-con-lm-studio-local.md) | Kiwi con LM Studio en el PC, sin RAG | Aceptado | 2026-11-06 |
| [0008](0008-ayuda-permitida-unica-puerta.md) | `ayudaPermitida(tipo)` como única puerta de la ayuda | Aceptado | 2026-11-09 |
| [0009](0009-conversaciones-sin-guardar.md) | Las conversaciones con Kiwi no se guardan | Aceptado | 2026-11-06 |
| [0010](0010-minsdk-29-targetsdk-36.md) | `minSdk` 29 y `targetSdk` 36 | Aceptado | 2026-11-09 |
| [0011](0011-cursos-aislados.md) | Todo va dentro de un curso (`curso_id`) | Aceptado | 2026-11-09 |
| [0012](0012-login-con-dominio-invalid.md) | Usuario y clave con `@preparate.invalid` | Aceptado | 2026-11-06 |
| [0013](0013-katex-en-webview.md) | Fórmulas con KaTeX en WebView | Aceptado | 2026-11-06 |
| [0014](0014-fsrs-portado.md) | FSRS portado de py-fsrs | Aceptado | 2026-11-09 |
| [0015](0015-formato-y-analisis-estatico.md) | Formato con Spotless y ktlint, y análisis estático con detekt | Aceptado | 2026-11-25 |
| [0016](0016-cobertura-con-kover.md) | Cobertura con Kover: mínimo de 70 % de líneas en `:dominio` y `:tutor` | Aceptado | 2026-11-25 |
| [0017](0017-tests-de-arquitectura-con-konsist.md) | Tests de arquitectura con Konsist en un módulo `:arquitectura` | Aceptado | 2026-11-25 |
