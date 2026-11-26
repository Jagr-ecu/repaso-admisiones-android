---
status: aceptado
date: 2026-11-25
decision-makers: Responsable del proyecto
---

# HTTP sin cifrar solo hacia la red de la casa

## Contexto y problema

Kiwi habla con LM Studio, en el PC de la casa, por `http://`: LM Studio no ofrece HTTPS
([ADR-0007](0007-kiwi-con-lm-studio-local.md)). Para que funcione, `network_security_config.xml` permitía HTTP
sin cifrar **hacia cualquier destino** (`base-config cleartextTrafficPermitted="true"`). Con eso, una petición
`http://` a Firebase o a cualquier servidor de Internet también pasaba, y la app no comprobaba que la dirección
escrita por el admin fuera de verdad de la casa.

Lo ideal sería permitir HTTP solo para "192.168.x.x", pero el archivo de Android **solo acepta dominios o IPs
exactas, no rangos**. Y la IP del PC no se conoce al compilar: la escribe el admin en los ajustes y puede cambiar.

## Factores de la decisión

* Kiwi debe seguir funcionando con cualquier IP del PC, sin recompilar.
* Firebase siempre por HTTPS, aunque una librería o un cambio futuro pida `http://`.
* El chat de un menor no debe salir de la casa sin cifrar.
* Probarlo de forma automática: en la JVM lo que se pueda y en el dispositivo la política de Android.

## Opciones consideradas

* **A.** HTTP permitido por defecto, prohibido hacia Firebase y Google, y `ClienteKiwi` solo acepta direcciones
  de la red local.
* **B.** HTTP prohibido en toda la app, salvo para la IP del PC fijada al compilar.
* **C.** Un proxy HTTPS delante de LM Studio, con un certificado propio instalado en la tablet.
* **D.** Dejarlo como estaba.

## Decisión

Opción elegida: **"A"**, con dos capas.

1. **Android (`app/src/main/res/xml/network_security_config.xml`):** `base-config` permite HTTP, y un
   `domain-config` lo prohíbe, con subdominios, hacia `googleapis.com`, `firebaseio.com`, `firebaseapp.com`,
   `firebase.com`, `google.com` y `gstatic.com`. Android bloquea esas peticiones, también las del SDK de
   Firebase.
2. **Código (`ClienteKiwi.esDeRedLocal`, en `:tutor`):** solo acepta direcciones de la red local: privadas
   (10/8, 172.16/12, 192.168/16), el propio dispositivo (127/8, `::1`, `localhost`), de enlace local
   (169.254/16, `fe80::/10`), locales de IPv6 (`fc00::/7`), nombres `.local`, `.lan` o `.home.arpa`, y nombres
   sin punto. No consulta el DNS: un nombre de Internet que apunte a una IP privada no cuenta como local. Si la
   dirección no es local, los ajustes lo dicen y no la guardan. Además, `ClienteKiwi` ya no sigue
   redirecciones.

### Consecuencias

* Bueno: Firebase solo por HTTPS, comprobado en el dispositivo (`RedSeguraTest`).
* Bueno: el chat no puede ir a un servidor de Internet por un error al escribir la dirección. Comprobado en la
  JVM (`ClienteKiwiTest`, con una tabla de direcciones aceptadas y rechazadas).
* Bueno: Kiwi sigue funcionando con cualquier IP de la casa, sin recompilar.
* Malo: un dominio de Internet que no esté en la lista podría usarse por HTTP. Se acepta porque la app no
  habla con ningún otro servidor: solo con Firebase y LM Studio.
* Malo (riesgo aceptado): el tráfico entre la tablet y LM Studio va **sin cifrar dentro de la wifi de la
  casa**. Alguien conectado a esa wifi podría leer las preguntas a Kiwi y sus respuestas. Se acepta porque es
  la red de casa, el chat no lleva el nombre del estudiante y no se guarda
  ([ADR-0009](0009-conversaciones-sin-guardar.md)), y la opción C no compensa (ver abajo).
* Neutro: si alguna vez se instala en otra red (por ejemplo, la de un colegio), hay que revisar esta decisión.

### Confirmación

* `./gradlew :tutor:test`: `ClienteKiwiTest` comprueba direcciones locales y de Internet, que con una
  dirección de Internet no se llama a nadie y que no se siguen redirecciones.
* `./gradlew :app:connectedDebugAndroidTest`: `RedSeguraTest` comprueba que Android bloquea `http://` hacia
  Firestore, Auth y Google y lo permite hacia la red local.
* A mano en la MatePad: Kiwi responde con LM Studio en `http://192.168.x.x:1234`.

## Pros y contras de las opciones

### A. Lista de Firebase y Google, y comprobación en `ClienteKiwi`

* Bueno: cumple los dos objetivos sin cambiar cómo se instala o se configura la app.
* Malo: depende de que la lista de dominios esté completa para lo que usa la app.

### B. HTTP solo hacia una IP fijada al compilar

* Bueno: es lo más estricto, porque el HTTP solo va a un destino.
* Malo: si el router le da otra IP al PC, Kiwi deja de funcionar hasta instalar un APK nuevo.

### C. Proxy HTTPS con certificado propio

* Bueno: cifra también dentro de la casa.
* Malo: hay que instalar y mantener un proxy en el PC, crear una autoridad de certificación propia y confiar en
  ella dentro de la app o del dispositivo. Es mucho mantenimiento para una red de casa.

### D. Dejarlo como estaba

* Malo: cualquier destino por HTTP, sin ninguna comprobación.

## Más información

* Issue #38, épica #36. Modelo de amenazas: `docs/security/threat-model.md`, frontera 2.
