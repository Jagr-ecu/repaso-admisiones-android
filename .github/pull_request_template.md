<!-- Título: Conventional Commits, p. ej. "feat(dominio): regla de 3 errores". Será el mensaje del squash. -->

## Qué cambia y por qué

Closes #

## Cómo probarlo

- [ ] `./gradlew test`
- [ ] Otro:

## Checklist

### Tests
- [ ] Todo cambio de comportamiento tiene tests (lógica pura en `:dominio`)
- [ ] Lo portado de Python coincide con los casos de referencia
- [ ] Tests en el dispositivo si toca Room o la sincronización (`:datos:connectedDebugAndroidTest`)

### Seguridad y datos de menores
- [ ] Sin secretos, credenciales ni datos reales de estudiantes
- [ ] Las consultas locales filtran por `usuario_id` y `curso_id`
- [ ] No se guarda nada de las conversaciones con Kiwi
- [ ] Si cambia `firestore.rules`, pasan las pruebas de `pruebas-reglas/`

### Reglas del proyecto
- [ ] Kiwi no revela respuestas y no aparece en pruebas ni exámenes (`ayudaPermitida`)
- [ ] La app sigue funcionando sin Internet ni LM Studio
- [ ] Los textos nuevos están en `strings.xml`, en español sencillo

### Documentación
- [ ] Documentación al día (README, `docs/`, `CLAUDE.md`)
- [ ] ADR nuevo o actualizado si cambia una decisión

### Compatibilidad
- [ ] Sin breaking changes
- [ ] Hay breaking changes (descríbelos abajo y usa `!` en el título)
- [ ] Si cambia el esquema de Room: migración con su test
- [ ] Si toca pantallas: probado en tablet y celular

## Breaking changes / notas para el despliegue

<!-- Migraciones, reglas de Firestore que hay que publicar, versionCode, etc. Borra si no aplica. -->
