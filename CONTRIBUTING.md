# Cómo contribuir

Aunque hoy hay un solo desarrollador, el proyecto trabaja como un equipo: todo cambio pasa por un issue, una rama
corta y un PR con los checks en verde.

## Antes de empezar

1. Lee [`CLAUDE.md`](CLAUDE.md) (reglas inviolables) y [`docs/vision.md`](docs/vision.md).
2. Ejecuta `npm install` en la raíz una vez: instala el hook que revisa los mensajes de commit.
3. Consigue `app/google-services.json` (no está en git).

## Flujo de trabajo (trunk-based)

`main` siempre compila y está protegida: no se hace push directo ni force-push.

1. **Issue.** Todo empieza con un issue (bug, feature o tarea técnica) con criterios de aceptación, en el
   tablero del proyecto ([`docs/gestion-del-trabajo.md`](docs/gestion-del-trabajo.md)).
2. **Rama corta** desde `main` actualizada, con vida de días, no de semanas:

   | Prefijo | Para qué | Ejemplo |
   |---|---|---|
   | `feature/` | Función nueva | `feature/19-acceso-a-cursos` |
   | `fix/` | Corrección de un error | `fix/31-reloj-del-examen` |
   | `chore/` | Mantenimiento, dependencias, configuración | `chore/22-gobierno-repo` |
   | `docs/` | Solo documentación | `docs/40-runbook` |
   | `refactor/` | Cambio interno sin cambiar comportamiento | `refactor/45-separar-estudios` |

   Formato: `<prefijo>/<número de issue>-<slug-en-minúsculas>`.
3. **Commits pequeños** con Conventional Commits (abajo). Antes de abrir el PR, `./gradlew check` en verde
   (formato, detekt, lint, tests y cobertura mínima de 70 % en `:dominio` y `:tutor`). Si falla el formato, `./gradlew spotlessApply` lo corrige.
4. **PR** hacia `main` con la plantilla completa y `Closes #N`. Un PR resuelve una sola cosa.
5. **Merge** con *squash* cuando los checks están en verde y las conversaciones resueltas. El título del PR
   queda como mensaje del commit en `main`, así que también sigue Conventional Commits.
6. Se borra la rama.

## Mensajes de commit (Conventional Commits)

```text
<tipo>(<ámbito opcional>): <descripción en español, en minúscula y sin punto final>

<cuerpo opcional: qué y por qué>

<pie opcional: Closes #N, BREAKING CHANGE: ...>
```

El tipo va en inglés; la descripción y el cuerpo, en español. El hook `commit-msg` (commitlint) rechaza los
mensajes que no cumplen el formato.

| Tipo | Cuándo | Efecto en la versión |
|---|---|---|
| `feat` | Función nueva para el usuario | Sube MINOR |
| `fix` | Corrección de un error | Sube PATCH |
| `perf` | Mejora de rendimiento | Sube PATCH |
| `refactor` | Cambio interno sin cambiar comportamiento | — |
| `test` | Tests nuevos o corregidos | — |
| `docs` | Solo documentación | — |
| `build` | Gradle, dependencias, firma | — |
| `ci` | Workflows de GitHub Actions | — |
| `chore` | Mantenimiento que no encaja en lo anterior | — |
| `content` | Paquete de contenido o casos de referencia regenerados | Sube PATCH |
| `revert` | Deshace un commit anterior | — |

Ámbitos permitidos: `dominio`, `datos`, `tutor`, `sincro`, `app`, `contenido`, `reglas`, `herramientas`,
`deps`, `docs`, `ci`, `release`.

Ejemplos:

```text
feat(dominio): regla de 3 errores por tema
fix(sincro): no subir la vista previa de depuración
content: paquete con las revisiones de Inglés
build(deps): subir Room a 2.8.5
feat(sincro)!: nuevo formato de documentos en Firestore

BREAKING CHANGE: los documentos de la versión 1 ya no se leen.
```

## Versionado

[Versionado semántico](https://semver.org/lang/es/) en `versionName` (`app/build.gradle.kts`):

- **MAJOR**: cambio incompatible del progreso guardado o del formato en Firestore que obliga a migrar.
- **MINOR**: función nueva compatible.
- **PATCH**: correcciones y contenido revisado.

`versionCode` sube en **cada** APK que se instala encima de otro. Cada versión publicada lleva una etiqueta
`vMAJOR.MINOR.PATCH`. La automatización del versionado y del changelog llega con la fase 5 (CI/CD).

## Definition of Done

Un cambio está terminado cuando:

- [ ] Está vinculado a un issue y sus criterios de aceptación se cumplen.
- [ ] Todo cambio de comportamiento tiene tests. La lógica pura está en `:dominio`, con tests en la JVM.
- [ ] Lo portado de Python coincide con los casos de referencia.
- [ ] Si cambia el esquema de Room, hay una migración con su test.
- [ ] Si cambia `firestore.rules`, pasan las pruebas de `pruebas-reglas/`.
- [ ] Los checks del PR están en verde (cuando exista el CI) y no hay vulnerabilidades altas o críticas.
- [ ] No hay secretos, credenciales ni datos de estudiantes en el código, los tests ni los registros.
- [ ] Los textos nuevos están en `strings.xml`, en español sencillo para 11–12 años.
- [ ] La documentación está al día. Un cambio de decisión lleva un ADR en `docs/adr/`.
- [ ] Si toca pantallas, se probó en tablet y celular (y en la MatePad si afecta a Firebase o al WebView).

## Revisión de PR

Aunque no se exigen aprobaciones de otra persona, cada PR se revisa antes del merge:

- ¿Rompe alguna regla inviolable de `CLAUDE.md` (sobre todo 1, 2, 3 y 8)?
- ¿Toda consulta local filtra por `usuario_id` y `curso_id`?
- ¿Se guarda algo de las conversaciones con Kiwi? No debe.
- ¿El cambio es pequeño y enfocado?
