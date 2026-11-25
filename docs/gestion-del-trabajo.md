# Gestión del trabajo

El trabajo se organiza en el GitHub Project **"Prepárate"** de la cuenta `Jagr-ecu`, enlazado a este repo.
Todo issue abierto está en el tablero; todo PR cierra un issue con `Closes #N`.

## Crear el tablero y el backlog (una sola vez)

Necesitas `gh` 2.40 o superior y Bash 4 o superior (Git Bash en Windows sirve).

```bash
gh auth refresh -s project
```

Primero, una simulación que no cambia nada:

```bash
DRY_RUN=1 bash scripts/gestion/crear-proyecto.sh
```

```bash
bash scripts/gestion/crear-proyecto.sh
```

```bash
DRY_RUN=1 bash scripts/gestion/crear-backlog.sh
```

```bash
bash scripts/gestion/crear-backlog.sh
```

Los dos scripts se pueden repetir: no duplican el Project, los campos, las etiquetas, los milestones ni los
issues (los buscan por título exacto), y **no cambian los campos de un issue que ya está en el tablero**.

Si la API no deja configurar `Status` o crear `Iteration`, el script lo avisa y dice cómo hacerlo a mano.

## Campos

| Campo | Valores | Uso |
|---|---|---|
| **Status** | Backlog → Ready → In progress → In review → Done | Dónde está el trabajo |
| **Priority** | P0, P1, P2 | P0: bloquea el milestone actual. P1: importante para el milestone. P2: puede esperar |
| **Size** | XS, S, M, L, XL | XS: menos de 1 h. S: medio día. M: 1–2 días. L: una semana. XL: dividir en issues más pequeños |
| **Iteration** | 1 semana, desde el lunes 2026-11-30 | Qué se intenta terminar esta semana |
| **Área** | dominio, datos, tutor, sincro, app, contenido, infra/CI, docs, seguridad | Parte del proyecto afectada |

### Qué significa cada Status

| Status | Entra cuando… |
|---|---|
| Backlog | Existe el issue. Puede no tener criterios claros |
| Ready | Tiene criterios de aceptación, Priority y Size, y no está bloqueado |
| In progress | Hay una rama `tipo/<issue>-<slug>` con trabajo |
| In review | Hay un PR abierto con `Closes #N` |
| Done | El PR se mezcló o el issue se cerró |

**Límite de trabajo en curso:** como mucho 2 issues en `In progress` a la vez.

## Milestones

| Milestone | Objetivo |
|---|---|
| **MVP v1.0** | Los estudiantes usan la app una semana real sin perder progreso (fase 7 de `PLAN-MIGRACION.md`) |
| **v1.1 Calidad y CI** | Calidad, seguridad, CI/CD, runbook y documentación viva |

Las funciones para después del MVP (etiqueta `enhancement`) no tienen milestone hasta que se planifiquen.

## Épicas

Una épica es un issue con la etiqueta `epica` cuyos hijos son **sub-issues**. GitHub muestra el avance de la
épica según los hijos cerrados. Cada hijo termina con la línea `Épica: #N`.

| Épica | Contenido |
|---|---|
| Calidad del código | Spotless/ktlint/detekt, Kover, tests de arquitectura |
| Seguridad shift-left | STRIDE, HTTP solo hacia LM Studio, Gitleaks, CodeQL, SBOM, registros sin datos personales |
| CI/CD | `ci.yml`, `pr-title.yml`, reglas de Firestore en CI, emulador, release firmado, ruleset, tablero |
| Operación y documentación viva | Runbook, CHANGELOG e insignias |

## Etiquetas

| Etiqueta | Para qué |
|---|---|
| `bug` | Algo no funciona (plantilla "Error") |
| `enhancement` | Función nueva (plantilla "Función nueva") |
| `tarea-tecnica` | Mantenimiento o deuda técnica (plantilla "Tarea técnica") |
| `epica` | Agrupa issues |
| `calidad`, `seguridad`, `ci-cd`, `documentation`, `contenido` | Tema del issue |
| `dependencies` | PR de Dependabot |

Las fases ya no van en etiquetas: se usan el milestone y el campo Área.

## Automatizaciones

En el Project: **⋯ → Workflows**. Activar estas (vienen con GitHub):

| Workflow | Configuración |
|---|---|
| **Item added to project** | Status → **Backlog** |
| **Auto-add to project** | Filtro: `is:issue is:open` en `Jagr-ecu/repaso-admisiones-android` |
| **Item closed** | Status → **Done** |
| **Pull request merged** | Status → **Done** |
| **Item reopened** | Status → **Backlog** |
| **Auto-archive items** | `is:closed updated:<@today-14d` |

El paso **PR abierto → In review** no viene incluido. Se hará con un workflow en la fase 5
(issue "Automatización del tablero: PR abierto pasa a In review"). Mientras tanto, se mueve a mano.

## Vistas recomendadas

Crear en el Project (**+ New view**):

1. **Tablero** (Board), agrupado por Status, filtrado por la iteración actual: `iteration:@current`.
2. **Backlog** (Table), agrupado por Milestone, ordenado por Priority y luego Size.
3. **Hoja de ruta** (Roadmap), por Iteration.

## Ritmo semanal

- **Lunes (planificación, 15 min):** pasar a `Ready` y asignar a la iteración lo que cabe en la semana,
  empezando por P0. No pasar de unos 5 días de Size en total.
- **Durante la semana:** un issue → una rama → un PR. Moverlo de columna al empezar y al abrir el PR.
- **Viernes (revisión, 10 min):** lo no terminado vuelve a `Ready` o pasa a la iteración siguiente; anotar
  qué bloqueó.
