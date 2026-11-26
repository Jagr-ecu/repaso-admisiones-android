---
status: aceptado
date: 2026-11-25
decision-makers: Responsable del proyecto
---

# Escaneo de seguridad en CI: Gitleaks, CodeQL, revisión de dependencias y SBOM

## Contexto y problema

El repositorio es público y la app guarda datos de menores. Un secreto publicado por error (la credencial del
SDK de administración, la configuración de Firebase o la clave de firma) da acceso a esos datos o permite
publicar una "actualización" falsa. Una dependencia con una vulnerabilidad conocida llega al APK sin que nadie
lo note. Hasta ahora solo `.gitignore` y Dependabot protegían esto. El modelo de amenazas
(`docs/security/threat-model.md`) lo deja pendiente en la frontera 3 y en la sección de cadena de suministro.

## Factores de la decisión

* Sin costo: el repositorio es público, y CodeQL y la revisión de dependencias son gratis.
* Detectar antes del commit cuando se pueda, y siempre en el PR.
* Que una acción de terceros comprometida no pueda colarse.
* No exponer los secretos reales a los workflows.

## Opciones consideradas

* **Secretos:** Gitleaks (binario oficial o `gitleaks-action`), TruffleHog, o solo el *secret scanning* de GitHub.
* **Análisis de código:** CodeQL, o nada más que detekt.
* **Dependencias:** `actions/dependency-review-action`, o solo Dependabot.
* **Compilar en CI** (CodeQL lo necesita para Kotlin): un `google-services.json` de prueba generado en el
  workflow, o el real guardado en un secreto.
* **SBOM:** el plugin CycloneDX de Gradle, como artefacto del workflow ahora y adjunto al Release con
  release-please (fase 5), o esperar a la fase 5.
* **Hook local:** Gitleaks obligatorio, o solo si está instalado.

## Decisión

* **Gitleaks 8.30.1** con `.gitleaks.toml`, que suma las reglas por defecto y las de este proyecto:
  `google-services.json`, `keystore.properties`, `*.jks`/`*.keystore`, `herramientas/credencial*.json`,
  `cuentas-pruebas.local`, el JSON de una cuenta de servicio y `storePassword`/`keyPassword` con un valor.
  * En **CI** (`.github/workflows/security.yml`): revisa **todo el historial** en cada PR, en cada push a `main`
    y una vez por semana. Usa el binario oficial, comprobado con su SHA-256, en vez de `gitleaks-action`
    (que pide licencia en organizaciones y es otra dependencia).
  * En **local** (`.husky/pre-commit`): revisa lo que está en el stage **si Gitleaks está instalado**. Si no,
    avisa y deja seguir, porque el CI lo revisa igual.
* **CodeQL** para Kotlin/Java (compilando con Gradle) y JavaScript (`pruebas-reglas/` y `assets/web/`), más
  **`actions/dependency-review-action`**, que falla con vulnerabilidades altas o críticas. Para compilar, el
  workflow genera un **`google-services.json` de prueba** con datos falsos: no hace falta ningún secreto.
* **SBOM CycloneDX** de las dependencias de runtime de `:app`, subido como artefacto en cada push a `main`.
  Adjuntarlo al Release queda en el issue de release-please (fase 5).
* **Todas las acciones fijadas por SHA**, con la versión en un comentario. Dependabot las mantiene al día.

Gitleaks entra con el issue #39, CodeQL y la revisión de dependencias con el #40, y el SBOM con el #41.

### Consecuencias

* Bueno: un secreto no llega a `main` sin que falle un check, y se puede parar antes, en el commit.
* Bueno: la revisión del historial completo confirmó que la reescritura no dejó nada (0 hallazgos en 50
  commits, 2026-11-25).
* Bueno: los workflows no ven ningún secreto real.
* Malo: en local, el hook depende de tener Gitleaks instalado (`winget install Gitleaks.Gitleaks`).
* Malo: reglas como `storePassword=valor` pueden dar falsos positivos. Se ajustan en `.gitleaks.toml` con un PR,
  nunca con `--no-verify`.
* Neutro: los checks serán obligatorios en `main` cuando se configure el ruleset (fase 5).

### Confirmación

* `gitleaks git --config .gitleaks.toml --redact .` no encuentra nada en `main`.
* Un archivo con `storePassword=…` o el JSON de una cuenta de servicio en el stage hace fallar el hook y el job.
* Las alertas de CodeQL aparecen en Security → Code scanning (#40).
* Cada push a `main` deja el artefacto `sbom` con `sbom.cdx.json` (#41).

## Pros y contras de las opciones

### Gitleaks con binario verificado

* Bueno: rápido, con reglas configurables en TOML, el mismo en local y en CI.
* Malo: hay que actualizar a mano la versión y su checksum en el workflow.

### TruffleHog

* Bueno: puede verificar si una clave sigue activa.
* Malo: más pesado, y esa verificación hace llamadas a servicios externos con los secretos encontrados.

### Solo el *secret scanning* de GitHub

* Bueno: no hay que configurar nada.
* Malo: no conoce los secretos propios del proyecto (`keystore.properties`, `cuentas-pruebas.local`) y no se
  puede usar antes del commit.

### `google-services.json` real en un secreto

* Bueno: el build de CI es igual que el local.
* Malo: expone la configuración real a los workflows sin necesidad. Para compilar y analizar basta con datos falsos.

## Más información

* Issues #39, #40 y #41, épica #36.
