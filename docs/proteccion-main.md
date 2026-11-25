# Protección de `main`

Objetivo: que todo cambio entre por PR con los checks en verde, sin aprobaciones de otra persona
(hay un solo desarrollador).

| Regla | Valor |
|---|---|
| PR obligatorio | Sí, con 0 aprobaciones |
| Conversaciones resueltas antes del merge | Sí |
| Método de merge | Solo *squash* (historial lineal) |
| Historial lineal | Sí |
| Force-push | Bloqueado |
| Borrar `main` | Bloqueado |
| Checks requeridos | Los de CI, cuando existan (fase 5) |
| Excepciones (bypass) | Ninguna |

## Paso 0: ¿se puede aplicar en este repo?

El repositorio es **privado**. GitHub solo aplica rulesets y protección de ramas en repos privados con
**GitHub Pro** (o Team/Enterprise). En el plan gratuito, la regla se guarda pero no se cumple
(aparece el aviso "Not enforced").

| Opción | Costo | Qué se consigue |
|---|---|---|
| **A. GitHub Pro** (recomendada si quieres simular un equipo real) | De pago, mensual | Todas las reglas, aplicadas por GitHub |
| B. Seguir en el plan gratuito | Gratis | Solo los respaldos locales de abajo y disciplina |
| C. Hacer público el repo | Gratis | **Descartada**: expone el contenido y la configuración del proyecto |

### Respaldos locales (ya incluidos, sirven en cualquier plan)

- `.husky/commit-msg`: commitlint rechaza mensajes que no siguen Conventional Commits.
- `.husky/pre-push`: bloquea el push directo a `main`.

Se activan con `npm install` en la raíz. Se pueden saltar con `--no-verify`, así que no sustituyen a la regla de GitHub.

## Paso 1: ajustes del repositorio

En **Settings → General → Pull Requests**:

- [x] Allow squash merging — *Default commit message:* **Pull request title**
- [ ] Allow merge commits
- [ ] Allow rebase merging
- [x] Always suggest updating pull request branches
- [x] Automatically delete head branches

Con `gh` (desde tu PC, con sesión iniciada en `gh auth login`):

```bash
gh api -X PATCH repos/Jagr-ecu/repaso-admisiones-android -F allow_squash_merge=true -F allow_merge_commit=false -F allow_rebase_merge=false -F delete_branch_on_merge=true -F allow_update_branch=true -f squash_merge_commit_title=PR_TITLE -f squash_merge_commit_message=PR_BODY
```

## Paso 2: ruleset de `main` (requiere la opción A)

### Por la interfaz

**Settings → Rules → Rulesets → New ruleset → New branch ruleset**:

1. *Ruleset name:* `main protegida` · *Enforcement status:* **Active**.
2. *Bypass list:* vacía.
3. *Target branches:* **Add target → Include default branch**.
4. Marcar:
   - **Restrict deletions**
   - **Require linear history**
   - **Require a pull request before merging**
     - *Required approvals:* **0**
     - **Require conversation resolution before merging**
     - *Allowed merge methods:* solo **Squash**
   - **Block force pushes**
   - **Require status checks to pass** — dejarlo para la fase 5, cuando existan los checks de CI.
5. **Create**.

### Con `gh`

Guarda esto como `ruleset-main.json` (fuera del repo o bórralo después):

```json
{
  "name": "main protegida",
  "target": "branch",
  "enforcement": "active",
  "bypass_actors": [],
  "conditions": {
    "ref_name": { "include": ["~DEFAULT_BRANCH"], "exclude": [] }
  },
  "rules": [
    { "type": "deletion" },
    { "type": "non_fast_forward" },
    { "type": "required_linear_history" },
    {
      "type": "pull_request",
      "parameters": {
        "required_approving_review_count": 0,
        "dismiss_stale_reviews_on_push": false,
        "require_code_owner_review": false,
        "require_last_push_approval": false,
        "required_review_thread_resolution": true,
        "allowed_merge_methods": ["squash"]
      }
    }
  ]
}
```

```bash
gh api -X POST repos/Jagr-ecu/repaso-admisiones-android/rulesets --input ruleset-main.json
```

Comprobar:

```bash
gh api repos/Jagr-ecu/repaso-admisiones-android/rulesets --jq '.[] | {id, name, enforcement}'
```

## Paso 3: checks requeridos (fase 5)

Cuando existan los workflows de CI, se añade al ruleset la regla `required_status_checks` con los nombres de
los jobs (por ejemplo, `build-y-tests` y `titulo-del-pr`) y **Require branches to be up to date before merging**.
Se documentará aquí con su comando.

## Paso 4: etiquetas que usan las plantillas

Las plantillas de issue usan `bug`, `enhancement` y `tarea-tecnica`, y Dependabot usa `dependencies`.
Las que no existen se crean una vez:

```bash
gh label create tarea-tecnica --color 5319E7 --description "Mantenimiento, CI, dependencias o deuda técnica" --repo Jagr-ecu/repaso-admisiones-android
```

```bash
gh label create dependencies --color 0366D6 --description "Actualización de dependencias" --repo Jagr-ecu/repaso-admisiones-android
```

## Paso 5: seguridad del repositorio (gratis también en privados)

**Settings → Advanced Security** (o *Code security*):

- [x] Dependency graph
- [x] Dependabot alerts
- [x] Dependabot security updates
- [x] Private vulnerability reporting (lo usa `SECURITY.md`)

Secret scanning y CodeQL en repos privados también requieren un plan de pago; se tratan en la fase 4.

## Cómo comprobar que funciona

```bash
git checkout main
```

```bash
git commit --allow-empty -m "test: comprobar la protección"
```

```bash
git push origin main
```

Debe fallar dos veces: primero el hook `pre-push` local y, si se salta con `--no-verify`, GitHub (solo con la opción A).
Después, deshaz el commit de prueba:

```bash
git reset --hard origin/main
```
