#!/usr/bin/env bash
# Sube a GitHub los secretos que necesita el release firmado (release.yml, ADR-0022), sin mostrarlos nunca:
# cada valor pasa por una tubería directa a `gh secret set`.
#
#   Environment "produccion":
#     FIRMA_KEYSTORE_BASE64        la clave (*.jks) que indica storeFile, en base64
#     FIRMA_STORE_PASSWORD         storePassword de keystore.properties
#     FIRMA_KEY_ALIAS              keyAlias
#     FIRMA_KEY_PASSWORD           keyPassword
#     GOOGLE_SERVICES_JSON_BASE64  app/google-services.json, en base64
#   Variable (no secreta) del Environment:
#     FIRMA_SHA256                 huella del certificado; release.yml la compara con la del APK
#
# Uso, desde la raíz del repo y con keystore.properties y app/google-services.json en su sitio:
#   bash scripts/gestion/secretos-release.sh            (sube)
#   DRY_RUN=1 bash scripts/gestion/secretos-release.sh  (solo comprueba los archivos)
# Se puede repetir: cada ejecución reemplaza los valores anteriores.
source "$(dirname "$0")/comun.sh"

ENTORNO="produccion"
PROPIEDADES="keystore.properties"
FIREBASE="app/google-services.json"

if ! command -v gh >/dev/null && [[ -x "/c/Program Files/GitHub CLI/gh.exe" ]]; then
  gh() { "/c/Program Files/GitHub CLI/gh.exe" "$@"; }
fi
command -v gh >/dev/null || declare -F gh >/dev/null || error "Falta gh (https://cli.github.com)."
gh auth status >/dev/null 2>&1 || error "Inicia sesión: gh auth login"
[[ -f "$PROPIEDADES" ]] || error "No encuentro $PROPIEDADES en $(pwd). Ejecuta el script desde la raíz del repo."
[[ -f "$FIREBASE" ]] || error "No encuentro $FIREBASE."

# Lee una propiedad de keystore.properties sin el salto de línea (ni el \r de Windows).
propiedad() {
  local valor
  valor=$(grep -E "^$1=" "$PROPIEDADES" | head -1 | cut -d= -f2- | tr -d '\r\n')
  [[ -n "$valor" ]] || error "Falta '$1' en $PROPIEDADES."
  printf '%s' "$valor"
}

CLAVE=$(propiedad storeFile)
[[ -f "$CLAVE" ]] || error "No encuentro la clave que indica storeFile."
propiedad storePassword >/dev/null
propiedad keyAlias >/dev/null
propiedad keyPassword >/dev/null

gh api "repos/$REPO/environments/$ENTORNO" >/dev/null 2>&1 \
  || error "No existe el Environment '$ENTORNO' en $REPO (Settings → Environments)."

# keytool: el del PATH, el de JAVA_HOME o el JDK de Android Studio. La contraseña va por variable de entorno.
KEYTOOL=$(command -v keytool || true)
for jdk in "${JAVA_HOME:-}" "/c/Program Files/Android/Android Studio/jbr"; do
  [[ -z "$KEYTOOL" && -n "$jdk" && -x "$jdk/bin/keytool" ]] && KEYTOOL="$jdk/bin/keytool"
done
HUELLA=""
if [[ -n "$KEYTOOL" ]]; then
  HUELLA=$(FIRMA_STOREPASS="$(propiedad storePassword)" "$KEYTOOL" -list -v -keystore "$CLAVE" \
    -alias "$(propiedad keyAlias)" -storepass:env FIRMA_STOREPASS 2>/dev/null \
    | grep -m1 'SHA256:' | sed 's/.*SHA256: *//' | tr -d '\r') || true
fi

if [[ "$DRY_RUN" == "1" ]]; then
  info "Archivos y propiedades correctos. No se subió nada (DRY_RUN=1)."
  info "Huella del certificado (es pública): ${HUELLA:-no se pudo leer}"
  exit 0
fi

subir() { gh secret set "$1" --env "$ENTORNO" --repo "$REPO" >/dev/null && info "Secreto $1 subido."; }

base64 -w0 "$CLAVE" | subir FIRMA_KEYSTORE_BASE64
propiedad storePassword | subir FIRMA_STORE_PASSWORD
propiedad keyAlias | subir FIRMA_KEY_ALIAS
propiedad keyPassword | subir FIRMA_KEY_PASSWORD
base64 -w0 "$FIREBASE" | subir GOOGLE_SERVICES_JSON_BASE64

if [[ -n "$HUELLA" ]]; then
  gh variable set FIRMA_SHA256 --env "$ENTORNO" --repo "$REPO" --body "$HUELLA" >/dev/null
  info "Variable FIRMA_SHA256 guardada: $HUELLA"
else
  aviso "No pude leer la huella con keytool. release.yml firmará igual, pero sin comparar el certificado."
fi

info "Listo. Comprueba en Settings → Environments → $ENTORNO que aparecen 5 secretos y 1 variable."
