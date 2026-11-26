# Política de seguridad

Esta app guarda datos de **menores de edad**. Cualquier fallo que exponga esos datos se trata como crítico.

## Cómo avisar de un problema

**No abras un issue público.** Usa el aviso privado de GitHub:
pestaña **Security** → **Report a vulnerability** en este repositorio.

Incluye qué encontraste, cómo reproducirlo y qué datos podrían quedar expuestos. Se responde en un plazo de 7 días.

Si encontraste un secreto publicado por error (por ejemplo, la configuración de Firebase o una credencial del SDK
de administración), avisa de inmediato: se revoca antes de cualquier otra cosa.

## Versiones con soporte

Solo la última versión instalada en los dispositivos de la familia. No hay versiones antiguas con soporte.

## Qué se protege

| Activo | Protección |
|---|---|
| Progreso y textos de Writing de cada estudiante | `firestore.rules` probadas con el emulador; consultas locales filtradas por `usuario_id` |
| Identidad de los estudiantes | Solo nombre, sin apellidos. Sin correos reales (`usuario@preparate.invalid`) |
| Conversaciones con Kiwi | No se guardan nunca, ni en el dispositivo ni en la nube |
| Credencial del SDK de administración | Solo en el PC, nunca en git ni en el APK |
| Clave de firma del APK | Fuera de git, con copia de seguridad |
| `google-services.json` | Fuera de git |

## Modelo de amenazas

Las amenazas de cada frontera, con su mitigación y su estado, están en
[`docs/security/threat-model.md`](docs/security/threat-model.md).

## Fuera de alcance (riesgos aceptados)

- El bloqueo de Kiwi en pruebas y el reloj del examen viven en el dispositivo y podrían saltarse con
  conocimientos técnicos. Las notas no son oficiales ([ADR-0008](docs/adr/0008-ayuda-permitida-unica-puerta.md)).
- El tráfico con LM Studio es HTTP sin cifrar dentro de la red local de la casa
  ([ADR-0007](docs/adr/0007-kiwi-con-lm-studio-local.md)).

## Reglas para quien contribuye

- Nunca subas secretos. Revisa `.gitignore` antes de añadir archivos de configuración. Gitleaks los busca antes
  del commit (si está instalado) y en cada PR (`.gitleaks.toml`, [ADR-0020](docs/adr/0020-escaneo-de-seguridad-en-ci.md)).
- No uses datos reales de estudiantes en tests, capturas ni issues.
- No registres (`Log`) datos personales, respuestas de Writing ni mensajes de Kiwi.
