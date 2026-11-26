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
| Checks requeridos | `build-y-tests`, `titulo-del-pr` y `reglas-firestore`, con la rama al día con `main` |
| Excepciones (bypass) | Ninguna |

**Estado (2026-11-26):** aplicado. El ruleset `main protegida` (id 28198031) está activo y un push directo a
`main` lo rechaza GitHub con `GH013` (#49).

## Paso 0: ¿se puede aplicar en este repo?

El repositorio es **público**, así que GitHub aplica los rulesets en el plan gratuito. Si algún día vuelve a
ser privado, hará falta **GitHub Pro** (o Team/Enterprise): en el plan gratuito la regla se guarda pero no se
cumple (aparece el aviso "Not enforced").

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

## Paso 2: ruleset de `main` (repo público o GitHub Pro)

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
   - **Require status checks to pass** y **Require branches to be up to date before merging**, con los checks
     `build-y-tests`, `titulo-del-pr` y `reglas-firestore`, de la fuente **GitHub Actions** (paso 3).
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
    },
    {
      "type": "required_status_checks",
      "parameters": {
        "strict_required_status_checks_policy": true,
        "do_not_enforce_on_create": false,
        "required_status_checks": [
          { "context": "build-y-tests", "integration_id": 15368 },
          { "context": "titulo-del-pr", "integration_id": 15368 },
          { "context": "reglas-firestore", "integration_id": 15368 }
        ]
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

Ya van en el JSON del paso 2 (regla `required_status_checks`). Cada check es el nombre de un job:

| Check | Workflow | Qué comprueba |
|---|---|---|
| `build-y-tests` | `ci.yml` | `./gradlew check` y el APK de depuración |
| `titulo-del-pr` | `pr-title.yml` | El título del PR con commitlint (es el mensaje del squash) |
| `reglas-firestore` | `reglas.yml` | `firestore.rules` con el emulador (solo prueba si el PR las cambia) |

`integration_id: 15368` es GitHub Actions: así otro integrador no puede marcar el check en verde.
`strict_required_status_checks_policy` obliga a tener la rama al día con `main` (botón *Update branch*).

Si cambia el nombre de un job, hay que cambiarlo también en el ruleset, o los PR se quedan esperando un check
que nunca llega. Para editar el ruleset existente:

```bash
gh api -X PUT repos/Jagr-ecu/repaso-admisiones-android/rulesets/28198031 --input ruleset-main.json
```

Los workflows de seguridad (Gitleaks, CodeQL, revisión de dependencias) también corren en cada PR, pero no son
requeridos: CodeQL tarda varios minutos y sus alertas se revisan en Security.

## Paso 4: etiquetas que usan las plantillas

Las plantillas de issue usan `bug`, `enhancement` y `tarea-tecnica`, y Dependabot usa `dependencies`.
Las que no existen se crean una vez:

```bash
gh label create tarea-tecnica --color 5319E7 --description "Mantenimiento, CI, dependencias o deuda técnica" --repo Jagr-ecu/repaso-admisiones-android
```

```bash
gh label create dependencies --color 0366D6 --description "Actualización de dependencias" --repo Jagr-ecu/repaso-admisiones-android
```

## Paso 5: seguridad del repositorio

**Settings → Advanced Security** (o *Code security*):

- [x] Dependency graph
- [x] Dependabot alerts
- [x] Dependabot security updates
- [x] Private vulnerability reporting (lo usa `SECURITY.md`)

Al ser público, el repo tiene además *secret scanning*, *push protection* y CodeQL gratis (fase 4, ADR-0020).

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

Debe fallar dos veces: primero el hook `pre-push` local y, si se salta con `--no-verify`, GitHub con
`GH013: Repository rule violations found` (comprobado el 2026-11-26).
Después, deshaz el commit de prueba:

```bash
git reset --hard origin/main
```
