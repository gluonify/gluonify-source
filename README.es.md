# gluonify-source

[English](README.md) · [Français](README.fr.md) · **Español** · [Italiano](README.it.md) · [Deutsch](README.de.md)

> **Variante Gradle.** El mismo servicio, construido con **Gradle** (Kotlin DSL) en lugar de Maven, existe en [`gluonify-source-gradle`](https://github.com/gluonify/gluonify-source-gradle): mismo código, mismas pruebas, mismas reglas de plataforma.

**El punto de partida de un servicio Quarkus 4 en [Gluonify](https://gluonify.cloud).** Un pequeño servicio completo (unas «notas») que muestra, con código que funciona y pruebas, cómo:

1. crear un **servicio REST** (validación, roles, OpenAPI);
2. crear una **interfaz web** con [Quinoa](https://quarkus.io/extensions/io.quarkiverse.quinoa/quarkus-quinoa/) (Vue 3, servida por el mismo ejecutable);
3. **interactuar con los servicios de Gluonify**: configuración y secretos (Top), tokens de identidad (Charm), datos (Gdown) o archivos distribuidos, otras aplicaciones, webhooks recibidos de Photon, documentos PDF (Crystallon), sitios estáticos (Field), dominios y políticas (Higgs);
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
| `/api/documents/invoice` | `POST`: una factura en PDF, producida por Crystallon cuando está configurado (véase [§7](#documentos-crystallon)) |
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
gluonify.json                   lo lee el builder de Gluonify (Bup): los servicios que declara al desplegar (`uses`), como en deploy/appspec.json
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
  documents/                    documentos PDF con Crystallon (opcional)
    DocumentsResource.java      POST /api/documents/invoice: su factura de entrada, un PDF de salida
    InvoiceRequest.java         la factura de SU dominio (cuerpo validado)
    InvoiceMapping.java         su factura -> los datos de la plantilla (la única clase que conoce su forma)
    CrystallonClients.java      construye el cliente del SDK desde la configuración (sin dirección = función apagada) y elige el token
    TokenMode.java              qué token recibe Crystallon: el de quien llama (forwarding) o el de la propia aplicación (platform)
    ForwardedToken.java         el token Bearer de quien llama, reenviado a Crystallon (modo forwarding)
    DocumentErrors.java         fallo de Crystallon -> 403 / 422 / 502 / 504
    DocumentsReflection.java    imagen nativa: los records de los datos generados
    generated/                  código generado por la pestaña «Código» de Crystallon (no editar a mano)
  security/DevAuthentication.java   identidad de desarrollo (solo perfil dev)
src/main/webui/                 la interfaz Vue 3 (Vite + vitest), construida por Quinoa
src/test/java/…                 86 pruebas Java; src/main/webui/src/App.test.js: 33 pruebas de interfaz
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
| **Otras aplicaciones** | llamada de servicio a servicio | `"uses": ["other"]` → `GLUONIFY_SERVICE_OTHER_URL` | [`PlatformResource.ping`](src/main/java/io/gluonify/source/platform/PlatformResource.java) |
| **Photon** (pasarela de API) | webhooks firmados, conversión XML / SOAP / formulario → JSON, reenvío | Photon entrega en `…/hooks/events`; su servicio confirma con 2XX | [`WebhookResource`](src/main/java/io/gluonify/source/platform/WebhookResource.java) |
| **Crystallon** (documentos PDF) | facturas, presupuestos y cartas en PDF/A a partir de plantillas dibujadas en su editor; cliente Java del SDK, código generado | opcional: `"uses": ["crystallon"]` → `GLUONIFY_SERVICE_CRYSTALLON_URL` y el archivo del token de la aplicación (`GLUONIFY_CRYSTALLON_TOKEN_FILE`); se reenvía el token de quien llama (por defecto) o se usa el token propio de la aplicación (`source.crystallon.token=platform`), rol `gluonify:crystallon:render` | [`DocumentsResource`](src/main/java/io/gluonify/source/documents/DocumentsResource.java), [detalles](#documentos-crystallon) |
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

### Documentos (Crystallon)

**Crystallon** produce documentos PDF (facturas, presupuestos, cartas; PDF/A-1B bajo demanda) a partir de una **plantilla** dibujada en su editor gráfico y conservada allí, con su historial, para cada organización. La aplicación envía sus datos (el *payload*, un JSON) y recibe el PDF. Es un componente **opcional** de la plataforma, así que este código es también el ejemplo de una integración opcional: [`DocumentsResource`](src/main/java/io/gluonify/source/documents/DocumentsResource.java) ofrece `POST /api/documents/invoice`, que recibe una factura del dominio propio de la aplicación ([`InvoiceRequest`](src/main/java/io/gluonify/source/documents/InvoiceRequest.java)) y responde `application/pdf`.

**1. La dependencia.** El cliente Java del SDK de Gluonify lo sirve el repositorio Maven de **gluonify-sea** (lectura pública: no hace falta ninguna cuenta para resolverlo). Declare el repositorio y la dependencia en `pom.xml` (ya hecho aquí):

```xml
<repositories>
  <repository>
    <id>gluonify-sea</id>
    <url>https://images.gluonify.io/maven</url>
  </repository>
</repositories>

<dependency>
  <groupId>io.gluonify.plasma</groupId>
  <artifactId>gluonify-plasma-crystallon-client</artifactId>
  <version>0.6.0</version>
</dependency>
```

El repositorio se añade a Maven Central, no lo sustituye. La versión es la de la plataforma (la pestaña *Código* del editor de Crystallon indica la de su instalación). Este `pom.xml` escribe la dirección como propiedad `gluonify.sea.maven.url`, para que un espejo o una copia local pueda sustituirla (`mvn verify -Dgluonify.sea.maven.url=file:///…`). El builder de Gluonify (Bup) declara por sí mismo este repositorio en los builds que lanza. El cliente es Java 17, sin CDI ni Quarkus, y usa el Jackson de la aplicación (aquí Jackson 3). Con Gradle: `maven("https://images.gluonify.io/maven")` en `repositories` e `implementation("io.gluonify.plasma:gluonify-plasma-crystallon-client:0.6.0")`.

**2. Declarar el uso.** `"uses": ["charm", "crystallon"]` en la especificación de la aplicación ([`deploy/appspec.json`](deploy/appspec.json)): la plataforma proporciona `GLUONIFY_SERVICE_CRYSTALLON_URL` **y abre la red** hacia Crystallon, y solo hacia él; [`CrystallonClients`](src/main/java/io/gluonify/source/documents/CrystallonClients.java) la lee (`source.crystallon.url`) y construye el cliente. **Sin ello la función queda apagada y nada más cambia**: el punto de entrada responde 404 e indica por qué, `GET /api/platform` indica `"documents": false`, la interfaz oculta la sección, la aplicación arranca y todas las demás pruebas pasan sin Crystallon.

**3. El token y el rol.** Crystallon exige un token de Charm de audiencia `gluonify-crystallon`; el rol **`gluonify:crystallon:render`** basta para generar y validar (`convert` y `templates` no hacen falta para producir documentos). Las plantillas son las de la **organización** que lleva el token. El ajuste **`source.crystallon.token`** (variable de entorno `SOURCE_CRYSTALLON_TOKEN`) elige **qué** token recibe Crystallon. Designa un modo; nunca contiene un token:

| `source.crystallon.token` | Crystallon ve | Elíjalo cuando |
|---|---|---|
| `forwarding` (por defecto) | a la **persona** que llama a esta aplicación: su token se reenvía tal cual ([`ForwardedToken`](src/main/java/io/gluonify/source/documents/ForwardedToken.java), `CrystallonTokens.forwarding`) | Crystallon debe saber quién pide (sus roles, su organización). Una llamada hecha fuera de una petición no tiene ningún token que reenviar |
| `platform` | a la **aplicación**: su token propio, que la plataforma emite para una aplicación desplegada con `"uses": ["crystallon"]` (token de Charm, sujeto `app:<nombre>`, audiencia `gluonify-crystallon`, rol `gluonify:crystallon:render`) | la llamada se hace en nombre de la aplicación (un temporizador, un webhook, una cola de mensajes), o quienes llaman no tienen token de Crystallon. Nada que emitir a mano |

**Reenvío (por defecto).** El token que envíe a `/api/documents/invoice` debe llevar, además de la audiencia y el rol de esta aplicación (`gluonify-source`, `source:read`), la audiencia `gluonify-crystallon` y el rol `gluonify:crystallon:render`. Charm pone varias audiencias en un mismo token (`audiences`, 8 como máximo); como el prefijo `gluonify:` está reservado a la plataforma, lo emite el administrador:

```bash
curl -s -X POST "$CHARM_URL/v1/tokens" -H "Authorization: Bearer $GLUONIFY_CHARM_ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"sub":"ada","audiences":["gluonify-source","gluonify-crystallon"],"ttlSeconds":3600,"roles":["source:read","gluonify:crystallon:render"]}'
```

**Plataforma.** Nada que emitir: el token que envíe solo tiene que abrir esta API (`source:read`). Con `"uses": ["crystallon"]`, el plano de control (Higgs) hace que Charm emita el token de la aplicación, y el agente del nodo (Strange) lo escribe en el archivo `/run/gluonify-tokens/crystallon.token` (de solo lectura dentro del sandbox de la instancia) y lo renueva antes de que caduque (900 s por defecto). La plataforma proporciona la variable `GLUONIFY_CRYSTALLON_TOKEN_FILE` con esa ruta (ajuste `source.crystallon.token-file`). [`CrystallonClients`](src/main/java/io/gluonify/source/documents/CrystallonClients.java) vuelve a leer el archivo **en cada llamada**: una renovación no exige reiniciar. Conviene saber:

- el token de la aplicación lleva la organización por defecto de Crystallon (`default`): la plantilla `Gluonify invoice` debe existir allí;
- sin la variable (la aplicación no está desplegada con `"uses": ["crystallon"]`, o la plataforma aún no emite tokens de aplicación), `platform` **detiene la aplicación al arrancar** con un mensaje que dice por qué, en lugar de fallar en la primera petición;
- cuando Crystallon rechaza el token de la aplicación, la respuesta es un 502 y no un 403: quien llama no puede arreglarlo, el despliegue sí;
- el SDK publicado (0.6.0) todavía no tiene `CrystallonTokens.platform()` (está en las fuentes del SDK, para una versión posterior): `CrystallonClients.platform` hace lo mismo con `CrystallonTokens.file(…)`. Con un SDK más reciente, sustitúyalo por esa llamada.

Dónde definir `SOURCE_CRYSTALLON_TOKEN=platform`: en `"env"` con `PUT /apps/<nombre>` (camino B del [§9](#9-desplegar)); con el builder (camino A), que no tiene campo `env`, en el espacio de la caja fuerte de `topNamespace` (las claves llegan sin prefijo). Con `mvn quarkus:dev`, esta API no pide token, pero Crystallon sí: defina `GLUONIFY_SERVICE_CRYSTALLON_URL` con su dirección y luego envíe (o pegue en la interfaz) un token de Charm que cumpla las condiciones anteriores, que se reenvía tal cual, o pruebe `platform` con un token de Charm escrito en un archivo (`SOURCE_CRYSTALLON_TOKEN=platform` y `GLUONIFY_CRYSTALLON_TOKEN_FILE=<ese archivo>`).

**4. La plantilla y el código generado.** El ejemplo llama a la plantilla de Crystallon llamada **`Gluonify invoice`**: créela en la consola de Crystallon (`/crystallon/` detrás del orquestador; *Nueva plantilla*, *Empezar desde* la plantilla predefinida `invoice`, con exactamente ese nombre). Crystallon **genera** entonces **el código Java** de una plantilla: records que describen exactamente los datos que lee, y una clase que la llama. Abra la plantilla y su pestaña **Código** (pide guardar antes: el código se genera a partir de la versión guardada), ponga *Paquete Java* a `io.gluonify.source.documents.generated` y *Nombre de la clase* a `GluonifyInvoice`, deje sin marcar *Fijar esta versión* (el código genera entonces la versión actual de la plantilla), y luego *Descargar (zip)* o *Copiar*. Los tres archivos de [`documents/generated/`](src/main/java/io/gluonify/source/documents/generated) son exactamente esa salida (solo se añadió un comentario de cabecera), generada a partir de la versión 1 de la plantilla:

- `GluonifyInvoicePayload`: un record por objeto de los datos (`Sender`, `Recipient`, `Invoice`, `Item`, `Payment`); el Javadoc de cada componente indica si es obligatorio;
- `GluonifyInvoiceDocument`: la fachada, con `render(payload)`, `render(payload, RenderOptions)` y `validate(payload)`; llama a la plantilla **por su nombre** (el mismo en cada entorno) y lleva las constantes `TEMPLATE_NAME`, `TEMPLATE_VERSION` y `SCHEMA_SHA256`;
- `package-info`.

**Para regenerarlos** (la plantilla cambió y ahora espera otros datos: su `SCHEMA_SHA256` ya no es el de la fachada), genere de nuevo en la pestaña Código con los mismos dos campos y sustituya los tres archivos; no los edite nunca a mano. El compilador muestra entonces qué adaptar en [`InvoiceMapping`](src/main/java/io/gluonify/source/documents/InvoiceMapping.java), la única clase que conoce la forma de los datos. Sin la consola, el mismo código viene de la API (rol `gluonify:crystallon:templates`, no `render`): `GET /v1/templates/<id>/code?lang=java&package=io.gluonify.source.documents.generated&name=GluonifyInvoice`.

**Imagen nativa.** El SDK declara para la reflexión sus propios records; **los de los datos son cosa de la aplicación**. El record raíz Y cada record anidado de cada payload generado figuran en [`DocumentsReflection`](src/main/java/io/gluonify/source/documents/DocumentsReflection.java) (`@RegisterForReflection`). Olvidar uno solo rompe el ejecutable nativo, en la primera llamada: `DocumentsReflectionTest` lo comprueba.

**5. El punto de entrada.**

```bash
curl -s -X POST localhost:8080/api/documents/invoice -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -o invoice.pdf -d '{
  "number": "2026-0042", "subject": "Website relaunch", "lang": "en",
  "customer": {"name": "Example Customer SA", "street": "Rue du Lac", "houseNumber": "7", "postalCode": "1003", "city": "Lausanne", "country": "CH"},
  "lines": [{"description": "Consulting, 12 hours", "quantity": 12, "unit": "h", "unitPrice": 160}]}'
```

`source:read` lo abre. `date` (hoy), `vatRate` (8,1 %) y `unit` (`pce`) tienen valores por defecto; el importe de cada línea lo calcula la aplicación; `lang` (`en`, `fr`, `es`, `it`, `de`) es opcional, la moneda, el país y la zona horaria son los de la plantilla (CHF, CH, Europe/Zurich). La respuesta es el PDF (`200`, `application/pdf`, PDF/A-1B, `Content-Disposition: attachment`, y las cabeceras `X-Crystallon-Pages` y `X-Crystallon-Warnings`). Los errores tienen el cuerpo habitual `{"error": "…"}`, más `code` y `path` (el lugar del problema en la plantilla) cuando Crystallon los da ([`DocumentErrors`](src/main/java/io/gluonify/source/documents/DocumentErrors.java)):

| Situación | Respuesta |
|---|---|
| petición no válida (número vacío, ninguna línea, `lang` desconocido…) | 400; no se llama a Crystallon |
| sin token, o sin `source:read` | 401 / 403 |
| Crystallon rechaza el token (falta la audiencia o el rol `gluonify:crystallon:render`, caducado) | `forwarding`: 403, con el motivo (el token de quien llama no basta); `platform`: 502 (el token de la aplicación: quien llama no puede arreglarlo) |
| la plantilla no puede generar estos datos (Crystallon 400 / 422) | 422, con `code` y `path` |
| ninguna plantilla con ese nombre para la organización del token (Crystallon 404), Crystallon en fallo (5XX), respuesta inesperada, inalcanzable, archivo del token ilegible (`platform`) | 502 |
| Crystallon demasiado lento (30 s) | 504 |
| Crystallon no configurado | 404 |

**En la interfaz**: un botón *Descargar una factura de ejemplo* en la sección *Documentos* (solo se muestra cuando `GET /api/platform` indica `"documents": true`) envía el token que haya pegado y una factura de ejemplo; su ayuda dice qué token recibe Crystallon (`GET /api/platform` responde `"documentsToken": "forwarding"` o `"platform"`). [`DocumentsResourceTest`](src/test/java/io/gluonify/source/documents/DocumentsResourceTest.java) y [`DocumentsPlatformTokenTest`](src/test/java/io/gluonify/source/documents/DocumentsPlatformTokenTest.java) prueban todo esto contra un Crystallon simulado (un `HttpServer` del JDK): la ruta llamada (`/v1/templates/by-name/Gluonify%20invoice/render`), el token reenviado o el del archivo de token (leído de nuevo tras una renovación), los datos enviados y cada error anterior.

### Llamar a otra aplicación

Declárela en `"uses"`: la plataforma proporciona `GLUONIFY_SERVICE_<APP>_URL` **y abre la red** hacia ella (sin `uses`, la aplicación no la alcanza). Nunca escriba la dirección en el código: lea la variable (véase `ping` en [`PlatformResource`](src/main/java/io/gluonify/source/platform/PlatformResource.java)).

### Sitios estáticos y dominios

- **Field** sirve un sitio estático (ZIP) en su dominio; nada que programar en este servicio.
- **Su dominio**: `PUT /domains/<nombre>` en la API de Higgs (véase [`deploy/domain.json`](deploy/domain.json)) enruta una ruta hacia la aplicación y aplica **políticas**: token de Charm (audiencia, roles), límites de tasa, CORS, listas de direcciones. El certificado HTTPS se obtiene automáticamente.

## 8. Construir

```bash
mvn test                       # pruebas Java (86)
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

**Los servicios que declara el builder.** El builder envía `uses` y `gdownDatabase` a `PUT /apps/<nombre>`, tomados de la petición de build (`"uses": ["charm","gdown","crystallon"]`) o, si la petición no los trae, del [`gluonify.json`](gluonify.json) en la raíz del repositorio (la petición manda; una lista vacía en la petición significa «ningún servicio»). El archivo de este repositorio es `{"uses": ["charm", "gdown", "crystallon"]}`: los mismos servicios que [`deploy/appspec.json`](deploy/appspec.json), lo que comprueba `DeploySpecsTest`. Importa porque `PUT /apps/<nombre>` **sustituye toda la especificación**: un build que no enviara `uses` desplegaría la aplicación sin `GLUONIFY_SERVICE_*_URL`, sin red hacia Charm, Gdown y Crystallon, y sin el token de Crystallon de la aplicación (así que `source.crystallon.token=platform` la detendría al arrancar). Añada `"gdownDatabase": true` para la base de datos dedicada; un `?` inicial (`"?crystallon"`) marca un servicio opcional. Declare siempre `uses` en `gluonify.json` o en la petición de build.

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
| `uses` | servicios alcanzables, por nombre de aplicación (`charm`, `gdown`, `crystallon`…): `GLUONIFY_SERVICE_<APP>_URL` proporcionada, red abierta; para `crystallon`, también el token de la aplicación (`GLUONIFY_CRYSTALLON_TOKEN_FILE`). Con el builder: en `gluonify.json` o en la petición de build |
| `distributed` | `["std"]` para `/distributed/std` |
| `env` | variables **no secretas**; `${NAME}` y `${NAME:-default}` se resuelven |
| `top` | `true`: el plano de control asigna a la aplicación su propio espacio de caja fuerte `<uuid>.app`, cuyas claves llegan como `APP_<CLAVE>` |
| `gdownDatabase` | `true`: una base Gdown dedicada para la aplicación (grupo Raft propio, cuenta confinada); `GLUONIFY_GDOWN_DATABASE`, `GLUONIFY_GDOWN_USER`, `GLUONIFY_GDOWN_PASSWORD` y `GLUONIFY_SERVICE_GDOWN_URL` se proporcionan; nunca eliminada con la aplicación (ver [`deploy/appspec-graph.json`](deploy/appspec-graph.json)) |
| `topNamespace` | alternativa: un espacio de caja fuerte existente, por su nombre; sus claves llegan **sin prefijo** (sin `APP_`) |
| `internal` | `true`: ninguna ruta pública (servicio interno) |

Actualizar: el mismo `PUT` (o un nuevo build); las réplicas se sustituyen **una a una**. Reiniciar sin cambios: `POST /apps/<nombre>/redeploy`. Registros: `GET /apps/<nombre>/logs`. Métricas: `/q/metrics`.

## 10. Probar

```bash
mvn test                                 # 86 pruebas Java
cd src/main/webui && npm install && npm test   # 33 pruebas de interfaz
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
| `DocumentsResourceTest` | 13 | contra un **Crystallon simulado**: PDF devuelto, ruta llamada (`/v1/templates/by-name/Gluonify%20invoice/render`), **token reenviado**, datos enviados, correspondencia de los 422 / 403 / 502, validación propia (400), roles, OpenAPI |
| `DocumentsDisabledTest` | 4 | Crystallon no configurado: 404 con el motivo, `"documents": false`, el resto intacto, ajustes del token vacíos por defecto |
| `DocumentsPlatformTokenTest` | 8 | `source.crystallon.token=platform` contra el Crystallon simulado: se envía el **token de la aplicación** (no el de quien llama), también fuera de una petición, el archivo se **lee de nuevo tras una renovación**, un rechazo da un 502, `"documentsToken"` |
| `CrystallonClientsTest` | 11 | sin dirección = sin cliente, una dirección incorrecta nombrada, el token de quien llama pedido en cada llamada; los dos modos: modo desconocido (valor nunca repetido), `platform` sin su archivo, token del archivo leído de nuevo, ignorado con `forwarding` |
| `InvoiceMappingTest` / `DocumentErrorsTest` / `ForwardedTokenTest` | 3 + 9 + 1 | su factura → los datos de la plantilla (importes, valores por defecto, nombre de archivo seguro); cada fallo de Crystallon → un estado, en cada modo de token; qué cabeceras dan un token |
| `DocumentsReflectionTest` | 1 | salvaguarda nativa: cada record de los datos generados está declarado para la reflexión |
| `DeploySpecsTest` | 2 | las especificaciones de `deploy/` usan los nombres actuales (`uses`, `SOURCE_STORE`); `gluonify.json` (leído por el builder) declara los mismos `uses` que `deploy/appspec.json` |

Una prueba de API se copia de `NotesResourceTest`; una prueba de almacenamiento, de `FileNoteStoreTest` (sin arrancar Quarkus: rápida).

## 11. Trampas conocidas

- **Nativo**: **no** cree un `HttpClient`, un `Random` ni un `SecureRandom` en un campo `static` (el estado quedaría congelado en la compilación, no en la ejecución): créelos en el primer uso (véase `GraphNoteStore.client()`). Lea el JSON de terceros como árbol (`JsonNode`) en lugar de en clases; un tipo (de)serializado por Jackson fuera de una firma REST debe llevar `@RegisterForReflection`. **Un error que solo aparece en nativo no se ve en `mvn test`**: ejecute el ejecutable nativo antes de entregar.
- **Jackson 3**: el paquete es `tools.jackson.databind`, no `com.fasterxml.jackson.databind`.
- **Ninguna dirección, ningún puerto, ninguna contraseña en el código**: todo viene del entorno.
- **Archivos distribuidos**: véanse las reglas del [§7](#archivos-distribuidos-sourcestorefiles) (nunca reescribir, ~3 s de retraso).
- **Webhooks**: responder rápido con 2XX, ser idempotente, proteger con una clave.
- **Código generado**: los archivos de `documents/generated/` vienen de la pestaña *Código* de Crystallon: regenérelos, no los edite nunca a mano; y declare cada record de un payload generado en `DocumentsReflection` (un fallo que solo aparece en nativo).
- **Nombres que parecen secretos**: la regla R-SECRET del builder rechaza un valor en claro en un ajuste cuyo nombre contenga `token`, `password`, `secret` o `key`. `source.crystallon.token` contiene un modo, no un token, y aun así se escribe `${SOURCE_CRYSTALLON_TOKEN:}`; hoy el builder acepta ahí `${VARIABLE}` y `${VARIABLE:}` (valor por defecto vacío), **no** `${VARIABLE:forwarding}`: el valor por defecto vive en el código (`TokenMode`). `ConformityTest` es más permisivo que el builder en este punto (acepta cualquier `${…}`): conserve el valor por defecto vacío.
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
| `POST /api/documents/invoice`: 404 «documents are off» | la aplicación no está desplegada con `"uses": ["crystallon"]` (sin `GLUONIFY_SERVICE_CRYSTALLON_URL`) |
| Documentos: 403 «Crystallon did not accept your token» | modo `forwarding`: al token le falta la audiencia `gluonify-crystallon` o el rol `gluonify:crystallon:render`, o ha caducado (véase [§7](#documentos-crystallon)); o elija `source.crystallon.token=platform` |
| Documentos: 502 «did not accept the token of the application» | modo `platform`: la aplicación no está desplegada con `"uses": ["crystallon"]`, la plataforma aún no emite tokens de aplicación, o `GLUONIFY_CRYSTALLON_TOKEN_FILE` falta, está vacío o caducado |
| La aplicación no arranca: «source.crystallon.token=platform needs the file of the application token» | `platform` sin `GLUONIFY_CRYSTALLON_TOKEN_FILE`: declare `"uses": ["crystallon"]` (también en `gluonify.json` para un build del builder) o use `forwarding` |
| Documentos: 502 «does not know the template» | ninguna plantilla llamada `Gluonify invoice` en la organización del token (`platform`: la organización por defecto de Crystallon): créela en la consola de Crystallon |
| Documentos: 422 con `code` y `path` | la plantilla no puede generar estos datos: `path` es el lugar del problema en la plantilla |
| Los documentos funcionan en JVM pero fallan en la primera llamada del ejecutable nativo | falta un record de los datos generados en `DocumentsReflection` |

---

*Gluonify es una plataforma minimalista para aplicaciones Quarkus nativas: [gluonify.cloud](https://gluonify.cloud) (presentación) y [gluonify.io](https://gluonify.io) (técnica). Este repositorio es deliberadamente pequeño: se lee en una hora.*
