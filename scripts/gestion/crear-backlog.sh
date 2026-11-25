#!/usr/bin/env bash
# Crea etiquetas, milestones, épicas e issues del backlog, ordena los issues existentes
# y añade todo al Project con Status, Priority, Size y Área.
# Se puede ejecutar varias veces: no duplica nada (busca los issues por título exacto) y en los
# issues que ya están en el tablero solo rellena los campos vacíos.
#
#   bash scripts/gestion/crear-backlog.sh            # crea
#   DRY_RUN=1 bash scripts/gestion/crear-backlog.sh  # solo muestra
#
# Requiere haber ejecutado antes scripts/gestion/crear-proyecto.sh.
source "$(dirname "$0")/comun.sh"

MVP="MVP v1.0"
V11="v1.1 Calidad y CI"

comprobar_requisitos
numero="$(numero_proyecto)"
[[ -n "$numero" ]] || error "No existe el Project \"$PROYECTO_TITULO\". Ejecuta antes crear-proyecto.sh."
pid="$(id_proyecto "$numero")"
cargar_campos "$numero"
for campo in Status Priority Size "Área"; do
  [[ -n "${CAMPO_ID[$campo]:-}" ]] || error "Falta el campo $campo en el Project. Ejecuta crear-proyecto.sh."
done

# ── 1. Etiquetas ────────────────────────────────────────────────────────────────
info "Etiquetas…"
etiqueta() { escribir gh label create "$1" --repo "$REPO" --color "$2" --description "$3" --force >/dev/null; }
etiqueta "epica"         "3E4B9E" "Agrupa varios issues de un mismo objetivo"
etiqueta "tarea-tecnica" "5319E7" "Mantenimiento, CI, dependencias o deuda técnica"
etiqueta "calidad"       "0E8A16" "Formato, análisis estático, cobertura y tests de arquitectura"
etiqueta "seguridad"     "B60205" "Seguridad y privacidad de datos de menores"
etiqueta "ci-cd"         "1D76DB" "GitHub Actions, releases y distribución del APK"
etiqueta "dependencies"  "0366D6" "Actualización de dependencias"

# ── 2. Milestones ───────────────────────────────────────────────────────────────
info "Milestones…"
milestone() {
  local titulo="$1" descripcion="$2"
  if gh api "repos/$REPO/milestones?state=all&per_page=100" --jq '.[].title' | grep -Fxq "$titulo"; then
    info "El milestone \"$titulo\" ya existe."
  else
    escribir gh api -X POST "repos/$REPO/milestones" -f title="$titulo" -f description="$descripcion" >/dev/null
  fi
}
milestone "$MVP" "Los estudiantes usan la app una semana real sin perder progreso (fase 7)."
milestone "$V11" "Calidad, seguridad, CI/CD, runbook y documentación viva (fases 3 a 8 adaptadas a Android)."

# ── 3. Utilidades de issues ─────────────────────────────────────────────────────
# Número de un issue por título exacto (vacío si no existe).
buscar_issue() {
  gh issue list --repo "$REPO" --state all --limit 500 --json number,title \
    --jq ".[] | select(.title==\"$1\") | .number" | head -n1
}

# Issues que ya están en el tablero: su elemento y el valor actual de cada campo.
# Solo se rellenan los campos vacíos, para no pisar lo que se movió a mano.
# (gh pone los nombres de campo en minúscula; Área se busca por el final para no depender de la tilde.)
declare -A ITEM_ID=()
declare -A VALOR=()
cargar_tablero() {
  local n id estado prioridad tamano area
  ITEM_ID=(); VALOR=()
  while IFS=$'\t' read -r n id estado prioridad tamano area; do
    [[ -z "$n" ]] && continue
    ITEM_ID["$n"]="$id"
    VALOR["$n|Status"]="$estado"; VALOR["$n|Priority"]="$prioridad"
    VALOR["$n|Size"]="$tamano";   VALOR["$n|Área"]="$area"
  done < <(gh project item-list "$numero" --owner "$DUENO" --limit 1000 --format json --jq '
    .items[] | select(.content.type=="Issue")
    | [.content.number, .id, (.status // ""), (.priority // ""), (.size // ""),
       ((to_entries | map(select(.key | endswith("rea"))) | .[0].value) // "")] | @tsv')
}
cargar_tablero

# Id del elemento de un issue en el tablero. "Auto-add to project" puede tardar unos segundos en añadirlo.
buscar_item() {
  local n="$1" intento item
  for intento in 1 2 3 4 5; do
    item="$(gh project item-list "$numero" --owner "$DUENO" --limit 1000 --format json \
      --jq ".items[] | select(.content.type==\"Issue\" and .content.number==$n) | .id")"
    [[ -n "$item" ]] && { printf '%s\n' "$item"; return 0; }
    sleep 2
  done
  return 1
}

# Añade un issue al Project (si no está) y rellena sus campos vacíos. Args: número, status, prioridad, tamaño, área
al_tablero() {
  local n="$1" estado="$2" prioridad="$3" tamano="$4" area="$5" item
  item="${ITEM_ID[$n]:-}"
  if [[ -z "$item" ]]; then
    # Si Auto-add ya lo añadió, item-add falla con "Content already exists": se busca el elemento.
    item="$(escribir gh project item-add "$numero" --owner "$DUENO" \
      --url "https://github.com/$REPO/issues/$n" --format json --jq '.id' 2>/dev/null)" \
      || item="$(buscar_item "$n")" \
      || { aviso "#$n: no se pudo añadir al tablero."; return 0; }
    [[ "$item" == "SIMULADO" ]] && return 0
    ITEM_ID["$n"]="$item"
  fi
  fijar() {
    local campo="$1" valor="$2" actual oid
    [[ -z "$valor" ]] && return 0
    actual="${VALOR["$n|$campo"]:-}"
    [[ -n "$actual" ]] && return 0
    oid="${OPCION_ID["$campo|$valor"]:-}"
    if [[ -z "$oid" ]]; then aviso "#$n: no existe la opción \"$valor\" en $campo."; return 0; fi
    info "#$n: $campo → $valor"
    escribir gh project item-edit --id "$item" --project-id "$pid" \
      --field-id "${CAMPO_ID[$campo]}" --single-select-option-id "$oid" >/dev/null
    VALOR["$n|$campo"]="$valor"
  }
  fijar Status "$estado"; fijar Priority "$prioridad"; fijar Size "$tamano"; fijar "Área" "$area"
}

# Engancha un issue como sub-issue de su épica (si la API no está disponible, solo avisa).
como_sub_issue() {
  local epica="$1" hijo="$2" id_hijo
  [[ -z "$epica" || "$epica" == "SIMULADO" || "$hijo" == "SIMULADO" ]] && return 0
  id_hijo="$(gh api "repos/$REPO/issues/$hijo" --jq '.id')"
  if gh api "repos/$REPO/issues/$epica/sub_issues" --jq '.[].number' 2>/dev/null | grep -Fxq "$hijo"; then
    return 0
  fi
  escribir gh api -X POST "repos/$REPO/issues/$epica/sub_issues" -F sub_issue_id="$id_hijo" >/dev/null 2>&1 \
    || aviso "No se pudo enganchar #$hijo a la épica #$epica (sub-issues). Queda enlazado en el texto."
}

declare -A EPICA=()
# Crea un issue si no existe. Args: clave, título, etiquetas, milestone, área, prioridad, tamaño, clave-épica
# El cuerpo se lee de stdin.
nuevo() {
  local clave="$1" titulo="$2" etiquetas="$3" hito="$4" area="$5" prioridad="$6" tamano="$7" padre="${8:-}"
  local cuerpo n url
  cuerpo="$(cat)"
  if [[ -n "$padre" ]]; then
    cuerpo="$cuerpo"$'\n\n'"Épica: #${EPICA[$padre]}"
  fi
  n="$(buscar_issue "$titulo")"
  if [[ -n "$n" ]]; then
    info "Ya existe #$n: $titulo"
  else
    info "Creando: $titulo"
    url="$(escribir gh issue create --repo "$REPO" --title "$titulo" --label "$etiquetas" \
      --milestone "$hito" --body "$cuerpo")"
    n="${url##*/}"
  fi
  [[ "$etiquetas" == *epica* ]] && EPICA["$clave"]="$n"
  al_tablero "$n" "Backlog" "$prioridad" "$tamano" "$area"
  [[ -n "$padre" ]] && como_sub_issue "${EPICA[$padre]}" "$n"
  return 0
}

# ── 4. Issues existentes ────────────────────────────────────────────────────────
info "Ordenando los issues existentes…"
# número | milestone | status | prioridad | tamaño | área
while IFS='|' read -r -u 3 n hito estado prioridad tamano area; do
  [[ -z "$n" || "$n" == \#* ]] && continue
  if [[ -n "$hito" ]]; then
    escribir gh issue edit "$n" --repo "$REPO" --milestone "$hito" >/dev/null
  fi
  al_tablero "$n" "$estado" "$prioridad" "$tamano" "$area"
done 3<<EOF
# MVP: pruebas reales y uso real (fases 5, 6 y 7)
1|$MVP|Backlog|P1|S|tutor
2|$MVP|Backlog|P1|XS|seguridad
3|$MVP|Backlog|P0|M|sincro
6|$MVP|Backlog|P0|XS|seguridad
7|$MVP|Backlog|P0|XS|sincro
10|$MVP|Backlog|P1|XS|sincro
11|$MVP|Backlog|P0|L|app
# Ya cerrados: al tablero como hechos
4||Done|||contenido
5|$MVP|Done|||contenido
8|$MVP|Done|||sincro
9|$MVP|Done|||app
20||Done|||docs
22||Done|||docs
30||Done|||docs
# Después del MVP (sin milestone)
13||Backlog|P2|M|sincro
14||Backlog|P2|XL|contenido
15||Backlog|P2|L|sincro
16||Backlog|P2|M|contenido
17||Backlog|P2|M|contenido
18||Backlog|P2|M|datos
19||Backlog|P2|M|app
EOF

# Las etiquetas de fase se reemplazan por milestone + Área (borrar la etiqueta la quita de todos los issues).
for etiqueta_fase in fase-5 fase-6 fase-7; do
  if gh label list --repo "$REPO" --limit 200 --json name --jq '.[].name' | grep -Fxq "$etiqueta_fase"; then
    info "Quitando la etiqueta $etiqueta_fase…"
    escribir gh label delete "$etiqueta_fase" --repo "$REPO" --yes >/dev/null
  fi
done

# ── 5. Épicas e issues nuevos (v1.1 Calidad y CI) ───────────────────────────────
info "Épicas e issues nuevos…"

nuevo E-CAL "Épica: calidad del código (formato, análisis estático, cobertura y arquitectura)" \
  "epica,calidad" "$V11" "infra/CI" P1 L <<'EOF'
## Objetivo
Que el código mantenga su calidad sin depender de la memoria: formato automático, análisis estático,
cobertura mínima y reglas de arquitectura comprobadas por tests (fase 3 adaptada a Android).

## Hecho cuando
- Los issues de esta épica están cerrados.
- `./gradlew check` corre formato, análisis estático, tests y cobertura.
- Hay un ADR por cada herramienta elegida.
EOF

nuevo CAL-1 "Formato y análisis estático con Spotless, ktlint y detekt" \
  "tarea-tecnica,calidad" "$V11" "infra/CI" P1 S E-CAL <<'EOF'
## Contexto
No hay formato automático ni análisis estático. `.editorconfig` ya define el estilo.

## Alcance
- Spotless con ktlint para `*.kt` y `*.kts`, leyendo `.editorconfig`.
- detekt con una configuración base y un baseline para no bloquear por lo existente.
- Tareas incluidas en `./gradlew check`.
- ADR con la decisión.

## Criterios de aceptación
```gherkin
Escenario: formato roto
  Dado un archivo Kotlin mal formateado
  Cuando ejecuto ./gradlew spotlessCheck
  Entonces falla y ./gradlew spotlessApply lo corrige

Escenario: código existente
  Cuando ejecuto ./gradlew detekt en main
  Entonces pasa gracias al baseline
```
EOF

nuevo CAL-2 "Cobertura con Kover: mínimo 70 % en dominio y tutor" \
  "tarea-tecnica,calidad" "$V11" "infra/CI" P1 S E-CAL <<'EOF'
## Contexto
La lógica crítica (reglas, verificador, guardia, puerta de ayuda) está en `:dominio` y `:tutor`, en la JVM.

## Alcance
- Kover en `:dominio` y `:tutor`, informe HTML y XML.
- Regla `koverVerify` con un mínimo configurable (70 % de líneas por defecto).
- ADR con la decisión.

## Criterios de aceptación
```gherkin
Escenario: cobertura suficiente
  Cuando ejecuto ./gradlew koverVerify
  Entonces pasa si la cobertura de líneas es de al menos 70 %

Escenario: cobertura insuficiente
  Dado que el mínimo es mayor que la cobertura real
  Cuando ejecuto ./gradlew koverVerify
  Entonces falla e indica el porcentaje
```
EOF

nuevo CAL-3 "Tests de arquitectura con Konsist" \
  "tarea-tecnica,calidad" "$V11" "dominio" P2 M E-CAL <<'EOF'
## Contexto
Las reglas de módulos solo están escritas en `CLAUDE.md` y `docs/arquitectura.md`.

## Alcance
Tests con Konsist (o equivalente) que comprueben:
- `:dominio` y `:tutor` no importan `android.*` ni `androidx.*`.
- Ningún módulo depende de `:app`.
- Las consultas de los DAO de Room incluyen `usuario_id`.
- Ninguna clase guarda mensajes de Kiwi en Room ni en Firestore.

## Criterios de aceptación
```gherkin
Escenario: dependencia prohibida
  Dado que añado un import de android.content en :dominio
  Cuando ejecuto los tests de arquitectura
  Entonces fallan e indican el archivo
```
EOF

nuevo E-SEG "Épica: seguridad shift-left y privacidad de menores" \
  "epica,seguridad" "$V11" "seguridad" P1 L <<'EOF'
## Objetivo
Detectar fallos de seguridad antes del merge y documentar las amenazas (fase 4 adaptada a Android y Firebase).

## Hecho cuando
- Los issues de esta épica están cerrados.
- `docs/security/threat-model.md` existe y cada amenaza alta tiene mitigación o riesgo aceptado.
- Ningún PR se mezcla con secretos o vulnerabilidades altas o críticas.
EOF

nuevo SEG-1 "Modelo de amenazas STRIDE" \
  "seguridad,documentation" "$V11" "seguridad" P1 M E-SEG <<'EOF'
## Alcance
`docs/security/threat-model.md` con STRIDE sobre las fronteras de `docs/arquitectura.md`:
app ↔ Firestore, app ↔ LM Studio, scripts del PC ↔ Firebase y estudiante ↔ su dispositivo.
Cada amenaza con impacto, probabilidad, mitigación existente y pendiente (issue enlazado).

## Criterios de aceptación
- Cubre las 6 categorías STRIDE en cada frontera.
- Los riesgos aceptados enlazan su ADR.
EOF

nuevo SEG-2 "HTTP sin cifrar solo hacia LM Studio" \
  "seguridad" "$V11" "seguridad" P1 S E-SEG <<'EOF'
## Contexto
`network_security_config.xml` permite HTTP en toda la app (`base-config`), porque la IP del PC la elige el admin.

## Alcance
Prohibir HTTP hacia los dominios de Firebase y Google (`domain-config` con `cleartextTrafficPermitted="false"`)
o restringirlo a la red local. Documentar la decisión en un ADR.

## Criterios de aceptación
```gherkin
Escenario: Firebase solo por HTTPS
  Cuando la app intenta una petición http:// a un dominio de Firebase
  Entonces Android la bloquea

Escenario: Kiwi sigue funcionando
  Dado LM Studio en http://192.168.x.x:1234
  Cuando el estudiante pide una pista
  Entonces Kiwi responde
```
EOF

nuevo SEG-3 "Escaneo de secretos con Gitleaks en CI y antes del commit" \
  "seguridad,ci-cd" "$V11" "seguridad" P1 S E-SEG <<'EOF'
## Alcance
- Job de Gitleaks en `security.yml` sobre cada PR y `main`.
- Hook `pre-commit` (husky) que ejecuta Gitleaks si está instalado.
- Reglas extra: `google-services.json`, claves de cuentas de servicio y `keystore.properties`.

## Criterios de aceptación
```gherkin
Escenario: secreto en un PR
  Dado un PR que añade una clave privada de cuenta de servicio
  Cuando corre security.yml
  Entonces el job falla
```
EOF

nuevo SEG-4 "CodeQL y revisión de dependencias en los PR" \
  "seguridad,ci-cd" "$V11" "seguridad" P1 S E-SEG <<'EOF'
## Contexto
CodeQL y dependency review son gratis en repos públicos.

## Alcance
- CodeQL para Kotlin/Java (build con Gradle) y JavaScript (`pruebas-reglas/`).
- `actions/dependency-review-action` que falla con vulnerabilidades altas o críticas.
- Acciones fijadas por SHA.

## Criterios de aceptación
- Las alertas aparecen en Security → Code scanning.
- Un PR que añade una dependencia con una vulnerabilidad crítica no pasa el check.
EOF

nuevo SEG-5 "SBOM CycloneDX en cada release" \
  "seguridad,ci-cd" "$V11" "seguridad" P2 S E-SEG <<'EOF'
## Alcance
Plugin CycloneDX de Gradle que genera el SBOM de `:app` y lo adjunta al GitHub Release junto al APK.

## Criterios de aceptación
- Cada release incluye `sbom.cdx.json` con las dependencias de runtime del APK.
EOF

nuevo SEG-6 "Ningún dato personal en los registros (Log)" \
  "seguridad,calidad" "$V11" "seguridad" P2 S E-SEG <<'EOF'
## Alcance
- Regla de detekt (o test) que prohíbe `Log.*` / `println` con nombres, respuestas de Writing o mensajes de Kiwi.
- Revisión de los `Log` actuales.

## Criterios de aceptación
```gherkin
Escenario: registro con datos personales
  Dado un Log.d que imprime el texto de un Writing
  Cuando corre el análisis estático
  Entonces falla
```
EOF

nuevo E-CI "Épica: CI/CD con GitHub Actions y release del APK firmado" \
  "epica,ci-cd" "$V11" "infra/CI" P0 XL <<'EOF'
## Objetivo
Que cada PR se compruebe solo y que publicar una versión sea un clic con aprobación
(fase 5; las fases 6 y 7 de contenedores y despliegue se reemplazan por la distribución del APK).

## Hecho cuando
- Los issues de esta épica están cerrados.
- `main` exige los checks de CI (ruleset).
- Un release genera el APK firmado, su SHA-256, el SBOM y el CHANGELOG.
EOF

nuevo CI-1 "Workflow ci.yml: tests, lint, cobertura y APK de depuración" \
  "ci-cd,tarea-tecnica" "$V11" "infra/CI" P0 M E-CI <<'EOF'
## Alcance
- `ci.yml` en cada PR y en `main`: `./gradlew check` (tests JVM, formato, detekt, Kover) y `:app:assembleDebug`.
- JDK 21, caché de Gradle, `concurrency` que cancela ejecuciones viejas, `permissions: contents: read`.
- `google-services.json` desde un secreto (base64) o uno de prueba con el proyecto `demo-preparate`.
- Acciones fijadas por SHA.

## Criterios de aceptación
```gherkin
Escenario: PR con un test roto
  Cuando abro el PR
  Entonces el check build-y-tests falla

Escenario: dos pushes seguidos
  Cuando hago push dos veces al mismo PR
  Entonces la primera ejecución se cancela
```
EOF

nuevo CI-2 "Workflow pr-title.yml: título del PR con Conventional Commits" \
  "ci-cd" "$V11" "infra/CI" P1 XS E-CI <<'EOF'
## Alcance
Validar el título del PR con los mismos tipos y ámbitos de `commitlint.config.mjs` (es el mensaje del squash).

## Criterios de aceptación
- `Arreglos varios` falla; `fix(sincro): no subir la vista previa` pasa.
EOF

nuevo CI-3 "Pruebas de firestore.rules en CI" \
  "ci-cd,seguridad" "$V11" "infra/CI" P1 S E-CI <<'EOF'
## Alcance
Job que ejecuta `npm test` en `pruebas-reglas/` con el emulador de Firebase cuando cambian `firestore.rules`
o `pruebas-reglas/`.

## Criterios de aceptación
```gherkin
Escenario: regla demasiado abierta
  Dado un PR que permite a un estudiante leer a otro
  Cuando corre el job
  Entonces falla
```
EOF

nuevo CI-4 "Tests en emulador Android en CI (Room, sincronización y migraciones)" \
  "ci-cd,tarea-tecnica" "$V11" "datos" P2 M E-CI <<'EOF'
## Alcance
Job con un emulador (API 29 y 36) que ejecuta `./gradlew :datos:connectedDebugAndroidTest`.
Puede correr solo en `main` o con una etiqueta si es lento.

## Criterios de aceptación
- Las migraciones de Room y el verificador con el motor ICU se prueban en CI.
EOF

nuevo CI-5 "Release con release-please y APK firmado" \
  "ci-cd" "$V11" "infra/CI" P1 L E-CI <<'EOF'
## Alcance
- release-please: versión semántica desde los commits, `CHANGELOG.md`, `versionName` y `versionCode`.
- Al publicar el release: `:app:assembleRelease` firmado con la clave en secretos del Environment
  `produccion`, con aprobación manual; adjunta el APK, su SHA-256 y el SBOM.
- ADR con la decisión y actualización de `CONTRIBUTING.md`.

## Criterios de aceptación
```gherkin
Escenario: nueva versión
  Dado que se mezcló un feat en main
  Cuando se mezcla el PR de release-please
  Entonces se crea la etiqueta vX.Y.0 y el release espera aprobación
  Y al aprobarlo se adjunta el APK firmado con su SHA-256

Escenario: clave protegida
  Cuando un PR intenta leer los secretos de produccion
  Entonces no tiene acceso
```
EOF

nuevo CI-6 "Ruleset de main con checks requeridos" \
  "ci-cd,tarea-tecnica" "$V11" "infra/CI" P1 XS E-CI <<'EOF'
## Alcance
Aplicar el paso 2 y 3 de `docs/proteccion-main.md` con los checks de CI-1, CI-2 y SEG-3/SEG-4 como requeridos.
Necesita el repo público o GitHub Pro.

## Criterios de aceptación
- Un PR con un check en rojo no se puede mezclar.
- Un push directo a `main` es rechazado por GitHub.
EOF

nuevo CI-7 "Automatización del tablero: PR abierto pasa a In review" \
  "ci-cd" "$V11" "infra/CI" P2 S E-CI <<'EOF'
## Contexto
Las automatizaciones integradas del Project no mueven el issue al abrir un PR.

## Alcance
Workflow que, al abrir un PR con `Closes #N`, pone el issue N en `In review` (y `In progress` al crear la rama,
si es viable). Usa un token con permiso de proyectos guardado como secreto.

## Criterios de aceptación
- Al abrir un PR con `Closes #N`, el issue N aparece en In review.
EOF

nuevo E-OPS "Épica: operación y documentación viva" \
  "epica,documentation" "$V11" "docs" P1 M <<'EOF'
## Objetivo
Saber qué hacer cuando algo falla y mantener la documentación al día sin esfuerzo (fases 7 y 8 adaptadas:
sin servidores, la operación es instalar, actualizar y recuperar el progreso).

## Hecho cuando
- Los issues de esta épica están cerrados.
EOF

nuevo OPS-1 "Runbook de operación" \
  "documentation" "$V11" "docs" P1 M E-OPS <<'EOF'
## Alcance
`docs/runbook.md` con procedimientos paso a paso:
- instalar y actualizar el APK (versionCode, desinstalar el de depuración);
- recuperar el progreso de un estudiante desde Firestore en otro dispositivo;
- clave olvidada (`cuentas.py cambiar-clave`) y clave de firma perdida;
- cuota diaria de Firebase agotada;
- Kiwi no responde (IP del PC, LM Studio, red local);
- volver a una versión anterior del APK y qué pasa con las migraciones de Room;
- revocar la credencial del SDK de administración si se expone.

## Criterios de aceptación
- Cada procedimiento indica síntomas, pasos y cómo comprobar que se resolvió.
EOF

nuevo OPS-2 "CHANGELOG generado e insignias de estado en el README" \
  "documentation,ci-cd" "$V11" "docs" P2 S E-OPS <<'EOF'
## Alcance
- `CHANGELOG.md` generado por release-please (CI-5).
- README con insignias de CI, cobertura y última versión.
- Revisar que `docs/arquitectura.md` y los ADR reflejan las decisiones de las fases 3 a 5.

## Criterios de aceptación
- El README muestra el estado de CI de `main`.
EOF

info "Listo. Tablero: https://github.com/users/$DUENO/projects/$numero"
