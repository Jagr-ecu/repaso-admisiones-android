# Pruebas de las reglas de Firestore

Prueban `../firestore.rules` con el emulador de Firestore, sin tocar el proyecto real
(`demo-preparate` es un proyecto de demostración que solo existe en el emulador).

Casos: un estudiante intenta leer a otro, cambiar su rol, borrar su progreso o escribir fuera de sus
colecciones; un admin intenta escribir progreso o crear otro admin; una cuenta externa (sin perfil
en `usuarios/`) o sin sesión intenta leer o escribir.

## Correr

Necesita Node 22 y Java 21 (el de Android Studio sirve).

```bash
cd pruebas-reglas
npm install
JAVA_HOME="C:\Program Files\Android\Android Studio\jbr" PATH="$JAVA_HOME/bin:$PATH" npm test
```

En CI las corre `.github/workflows/reglas.yml` (check `reglas-firestore`) en cada PR que cambia
`firestore.rules`, `firebase.json` o esta carpeta.

## Dependencias

`overrides` en `package.json` sube `@grpc/grpc-js`, `re2` y `uuid` a versiones sin las alertas conocidas
(dentro de la misma versión mayor que piden `firebase` y `firebase-tools`). Quedan alertas moderadas de `glob`,
`minimatch` y `@opentelemetry/core` dentro de `firebase-tools`: son de denegación de servicio, solo corren en el
PC y en CI con entradas nuestras, y arreglarlas exige versiones mayores que `firebase-tools` todavía no admite.
Se revisan cuando Dependabot proponga una versión nueva de `firebase-tools`.

## Publicar las reglas

Después de cambiar `firestore.rules` y ver las pruebas en verde, se copian en la consola de Firebase:
Firestore → Reglas → Publicar.
