#!/usr/bin/env bash
# Crea (o completa) el GitHub Project "Prepárate" en la cuenta del dueño y lo enlaza al repo.
# Se puede ejecutar varias veces: lo que ya existe no se vuelve a crear.
#
#   bash scripts/gestion/crear-proyecto.sh            # crea
#   DRY_RUN=1 bash scripts/gestion/crear-proyecto.sh  # solo muestra
#
# Campos: Status, Priority, Size, Iteration (1 semana) y Área. Ver docs/gestion-del-trabajo.md.
source "$(dirname "$0")/comun.sh"

INICIO_ITERACIONES="${INICIO_ITERACIONES:-2026-11-30}"   # lunes
AREAS="dominio,datos,tutor,sincro,app,contenido,infra/CI,docs,seguridad"

comprobar_requisitos

# 1. Project
numero="$(numero_proyecto)"
if [[ -z "$numero" ]]; then
  info "Creando el Project \"$PROYECTO_TITULO\"…"
  numero="$(escribir gh project create --owner "$DUENO" --title "$PROYECTO_TITULO" --format json --jq '.number')"
else
  info "El Project \"$PROYECTO_TITULO\" ya existe (#$numero)."
fi
if [[ "$numero" == "SIMULADO" ]]; then
  info "Simulación: sin Project real no se pueden mostrar los pasos siguientes."
  exit 0
fi

escribir gh project edit "$numero" --owner "$DUENO" \
  --description "Tablero de Prepárate para Android: MVP, calidad y CI." >/dev/null
escribir gh project link "$numero" --owner "$DUENO" --repo "$REPO" >/dev/null 2>&1 \
  || info "El Project ya estaba enlazado al repo."

pid="$(id_proyecto "$numero")"
cargar_campos "$numero"

# 2. Status: cambia las opciones por defecto (Todo, In Progress, Done) por las del flujo.
estado_ok=1
for opcion in Backlog Ready "In progress" "In review" Done; do
  [[ -n "${OPCION_ID["Status|$opcion"]:-}" ]] || estado_ok=0
done
if (( estado_ok )); then
  info "Status ya tiene las opciones del flujo."
else
  info "Configurando las opciones de Status…"
  consulta='mutation($campo: ID!) {
    updateProjectV2Field(input: {fieldId: $campo, singleSelectOptions: [
      {name: "Backlog",     color: GRAY,   description: "Sin priorizar o sin empezar"},
      {name: "Ready",       color: BLUE,   description: "Listo para empezar: criterios claros"},
      {name: "In progress", color: YELLOW, description: "En una rama"},
      {name: "In review",   color: PURPLE, description: "Con PR abierto"},
      {name: "Done",        color: GREEN,  description: "Mezclado o cerrado"}
    ]}) { projectV2Field { ... on ProjectV2SingleSelectField { id } } }
  }'
  if ! escribir gh api graphql -f query="$consulta" -f campo="${CAMPO_ID[Status]}" >/dev/null; then
    aviso "No se pudo cambiar Status por la API. Hazlo a mano: Project → Settings → Status →"
    aviso "  opciones Backlog, Ready, In progress, In review, Done (en ese orden)."
  fi
fi

# 3. Campos de selección simple
crear_seleccion() {
  local nombre="$1" opciones="$2"
  if [[ -n "${CAMPO_ID[$nombre]:-}" ]]; then
    info "El campo $nombre ya existe."
  else
    info "Creando el campo $nombre ($opciones)…"
    escribir gh project field-create "$numero" --owner "$DUENO" --name "$nombre" \
      --data-type SINGLE_SELECT --single-select-options "$opciones" >/dev/null
  fi
}
crear_seleccion "Priority" "P0,P1,P2"
crear_seleccion "Size" "XS,S,M,L,XL"
crear_seleccion "Área" "$AREAS"

# 4. Iteration de 1 semana (gh project field-create no admite iteraciones: se usa GraphQL)
if [[ -n "${CAMPO_ID[Iteration]:-}" ]]; then
  info "El campo Iteration ya existe."
else
  info "Creando el campo Iteration (1 semana, desde $INICIO_ITERACIONES)…"
  consulta='mutation($proyecto: ID!, $inicio: Date!) {
    createProjectV2Field(input: {projectId: $proyecto, dataType: ITERATION, name: "Iteration",
      iterationConfiguration: {startDate: $inicio, duration: 7, iterations: []}}) {
      projectV2Field { ... on ProjectV2IterationField { id } }
    }
  }'
  if ! escribir gh api graphql -f query="$consulta" -f proyecto="$pid" -f inicio="$INICIO_ITERACIONES" >/dev/null; then
    aviso "No se pudo crear Iteration por la API. Hazlo a mano: Project → Settings → New field →"
    aviso "  Iteration, duración 1 semana, inicio $INICIO_ITERACIONES."
  fi
fi

info "Listo. Project #$numero: https://github.com/users/$DUENO/projects/$numero"
info "Siguiente: bash scripts/gestion/crear-backlog.sh"
