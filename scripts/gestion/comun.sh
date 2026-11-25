#!/usr/bin/env bash
# Funciones comunes de los scripts de gestión. Se cargan con `source`.
# Requisitos: gh >= 2.40 con sesión iniciada y el permiso de proyectos:
#   gh auth refresh -s project
# Bash >= 4 (Git Bash en Windows sirve).
set -euo pipefail

DUENO="${DUENO:-Jagr-ecu}"
REPO="${REPO:-Jagr-ecu/repaso-admisiones-android}"
# Título exacto del tablero en GitHub (Project 1). Si se renombra, cambiarlo aquí.
# (va aparte porque el apóstrofo no se puede escribir dentro de ${...:-...}).
TITULO_POR_DEFECTO="@Jagr-ecu's Preparate App"
PROYECTO_TITULO="${PROYECTO_TITULO:-$TITULO_POR_DEFECTO}"
DRY_RUN="${DRY_RUN:-0}"

info()  { printf '• %s\n' "$*" >&2; }
aviso() { printf '⚠ %s\n' "$*" >&2; }
error() { printf '✖ %s\n' "$*" >&2; exit 1; }

# Ejecuta un comando que CAMBIA algo en GitHub. Con DRY_RUN=1 solo lo muestra.
# Lo que el comando imprime por stdout se devuelve (para capturar ids o URLs).
escribir() {
  if [[ "$DRY_RUN" == "1" ]]; then
    printf '[simulado] %s\n' "$*" >&2
    printf 'SIMULADO\n'
    return 0
  fi
  "$@"
}

comprobar_requisitos() {
  command -v gh >/dev/null || error "Falta gh (https://cli.github.com)."
  (( BASH_VERSINFO[0] >= 4 )) || error "Hace falta Bash 4 o superior."
  gh auth status >/dev/null 2>&1 || error "Inicia sesión: gh auth login"
  if ! gh auth status 2>&1 | grep -q "'project'"; then
    aviso "El token podría no tener el permiso 'project'. Si algo falla: gh auth refresh -s project"
  fi
}

# Número del Project por su título (vacío si no existe).
numero_proyecto() {
  gh project list --owner "$DUENO" --limit 100 --format json \
    --jq ".projects[] | select(.title==\"$PROYECTO_TITULO\") | .number" | head -n1
}

id_proyecto() {
  gh project view "$1" --owner "$DUENO" --format json --jq '.id'
}

# Carga en los arreglos CAMPO_ID[nombre] y OPCION_ID[nombre|opción] los campos del Project.
declare -gA CAMPO_ID=()
declare -gA OPCION_ID=()
cargar_campos() {
  local numero="$1" nombre fid opcion oid
  CAMPO_ID=(); OPCION_ID=()
  while IFS=$'\t' read -r nombre fid; do
    [[ -n "$nombre" ]] && CAMPO_ID["$nombre"]="$fid"
  done < <(gh project field-list "$numero" --owner "$DUENO" --limit 100 --format json \
    --jq '.fields[] | [.name, .id] | @tsv')
  while IFS=$'\t' read -r nombre opcion oid; do
    [[ -n "$nombre" ]] && OPCION_ID["$nombre|$opcion"]="$oid"
  done < <(gh project field-list "$numero" --owner "$DUENO" --limit 100 --format json \
    --jq '.fields[] | select(.options != null) | .name as $c | .options[] | [$c, .name, .id] | @tsv')
}
