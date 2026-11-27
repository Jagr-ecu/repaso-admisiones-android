---
status: aceptado
date: 2026-11-26
decision-makers: Responsable del proyecto
---

# Release con release-please y APK firmado en un Environment con aprobación

## Contexto y problema

Hasta ahora el APK firmado se compilaba en el PC con `./gradlew :app:assembleRelease` y `keystore.properties`.
La versión (`versionName` y `versionCode`) se cambiaba a mano y no había CHANGELOG (issue #48, épica #43).
Eso tiene tres riesgos:

* Olvidar subir el `versionCode`: el APK no se instala encima del anterior.
* No saber qué cambió en cada versión ni qué dependencias lleva.
* Que la clave de firma quede expuesta en CI. Quien la tenga puede publicar una "actualización" falsa
  (modelo de amenazas, F4-T).

## Factores de la decisión

* Un solo desarrollador: publicar tiene que ser un clic, pero con una aprobación explícita.
* Los commits ya siguen Conventional Commits (commitlint y `titulo-del-pr`), así que la versión puede salir de ellos.
* La clave de firma no debe llegar a ningún PR ni a ningún workflow que no sea el de release.
* El ruleset de `main` exige checks en todos los PR, también en el de release.

## Opciones consideradas

* **Versión:** a mano en `app/build.gradle.kts`; desde la etiqueta de git; **release-please** con `version.txt`.
* **Firma:** en el PC; **en CI con secretos de un Environment con aprobación**; Play App Signing (descartada: la
  MatePad no tiene Google Play).
* **Token del PR de release:** `GITHUB_TOKEN`; **token personal *fine-grained***; GitHub App.

## Decisión

* **release-please** (`release-type: simple`) mantiene `version.txt`, `CHANGELOG.md` y un PR
  `chore(release): versión X.Y.Z`. Al mezclarlo crea la etiqueta `vX.Y.Z` y el Release.
  * `app/build.gradle.kts` lee `version.txt`: `versionName` es la versión y `versionCode` sale de ella
    (`1.2.3` → `10203`). Así siempre crece, y menor y parche deben ser menores que 100.
  * La primera versión es **0.9.0** (`release-as` en `release-please-config.json`). **Después del primer
    release hay que quitar `release-as`**, o todas las versiones seguirían siendo 0.9.0. La 1.0.0 sale al cerrar
    el MVP v1.0 (con un commit con el pie `Release-As: 1.0.0`).
  * Antes de la 1.0.0, `feat` sube el número menor y `fix` el parche. Solo `feat`, `fix` y `perf` abren
    un release. `content` sale en el CHANGELOG, pero por sí solo no abre un release.
* **Firma en CI** con el job `apk` de `release.yml`, que usa el Environment **`produccion`**:
  * Solo se despliega desde `main` y necesita la aprobación del responsable.
  * Sus secretos son `FIRMA_KEYSTORE_BASE64`, `FIRMA_STORE_PASSWORD`, `FIRMA_KEY_ALIAS`, `FIRMA_KEY_PASSWORD` y
    `GOOGLE_SERVICES_JSON_BASE64`. Se suben con `scripts/gestion/secretos-release.sh`, que nunca los muestra.
  * `FIRMA_SHA256` (variable o secreto del Environment, es pública) guarda la huella del certificado. Es
    obligatoria: el job falla si falta o si el APK está firmado con otra clave.
  * Gradle va sin caché en ese job, para que nada de otra ejecución entre en el APK.
  * Al terminar, el job adjunta al Release `preparate-vX.Y.Z.apk`, su `.sha256` y el SBOM, y borra la clave del
    runner.
* **Token:** el secreto de repositorio `RELEASE_PLEASE_TOKEN` es un token *fine-grained* que solo tiene acceso a este
  repo, con *Contents* y *Pull requests* de lectura y escritura. Hace falta porque un PR abierto con `GITHUB_TOKEN`
  no dispara workflows, y entonces el PR de release se quedaría esperando los checks requeridos. Sin el secreto,
  el workflow usa `GITHUB_TOKEN` y hay que disparar los checks a mano.

## Consecuencias

* Bien: publicar es mezclar el PR de release y aprobar el despliegue. El `versionCode` ya no se olvida, y cada
  versión tiene CHANGELOG, SHA-256 y SBOM (cierra F4-T y el pendiente de A5 del modelo de amenazas).
* Bien: un PR no puede leer la clave. Los secretos son del Environment y este solo acepta `main`.
* Mal: hay un token personal que renovar cuando caduque (como mucho un año). Si caduca, el PR de release sigue
  saliendo, pero sin checks.
* Mal: el Release se publica antes de que esté el APK. El APK llega cuando apruebas el despliegue, unos minutos
  después.
* La clave sigue teniendo su copia fuera de GitHub (issue #6). Si se pierde, no hay forma de actualizar la app
  instalada.

## Cómo publicar

1. Mezclar el PR `chore(release): versión X.Y.Z` cuando el CHANGELOG esté bien.
2. En Actions → Release, aprobar el despliegue a `produccion` (*Review deployments*).
3. Descargar `preparate-vX.Y.Z.apk` del Release, comprobar el SHA-256 e instalarlo en la MatePad.
