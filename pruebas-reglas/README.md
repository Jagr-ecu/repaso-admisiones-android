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

## Publicar las reglas

Después de cambiar `firestore.rules` y ver las pruebas en verde, se copian en la consola de Firebase:
Firestore → Reglas → Publicar.
