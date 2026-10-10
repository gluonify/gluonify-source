# gluonify-source

[English](README.md) · [Français](README.fr.md) · **Español** · [Italiano](README.it.md) · [Deutsch](README.de.md)

> **Variante Gradle.** El mismo servicio, construido con **Gradle** (Kotlin DSL) en lugar de Maven, existe en [`gluonify-source-gradle`](https://github.com/gluonify/gluonify-source-gradle): mismo código, mismas pruebas, mismas reglas de plataforma.

**El punto de partida de un servicio Quarkus 4 en [Gluonify](https://gluonify.cloud).** Un pequeño servicio completo (unas «notas») que muestra, con código que funciona y pruebas, cómo:

1. crear un **servicio REST** (validación, roles, OpenAPI);
2. crear una **interfaz web** con [Quinoa](https://quarkus.io/extensions/io.quarkiverse.quinoa/quarkus-quinoa/) (Vue 3, servida por el mismo ejecutable);
3. **interactuar con los servicios de Gluonify**: configuración y secretos (Top), tokens de identidad (Charm), datos (Gdown) o archivos distribuidos, otras aplicaciones, webhooks recibidos de Photon, sitios estáticos (Field), dominios y políticas (Higgs);
4. **construirlo como ejecutable nativo** y **desplegarlo**.

Está pensado para un desarrollador o para **un asistente de IA** que empieza un servicio desde cero: cada decisión se explica aquí, cada línea de configuración está comentada en el código, y [`CLAUDE.md`](CLAUDE.md) resume las reglas para una IA.

> Gluonify ejecuta **aplicaciones Quarkus nativas** (un archivo ejecutable, sin JVM) en máquinas Debian. Usted da acceso a su código en Git; la plataforma lo construye, lo despliega, lo protege y lo mantiene en marcha.

## Índice
1. [Empezar en 5 minutos](#1-empezar-en-5-minutos)
2. [El mapa del proyecto](#2-el-mapa-del-proyecto)
3. [Renombrar el proyecto](#3-renombrar-el-proyecto)
4. [Hacer un servicio REST](#4-hacer-un-servicio-rest)
5. [Hacer una interfaz con Quinoa](#5-hacer-una-interfaz-con-quinoa)
6. [Autenticación: los tokens de Charm](#6-autenticación-los-tokens-de-charm)
7. [Interactuar con los servicios de Gluonify](#7-interactuar-con-los-servicios-de-gluonify)
8. [Construir](#8-construir)
9. [Desplegar](#9-desplegar)
10. [Probar](#10-probar)
11. [Trampas conocidas](#11-trampas-conocidas)
12. [Solución de problemas](#12-solución-de-problemas)

---

## 1. Empezar en 5 minutos

**Requisitos previos**: Java 25 (`JAVA_HOME`), Maven 3.9+. Node.js no es necesario: Quinoa descarga el suyo (v24.3.0) en la primera construcción. Docker solo es útil para el ejecutable nativo.

```bash
git clone https://github.com/gluonify/gluonify-source.git
cd gluonify-source
mvn quarkus:dev
```

Abra <http://localhost:8080>: la interfaz (añadir, listar, eliminar notas). También:

| Dirección | Contenido |
|---|---|
| `/` | la interfaz Vue |
| `/api/notes` | la API REST (JSON) |
| `/q/swagger-ui` | la documentación interactiva de la API |
| `/q/openapi` | el contrato OpenAPI (JSON o YAML) |
| `/q/health/ready` · `/q/health/live` | salud: «listo» y «vivo» (véase [§7](#7-interactuar-con-los-servicios-de-gluonify)) |
| `/q/metrics` | métricas Prometheus |

En **desarrollo** no se pide ningún token: una identidad «dev» (con todos los roles) existe solo en ese perfil (`@IfBuildProfile("dev")`, ausente del ejecutable de producción). Las notas están **en memoria** (`source.store=memory`): desaparecen al detener la aplicación.

```bash
curl -s localhost:8080/api/notes -H 'Content-Type: application/json' -d '{"title":"Courses","body":"du pain"}'
curl -s localhost:8080/api/notes
```

## 2. El mapa del proyecto

```
pom.xml                         proyecto Maven AUTÓNOMO (Quarkus 4.0.0.Beta1, Java 25): sin padre Gluonify
Dockerfile.build                construye el ejecutable nativo en Docker
src/main/resources/
  application.properties        TODA la configuración, comentada línea por línea
src/main/java/io/gluonify/source/
  SourceConfig.java             la configuración de la aplicación (prefijo «source.»)
  notes/
    Note.java, NewNote.java     el modelo (records) y el cuerpo de creación (validado)
    NotesResource.java          ← EL MODELO DE UN RECURSO REST (para copiar)
    NoteStore.java              la interfaz del almacenamiento; tres implementaciones:
    MemoryNoteStore.java          en memoria (desarrollo)
    FileNoteStore.java            archivos en /distributed/std (duradero, compartido por las réplicas)
    GraphNoteStore.java           Gdown, la base de datos de grafos de la plataforma
    NoteStores.java             elige la implementación (source.store): el lugar donde conectar la suya
    StoreHealth.java            la condición «listo» (/q/health/ready)
  platform/
    PlatformResource.java       variables proporcionadas por la plataforma; llamada a otra aplicación
    WebhookResource.java        recibir las entregas de Photon
  security/DevAuthentication.java   identidad de desarrollo (solo perfil dev)
src/main/webui/                 la interfaz Vue 3 (Vite + vitest), construida por Quinoa
src/test/java/…                 34 pruebas Java; src/main/webui/src/App.test.js: 15 pruebas de interfaz
deploy/                         ejemplos: AppSpec, dominio, webhook de Photon, base Gdown
scripts/rename.py               renombra el proyecto
```

## 3. Renombrar el proyecto

Su servicio no se llama `gluonify-source`. Un solo comando cambia las coordenadas Maven, el paquete Java (incluidas las carpetas), el nombre del ejecutable, el título OpenAPI y la **audiencia** esperada de los tokens:

```bash
python3 scripts/rename.py com.acme shop-api com.acme.shop
mvn test
```

(`groupId` `com.acme`, `artifactId` `shop-api`, paquete Java `com.acme.shop`. El script puede volver a ejecutarse sin riesgo.) El nombre del artefacto es también el nombre de la aplicación en Gluonify; elíjalo como un nombre DNS: minúsculas, dígitos, guiones.

## 4. Hacer un servicio REST

Lea [`NotesResource.java`](src/main/java/io/gluonify/source/notes/NotesResource.java): es el modelo. Para añadir **su** recurso, por ejemplo pedidos:

1. **El modelo**: un `record` Java (inmutable, Jackson lo lee y lo escribe, OpenAPI lo describe).
   ```java
   public record Order(String id, String customer, int quantity) {}
   public record NewOrder(@NotBlank String customer, @Min(1) int quantity) {}   // validado
   ```
2. **El recurso**: una clase con `@Path`, métodos `@GET`/`@POST`…, los roles con `@RolesAllowed`, la validación con `@Valid`.
   ```java
   @Path("/api/orders")
   @Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
   public class OrdersResource {
       @POST @RolesAllowed("shop:write")
       public Response create(@Valid NewOrder in) { … return Response.created(uri).entity(order).build(); }
   }
   ```
3. **La documentación**: `@Operation(summary = "…")` y `@Tag(name = "…")` (microprofile-openapi): aparecen en `/q/swagger-ui`. El contrato se regenera en cada arranque.
4. **Las pruebas**: copie [`NotesResourceTest`](src/test/java/io/gluonify/source/NotesResourceTest.java). `@TestSecurity(user = "ada", roles = {"shop:write"})` fija la identidad de una prueba; sin anotación, es una llamada sin token (esperado: 401).
5. **Los códigos HTTP**: 201 + `Location` al crear, 204 sin cuerpo, 400 para un cuerpo no válido (Hibernate Validator), 401 sin token, 403 sin el rol adecuado, 404 para un identificador desconocido. Los errores devuelven `{"error": "…"}`.

**El almacenamiento** está detrás de la interfaz [`NoteStore`](src/main/java/io/gluonify/source/notes/NoteStore.java): el recurso no sabe dónde viven los datos. Para su propio almacenamiento (otra base de datos, otra carpeta), implemente la interfaz y añada un caso en [`NoteStores`](src/main/java/io/gluonify/source/notes/NoteStores.java).

## 5. Hacer una interfaz con Quinoa

[Quinoa](https://quarkus.io/extensions/io.quarkiverse.quinoa/quarkus-quinoa/) construye un proyecto web (aquí Vue 3 + Vite, en `src/main/webui`) y lo sirve **desde el mismo ejecutable** que la API: un solo archivo que desplegar, un solo origen (sin CORS).

- **Desarrollar**: `mvn quarkus:dev` lanza también el servidor Vite (puerto 5173, recarga en caliente de la interfaz); Quarkus sirve la API. Abra <http://localhost:8080>.
- **Llamar a la API**: direcciones relativas (`fetch('/api/notes')`), véase [`src/api.js`](src/main/webui/src/api.js). El token de Charm lo pega el usuario y se conserva durante la pestaña (`sessionStorage`).
- **Las rutas de la interfaz**: `quarkus.quinoa.enable-spa-routing=true` devuelve `index.html` para una dirección desconocida (útil con vue-router). Pero **una dirección de API desconocida no debe devolver la interfaz**: `quarkus.quinoa.ignored-path-prefixes=/api,/hooks,/q` (ya configurado). Si añade un prefijo de API, añádalo aquí.
- **Construir**: `mvn package` ejecuta `npm install` y luego `npm run build`; `dist/` se incluye en el ejecutable. Sin interfaz: `-Dquarkus.quinoa=false`.
- **Idiomas (i18n)**: la interfaz está disponible en inglés (por defecto), francés, español, italiano y alemán. Ningún texto está escrito en los componentes: cada texto es una clave de [`src/i18n/`](src/main/webui/src/i18n) (`en.js`, `fr.js`, `es.js`, `it.js`, `de.js`) que se lee con `t('clave', { parámetro })` (una pequeña utilidad en `index.js`, sin dependencias). El selector de la cabecera recuerda la elección (`localStorage`); sin elección se usa el idioma del navegador si es uno de los cinco y, si no, el inglés; `<html lang>` se ajusta. Para añadir un idioma: copie `en.js` como `<código>.js` y traduzca los valores (mismas claves); después impórtelo en `index.js` (`MESSAGES` y `LANGUAGES`). Los mensajes de error devueltos por la API se muestran tal como llegan; los de los códigos 401 y 403 están traducidos.
- **Probar la interfaz**: `cd src/main/webui && npm install && npm test` (vitest + jsdom, `fetch` simulado: véase `App.test.js`). Con `quarkus.quinoa.run-tests=true`, `mvn package` también las ejecuta.
- **Caché**: los archivos de `dist/assets` llevan una huella en su nombre (inmutables); `index.html` se sirve con `Cache-Control: no-cache` (ajuste `ui-entry`).

Otra tecnología (React, Svelte…): sustituya el contenido de `src/main/webui`; Quinoa solo pide un `package.json` con los scripts `dev` y `build` y una carpeta de salida (`quarkus.quinoa.build-dir`).

## 6. Autenticación: los tokens de Charm

La API está **cerrada sin token**. Espera un token portador (JWT) emitido por **gluonify-charm**, el proveedor de identidad de Gluonify (OpenID Connect, ES256):

```
Authorization: Bearer <token>
```

El servicio nunca **crea** tokens: los **verifica** (modo `quarkus.oidc.application-type=service`). Lo que controla:

| Control | Ajuste | Valor |
|---|---|---|
| Firma | claves de Charm, leídas en `${GLUONIFY_SERVICE_CHARM_URL}/realms/gluonify` | `GLUONIFY_SERVICE_CHARM_URL` la proporciona la plataforma si la aplicación se despliega con `"uses": ["charm"]` |
| Emisor (`iss`) | `quarkus.oidc.token.issuer` | `GLUONIFY_ZZZNONE_OIDC_ISSUER` = `https://id.<su zona>/realms/gluonify` (estable: no es la dirección de la instancia de Charm) |
| Audiencia (`aud`) | `quarkus.oidc.token.audience` | `GLUONIFY_ZZZNONE_OIDC_AUDIENCE`, por defecto el nombre del proyecto (`gluonify-source`) |
| Caducidad | automática | tokens de corta duración |
| Roles | claim `roles` | `source:read` (leer), `source:write` (escribir): véase `@RolesAllowed`; el prefijo `gluonify:` está reservado a la plataforma (Charm lo rechaza para los roles de una aplicación) |

**Obtener un token de prueba** (el administrador de la plataforma, con el token de administración de Charm):

```bash
curl -s -X POST "$CHARM_URL/v1/tokens" -H "Authorization: Bearer $GLUONIFY_CHARM_ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"sub":"ada","audience":"gluonify-source","ttlSeconds":3600,"roles":["source:read","source:write"]}'
# -> {"token": "eyJ…"}
curl -s https://gluonify-source.<zone>/api/notes -H "Authorization: Bearer eyJ…"
```

Un valor de audiencia distinto (`"audience": "other"`) da **401**; un token válido sin el rol adecuado da **403**.

**En local**, el perfil `dev` proporciona la identidad «dev» (véase [`DevAuthentication`](src/main/java/io/gluonify/source/security/DevAuthentication.java)); **en las pruebas**, `@TestSecurity`. Nunca una identidad de este tipo en producción: solo se compila en el perfil `dev`.

## 7. Interactuar con los servicios de Gluonify

Una aplicación en Gluonify está **aislada**: su propia cuenta, su propia red, un sistema de archivos reducido. Solo alcanza lo que **declara** (`"uses"`) y recibe su configuración **por el entorno**. Aquí está cada servicio, con el lugar en el código.

| Servicio | Qué ofrece | Cómo lo usa la aplicación | En este repositorio |
|---|---|---|---|
| **Top** (caja fuerte) | configuración y secretos por aplicación | con `"top": true`, las claves del espacio `<uuid>.app` propio de su aplicación llegan como `APP_<CLAVE>`; `${app.gluonify.gdown.password}` en `application.properties` | `source.graph.password`, `source.webhook.key` |
| **Charm** (identidad) | tokens JWT de corta duración | `"uses": ["charm"]` → `GLUONIFY_SERVICE_CHARM_URL`; `quarkus-oidc` verifica los tokens | [§6](#6-autenticación-los-tokens-de-charm) |
| **Gdown** (base de grafos) | base replicada (Raft), Cypher por HTTP | `"uses": ["gdown"]` → `GLUONIFY_SERVICE_GDOWN_URL`; una cuenta local de Gdown | [`GraphNoteStore`](src/main/java/io/gluonify/source/notes/GraphNoteStore.java) |
| **Archivos distribuidos** | `/distributed/std`: duradero, compartido por todas las réplicas, 2 copias | `"distributed": ["std"]` en el despliegue | [`FileNoteStore`](src/main/java/io/gluonify/source/notes/FileNoteStore.java) |
| **Otras aplicaciones** | llamada de servicio a servicio | `"uses": ["other"]` → `SERVICE_OTHER_URL` | [`PlatformResource.ping`](src/main/java/io/gluonify/source/platform/PlatformResource.java) |
| **Photon** (pasarela de API) | webhooks firmados, conversión XML / SOAP / formulario → JSON, reenvío | Photon entrega en `…/hooks/events`; su servicio confirma con 2XX | [`WebhookResource`](src/main/java/io/gluonify/source/platform/WebhookResource.java) |
| **Field** | sitios estáticos (ZIP) servidos en su dominio | nada que programar: un sitio se sube en ZIP | — |
| **Higgs** (orquestador) | dominios, certificados HTTPS automáticos, rutas, políticas (token, límites, CORS, direcciones) | `PUT /domains/<nombre>` | `deploy/domain.json` |

### Configuración y secretos (Top)

- Un valor **no secreto**: en `"env"` del despliegue (`deploy/appspec.json`): `"SOURCE_STORE": "files"`. Se almacena en el estado del clúster.
- Un valor **secreto** (contraseña, clave de API): en la **caja fuerte** (Top, espacio `<uuid>.app` de su aplicación). La clave `GLUONIFY_GDOWN_PASSWORD` llega como variable `APP_GLUONIFY_GDOWN_PASSWORD` y se lee `${app.gluonify.gdown.password}`. Solicite ese espacio con `"top": true` en la especificación de la aplicación: solo ese espacio `<uuid>.app`, asignado por el plano de control, añade el prefijo `APP_`. Con un `"topNamespace"` literal (por ejemplo `"gluonify-source"`, la única opción del builder), las claves llegan **sin prefijo**, tal como se nombran en la caja fuerte (`GLUONIFY_GDOWN_PASSWORD` sigue siendo `GLUONIFY_GDOWN_PASSWORD`): nómbrelas según las propiedades leídas (por ejemplo `SOURCE_GRAPH_PASSWORD`). **Nunca** en el repositorio: el builder rechaza una contraseña en claro (regla R-SECRET), y `ConformityTest` se lo indica antes.
- **Guardar un valor no reinicia nada**: tras modificar varias claves, lance **un** redespliegue (`POST /apps/<nombre>/redeploy`); las réplicas se reinician una a una, sin corte si tiene dos o más.
- Variables que la plataforma añade **siempre**: `QUARKUS_HTTP_PORT` y `QUARKUS_HTTP_HOST`, `GLUONIFY_ENVIRONMENT` (el entorno: SBX, QUA, PRD…), `GLUONIFY_REPLICA` (número de la réplica: 1, 2…), `GLUONIFY_SELF_URL` (dirección de esta instancia). Las lee y muestra [`PlatformResource`](src/main/java/io/gluonify/source/platform/PlatformResource.java) (`GET /api/platform`, nunca un secreto).

### Salud: `/q/health/ready` y `/q/health/live`

La plataforma solo envía tráfico a las instancias cuyo **`/q/health/ready`** responde 200, y reinicia las cuyo **`/q/health/live`** falla de forma repetida. `quarkus-smallrye-health` es **obligatorio** (el builder lo controla: regla R-SANTE). [`StoreHealth`](src/main/java/io/gluonify/source/notes/StoreHealth.java) declara la instancia «lista» solo si el almacenamiento responde. Mantenga «vivo» independiente de los servicios externos: una caída de Gdown no debe hacer reiniciar la aplicación en bucle.

### Datos en Gdown (`source.store=gdown`)

**Lo más simple: una base dedicada.** Despliegue con `"gdownDatabase": true` y `SOURCE_STORE=gdown`: la plataforma crea una base propia para la aplicación (su propio grupo Raft, una cuenta confinada) y proporciona `GLUONIFY_GDOWN_DATABASE`, `GLUONIFY_GDOWN_USER`, `GLUONIFY_GDOWN_PASSWORD` y el acceso de red (`GLUONIFY_SERVICE_GDOWN_URL`); no hay nada más que hacer. La base **nunca se elimina** con la aplicación.

Un ejemplo listo para usar se encuentra en [`deploy/appspec-graph.json`](deploy/appspec-graph.json).

**A mano** (base compartida, sus propios nombres):

1. Despliegue con `"uses": ["gdown"]` (eso también **abre la red** hacia Gdown) y `SOURCE_STORE=gdown`.
2. Un administrador crea la base y la cuenta de la aplicación ([`deploy/gdown-setup.cypher`](deploy/gdown-setup.cypher): base `source`, rol confinado, cuenta `notes`).
3. Ponga `GLUONIFY_GDOWN_USER` (= `notes`) y `GLUONIFY_GDOWN_PASSWORD` en la caja fuerte.

La API HTTP de Gdown: `POST <url>/db/<base>/query` con `{"statement": "…", "parameters": {…}}` y autenticación básica; respuesta `{"columns": […], "rows": [[…]], "stats": {…}}`. **Siempre parámetros** (`$id`, `$title`), nunca un valor pegado en la instrucción (inyección). Un **mapa** no se puede almacenar como propiedad de un nodo: almacene escalares, listas de escalares, o el JSON como texto. Gdown también puede publicar consultas Cypher con nombre como API REST (contratos OpenAPI, tokens, límites): véase [gluonify.io](https://gluonify.io).

### Archivos distribuidos (`source.store=files`)

Despliegue con `"distributed": ["std"]`: aparece `/distributed/std`, compartido por todas las réplicas en todos los nodos, con 2 copias en los nodos de almacenamiento. **Reglas** (explican la forma de [`FileNoteStore`](src/main/java/io/gluonify/source/notes/FileNoteStore.java)):

1. **Nunca reescribir un archivo ni renombrar una carpeta recién escrita**: escriba archivos **nuevos** con su nombre definitivo (por eso una nota es inmutable); eliminar está permitido.
2. **La lista de una carpeta puede tener ~3 segundos de retraso** respecto a otra réplica: una nota creada en la réplica A puede tardar un momento en aparecer en B.
3. Un archivo se vuelve visible **al cerrarse**; aun así, tolere un archivo ilegible.
4. Sin bloqueos entre nodos por defecto (opción `distributedLocks`).
5. **El cuerpo de una petición HTTP se lee hasta el final**, o la conexión reutilizada se bloquea.

### Recibir los webhooks de Photon

Photon recibe los mensajes de sus socios (verifica su firma HMAC, convierte XML, SOAP o formulario en JSON) y luego los **entrega** a su servicio. Contrato de entrega:

- **un código 2XX confirma**; cualquier otro código (o una caída) provoca el **reenvío** según la política del webhook (`retry`), y luego pasa a la cola de mensajes fallidos;
- por tanto **responda rápido** y sea **idempotente**: «al menos una vez» significa que un mismo mensaje puede llegar dos veces. La cabecera `X-Gluonify-Event-Id` es la clave de deduplicación; `X-Gluonify-Delivery-Attempt` cuenta los intentos; `X-Gluonify-Webhook-Id` nombra el webhook. La memoria **no** se comparte entre réplicas: aquí los identificadores se guardan en el almacenamiento elegido por `source.store` (memoria, un archivo por evento creado con `CREATE_NEW` en `/distributed/std/events`, o un nodo protegido por una restricción de unicidad en Gdown), de modo que una sola réplica acepta un evento.
- Photon **no** envía un token de Charm: proteja el punto de entrada con una clave que ponga en el destino del webhook (`"headers": {"X-Api-Key": "…"}`, véase [`deploy/photon-webhook.json`](deploy/photon-webhook.json)) y en la caja fuerte (`WEBHOOK_KEY`). **Sin clave configurada, el receptor está cerrado** (404).

[`WebhookResource`](src/main/java/io/gluonify/source/platform/WebhookResource.java) lo muestra todo (clave comparada en tiempo constante, deduplicación, JSON ilegible → 400); sustituya el cuerpo de `receive` por su propio tratamiento.

### Llamar a otra aplicación

Declárela en `"uses"`: la plataforma proporciona `GLUONIFY_SERVICE_<APP>_URL` **y abre la red** hacia ella (sin `uses`, la aplicación no la alcanza). Nunca escriba la dirección en el código: lea la variable (véase `ping` en [`PlatformResource`](src/main/java/io/gluonify/source/platform/PlatformResource.java)).

### Sitios estáticos y dominios

- **Field** sirve un sitio estático (ZIP) en su dominio; nada que programar en este servicio.
- **Su dominio**: `PUT /domains/<nombre>` en la API de Higgs (véase [`deploy/domain.json`](deploy/domain.json)) enruta una ruta hacia la aplicación y aplica **políticas**: token de Charm (audiencia, roles), límites de tasa, CORS, listas de direcciones. El certificado HTTPS se obtiene automáticamente.

## 8. Construir

```bash
mvn test                       # pruebas Java (34)
mvn package                    # JVM: target/quarkus-app/; construye también la interfaz (Quinoa)
```

**El ejecutable nativo** (lo que Gluonify ejecuta: un archivo, sin JVM):

```bash
docker build --target out --output type=local,dest=dist -f Dockerfile.build .
./dist/gluonify-source         # funciona en este Linux, puerto 8080
```

El archivo `dist/gluonify-source` es un ejecutable Linux **glibc** de la arquitectura de su Docker (arm64 en Apple Silicon, amd64 en x86_64). Para la otra arquitectura: `docker build --platform linux/amd64 …` (lento: emulación; véanse los comentarios de `Dockerfile.build`).

Sin Docker: `mvn package -Dnative` exige tener GraalVM (NIK 25) instalado.

**Las normas de la plataforma**: tras el clonado, el builder de Gluonify controla el proyecto (`R-SANTE`, `R-SECRET`, `R-FICHIER-SENSIBLE`, `R-NATIF`, `R-IMAGE`, `R-ECOUTE`) y rechaza un repositorio que no las cumpla. [`ConformityTest`](src/test/java/io/gluonify/source/ConformityTest.java) las verifica **en su máquina**; consérvelo.

## 9. Desplegar

Hay dos caminos.

**A. Gluonify construye por usted** (el más sencillo): dé la dirección Git al builder (**gluonify-bup**):

```bash
curl -X POST "$BUP_URL/v1/builds" -H 'Content-Type: application/json' -H "X-Git-Token: $GIT_TOKEN" \
  -d '{"name":"gluonify-source","gitUrl":"https://github.com/VOUS/VOTRE-DEPOT.git","ref":"main","deploy":true,"replicas":2,"memoryMb":128,"topNamespace":"gluonify-source"}'
```

El builder clona, controla la conformidad, compila en nativo, publica el ejecutable y lo despliega. Sígalo: `GET /v1/builds/<id>` y `/logs`. (Un push de Git también puede lanzar el build mediante un webhook: `POST /v1/webhooks/git`.) Nota: el builder solo conoce `topNamespace`, así que con él las claves de la caja fuerte llegan sin prefijo.

**B. Usted publica el ejecutable** y luego despliega:

```bash
SHA=$(shasum -a 256 dist/gluonify-source | cut -d' ' -f1)
# publique dist/gluonify-source en una dirección HTTPS y luego:
curl -X PUT "https://api.$ZONE/apps/gluonify-source" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d @deploy/appspec.json     # tras poner su dirección y su huella en artifacts
```

La aplicación queda entonces en `https://gluonify-source.<zone>` (certificado automático). Los campos de [`deploy/appspec.json`](deploy/appspec.json):

| Campo | Función |
|---|---|
| `artifacts` | el ejecutable por arquitectura: `URL#sha256=…` (la huella es **obligatoria**, la verifica el nodo) |
| `replicas` | número de instancias (2 o más: actualizaciones sin corte) |
| `memoryMb` | límite de memoria por instancia (un nativo cabe en 64 a 128 MB) |
| `uses` | servicios alcanzables: `GLUONIFY_SERVICE_<APP>_URL` proporcionada, red abierta (`id` = Charm, `graphdb` = Gdown) |
| `distributed` | `["std"]` para `/distributed/std` |
| `env` | variables **no secretas**; `${NAME}` y `${NAME:-default}` se resuelven |
| `vault` | `true`: el plano de control asigna a la aplicación su propio espacio de caja fuerte `<uuid>.app`, cuyas claves llegan como `APP_<CLAVE>` |
| `gdownDatabase` | `true`: una base Gdown dedicada para la aplicación (grupo Raft propio, cuenta confinada); `GLUONIFY_GDOWN_DATABASE`, `GLUONIFY_GDOWN_USER`, `GLUONIFY_GDOWN_PASSWORD` y `GLUONIFY_SERVICE_GDOWN_URL` se proporcionan; nunca eliminada con la aplicación (ver [`deploy/appspec-graph.json`](deploy/appspec-graph.json)) |
| `topNamespace` | alternativa: un espacio de caja fuerte existente, por su nombre; sus claves llegan **sin prefijo** (sin `APP_`) |
| `internal` | `true`: ninguna ruta pública (servicio interno) |

Actualizar: el mismo `PUT` (o un nuevo build); las réplicas se sustituyen **una a una**. Reiniciar sin cambios: `POST /apps/<nombre>/redeploy`. Registros: `GET /apps/<nombre>/logs`. Métricas: `/q/metrics`.

## 10. Probar

```bash
mvn test                                 # 34 pruebas Java
cd src/main/webui && npm install && npm test   # 15 pruebas de interfaz
```

| Prueba | Pruebas | Qué verifica |
|---|---|---|
| `NotesResourceTest` | 7 | 401 sin token, 403 sin el rol adecuado, ciclo de vida completo, validación 400, OpenAPI, salud, métricas |
| `FileNoteStoreTest` | 6 | archivos nuevos nunca renombrados, orden, dos réplicas sobre la misma carpeta, archivos ilegibles ignorados, identificadores que nunca son rutas |
| `GraphNoteStoreTest` | 4 | contra un Gdown falso: ruta, autenticación, instrucción **parametrizada**, lectura de las filas, fallo |
| `WebhookResourceTest` / `WebhookClosedTest` | 4 + 1 | clave, confirmación, **idempotencia**, rechazos; cerrado sin clave |
| `PlatformResourceTest` | 4 | variables de la plataforma leídas, **ningún secreto devuelto**, llamada de servicio a servicio limitada a los servicios declarados |
| `ConformityTest` | 4 | las normas del builder, en su máquina |
| `EventLedgerTest` | 4 | registros de idempotencia: un único ganador por evento (memoria, archivos con dos réplicas, restricción de unicidad del grafo, error del grafo distinto de un duplicado propagado) |

Una prueba de API se copia de `NotesResourceTest`; una prueba de almacenamiento, de `FileNoteStoreTest` (sin arrancar Quarkus: rápida).

## 11. Trampas conocidas

- **Nativo**: **no** cree un `HttpClient`, un `Random` ni un `SecureRandom` en un campo `static` (el estado quedaría congelado en la compilación, no en la ejecución): créelos en el primer uso (véase `GraphNoteStore.client()`). Lea el JSON de terceros como árbol (`JsonNode`) en lugar de en clases; un tipo (de)serializado por Jackson fuera de una firma REST debe llevar `@RegisterForReflection`. **Un error que solo aparece en nativo no se ve en `mvn test`**: ejecute el ejecutable nativo antes de entregar.
- **Jackson 3**: el paquete es `tools.jackson.databind`, no `com.fasterxml.jackson.databind`.
- **Ninguna dirección, ningún puerto, ninguna contraseña en el código**: todo viene del entorno.
- **Archivos distribuidos**: véanse las reglas del [§7](#archivos-distribuidos-sourcestorefiles) (nunca reescribir, ~3 s de retraso).
- **Webhooks**: responder rápido con 2XX, ser idempotente, proteger con una clave.
- **Una identidad de desarrollo no tiene nada que hacer en producción**: conserve `@IfBuildProfile("dev")`.
- **Quinoa y las direcciones de API**: añada todo nuevo prefijo de API a `quarkus.quinoa.ignored-path-prefixes`.
- **Bajo emulación x86_64** (Docker en Apple Silicon): construya con `--build-arg MAVEN_OPTS=-Djdk.lang.Process.launchMechanism=VFORK`.

## 12. Solución de problemas

| Síntoma | Causa probable |
|---|---|
| 401 en producción | sin token, token caducado, o **audiencia** distinta de `GLUONIFY_ZZZNONE_OIDC_AUDIENCE`; o `GLUONIFY_ZZZNONE_OIDC_ISSUER` no coincide con el `iss` del token |
| 403 | token válido pero sin el rol (`source:read` / `source:write`) |
| La aplicación no recibe tráfico | `/q/health/ready` no responde 200 (¿almacenamiento inaccesible?): `GET /apps/<nombre>` muestra las instancias listas |
| `store=gdown`: «source.graph.url is empty» | la aplicación no está desplegada con `"uses": ["gdown"]` |
| Gdown: 401 o «Unsupported property value type» | cuenta incorrecta; o un **mapa** almacenado como propiedad (véase §7) |
| Una nota creada no aparece enseguida (archivos) | caché de lista de ~3 s entre réplicas: refrescar |
| Se rechaza el build del repositorio | una regla `R-…`: el mensaje la nombra; `mvn test` (`ConformityTest`) la muestra en su máquina |
| `mvn package` no encuentra Node | acceso a la red para descargarlo, o `-Dquarkus.quinoa=false` para construir sin la interfaz |
| El nativo falla al arrancar pero no en JVM | un estado congelado en la compilación (`static`) o una reflexión que falta (§11) |

---

*Gluonify es una plataforma minimalista para aplicaciones Quarkus nativas: [gluonify.cloud](https://gluonify.cloud) (presentación) y [gluonify.io](https://gluonify.io) (técnica). Este repositorio es deliberadamente pequeño: se lee en una hora.*
