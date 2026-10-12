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
gluonify.json                   lo lee el builder de Gluonify (Bup): solo los servicios que el SDK no detecta (`uses`: charm, gdown); Bup deduce el resto
crystallon/templates/           la plantilla Crystallon del ejemplo, tal como la exporta la consola; el builder la instala al desplegar
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
    Documents.java              ¿está disponible Crystallon? Nada que configurar: el cliente viene del SDK (gluonify-plasma-quarkus)
    DocumentErrors.java         fallo de Crystallon -> 403 / 422 / 502 / 504
    DocumentsReflection.java    imagen nativa: los records de los datos generados
    generated/                  código generado por la pestaña «Código» de Crystallon (no editar a mano)
  security/DevAuthentication.java   identidad de desarrollo (solo perfil dev)
  security/AuthConfigResource.java GET /api/auth-config: lo que la interfaz necesita para iniciar sesión con Charm (público)
src/main/webui/                 la interfaz Vue 3 (Vite + vitest), construida por Quinoa
  src/auth.js                   inicio de sesión con Charm (código de autorización + PKCE) mediante el kit @gluonify/ui
  vendor/gluonify-ui/           copia incluida del kit (gluonify-ui/scripts/sync-vendor.sh, nunca se edita a mano)
src/test/java/…                 67 pruebas Java; src/main/webui/src/App.test.js: 40 pruebas de interfaz
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
- **Llamar a la API**: direcciones relativas (`fetch('/api/notes')`), véase [`src/api.js`](src/main/webui/src/api.js). El token de Charm viene del inicio de sesión ([`src/auth.js`](src/main/webui/src/auth.js), véase [§6](#6-autenticación-los-tokens-de-charm)), se conserva durante la pestaña (`sessionStorage`) y se añade como `Authorization: Bearer`.
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
| Emisor (`iss`) | `quarkus.oidc.token.issuer` | `GLUONIFY_SOURCE_OIDC_ISSUER` = `https://id.<su zona>/realms/gluonify` (estable: no es la dirección de la instancia de Charm) |
| Audiencia (`aud`) | `quarkus.oidc.token.audience` | `GLUONIFY_SOURCE_OIDC_AUDIENCE`, por defecto el nombre del proyecto (`gluonify-source`) |
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

### Iniciar sesión desde la interfaz (página de inicio de sesión de Charm)

La interfaz nunca pide una contraseña ni le hace pegar un token. Es un **cliente OpenID Connect público** de Charm (código de autorización + PKCE S256, ningún secreto en el navegador), construido sobre el kit común `@gluonify/ui` (`createAuth`, el mismo inicio de sesión que las consolas de la plataforma), incluido en [`src/main/webui/vendor/gluonify-ui`](src/main/webui/vendor/gluonify-ui) para que un clon se construya solo (se actualiza con `gluonify-ui/scripts/sync-vendor.sh <este repositorio> src/main/webui`, nunca a mano):

1. La página pide `GET /api/auth-config` (público): el identificador de cliente, las direcciones **públicas** de Charm (`/authorize`, `/token`, `/logout`) y `signInRequired` (falso con `mvn quarkus:dev`: la API no pide token, así que la interfaz lo muestra todo).
2. **Iniciar sesión** lleva el navegador a la página de Charm: la persona escribe allí su usuario y su contraseña, **allí**, nunca en la aplicación. Charm devuelve el navegador a la interfaz (`https://<dirección de la interfaz>/?code=…&state=…`).
3. La interfaz comprueba `state`, intercambia el código con su verificador PKCE en `/token` de Charm y obtiene un token de audiencia `gluonify-source` cuyo claim `roles` lleva los roles de la cuenta (`source:read`, `source:write`). El token se conserva durante la pestaña (`sessionStorage`), se renueva con el token de actualización antes de caducar y se envía como `Authorization: Bearer` a `/api`.
4. **Cerrar sesión** olvida el token, avisa a las demás pestañas y cierra la sesión de Charm (`/logout` con `id_token_hint`). Un 401 de la API (token caducado o revocado) vuelve a mostrar el inicio de sesión.

Sin sesión, la página solo muestra una breve explicación y el botón **Iniciar sesión**: las notas, los documentos y la sección de la plataforma necesitan un token.

| Ajuste | Variable | Valor |
|---|---|---|
| `source.auth.client-id` | `GLUONIFY_SOURCE_OIDC_CLIENT_ID` | el cliente de Charm de esta interfaz (por ejemplo `source-ui`); vacío: sin botón *Iniciar sesión*, la página nombra la variable |
| `source.auth.charm-url` | `GLUONIFY_SOURCE_CHARM_PUBLIC_URL` | la dirección **pública** de Charm, la que alcanza el navegador; vacía: se deduce de `GLUONIFY_SOURCE_OIDC_ISSUER` (`https://gluonify.lan/realms/gluonify` → `https://gluonify.lan`). Nunca `GLUONIFY_SERVICE_CHARM_URL`: es una dirección interna |
| `source.auth.scope` | `SOURCE_AUTH_SCOPE` | `openid profile` (el kit añade `offline_access` para el token de actualización) |

**El cliente de Charm** (una vez, por el administrador de la plataforma): un cliente público cuya única redirección (y redirección tras cerrar sesión) es la dirección exacta de la interfaz, barra final incluida, y cuya audiencia es la de la aplicación. Charm solo acepta llamadas de otro origen a `/token` desde los orígenes de las redirecciones registradas.

```bash
curl -s -X PUT "$CHARM_URL/v1/admin/clients/source-ui" -H "Authorization: Bearer $GLUONIFY_CHARM_ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Source","redirectUris":["https://source.<zone>/"],"postLogoutRedirectUris":["https://source.<zone>/"],"audiences":["gluonify-source"],"scopes":["openid","profile","email","offline_access"],"accessTtl":3600}'
```

**Los roles** `source:read` y `source:write` se dan a las cuentas, incluidas las de clientes (los roles de una aplicación no son `gluonify:*`, así que Charm permite crearlos y darlos): `PUT /v1/admin/roles/source:read` (y `source:write`), luego `PUT /v1/admin/users/<id>/roles`. Charm pone todos los roles de la cuenta en el token: cierre sesión y vuelva a iniciarla tras un cambio.

**En local**, el perfil `dev` proporciona la identidad «dev» (véase [`DevAuthentication`](src/main/java/io/gluonify/source/security/DevAuthentication.java)); **en las pruebas**, `@TestSecurity`. Nunca una identidad de este tipo en producción: solo se compila en el perfil `dev`.

## 7. Interactuar con los servicios de Gluonify

Una aplicación en Gluonify está **aislada**: su propia cuenta, su propia red, un sistema de archivos reducido. Solo alcanza lo que **declara** (`"uses"`) y recibe su configuración **por el entorno**. Aquí está cada servicio, con el lugar en el código.

**Sin configuración para los servicios que cubre el SDK.** El SDK de Gluonify encuentra por sí mismo dónde está un servicio y qué token presentar: la plataforma proporciona la dirección y el token de la aplicación (`"uses"`), `gluonify login <zona>` hace lo mismo en su equipo y `GluonifyTestServices` sustituye los servicios en las pruebas. Hoy el SDK cubre **Crystallon** (véase [Documentos](#documents-crystallon)); Gdown y Charm aún no están cubiertos y siguen configurándose por URL (`source.graph.url`, `quarkus.oidc.auth-server-url`): vendrán después.

| Servicio | Qué ofrece | Cómo lo usa la aplicación | En este repositorio |
|---|---|---|---|
| **Top** (caja fuerte) | configuración y secretos por aplicación | con `"top": true`, las claves del espacio `<uuid>.app` propio de su aplicación llegan como `APP_<CLAVE>`; `${app.gluonify.gdown.password}` en `application.properties` | `source.graph.password`, `source.webhook.key` |
| **Charm** (identidad) | tokens JWT de corta duración | `"uses": ["charm"]` → `GLUONIFY_SERVICE_CHARM_URL`; `quarkus-oidc` verifica los tokens | [§6](#6-autenticación-los-tokens-de-charm) |
| **Gdown** (base de grafos) | base replicada (Raft), Cypher por HTTP | `"uses": ["gdown"]` → `GLUONIFY_SERVICE_GDOWN_URL`; una cuenta local de Gdown | [`GraphNoteStore`](src/main/java/io/gluonify/source/notes/GraphNoteStore.java) |
| **Archivos distribuidos** | `/distributed/std`: duradero, compartido por todas las réplicas, 2 copias | `"distributed": ["std"]` en el despliegue | [`FileNoteStore`](src/main/java/io/gluonify/source/notes/FileNoteStore.java) |
| **Otras aplicaciones** | llamada de servicio a servicio | `"uses": ["other"]` → `GLUONIFY_SERVICE_OTHER_URL` | [`PlatformResource.ping`](src/main/java/io/gluonify/source/platform/PlatformResource.java) |
| **Photon** (pasarela de API) | webhooks firmados, conversión XML / SOAP / formulario → JSON, reenvío | Photon entrega en `…/hooks/events`; su servicio confirma con 2XX | [`WebhookResource`](src/main/java/io/gluonify/source/platform/WebhookResource.java) |
| **Crystallon** (documentos PDF) | facturas, presupuestos y cartas en PDF/A a partir de plantillas dibujadas en su editor; cliente Java del SDK, código generado | opcional, **sin configuración**: nada que declarar (construida por Bup, `?crystallon` se deduce del SDK) y el SDK (`gluonify-plasma-quarkus`) encuentra la dirección y el token propio de la aplicación (rol `gluonify:crystallon:render`); en su equipo `gluonify login` | [`DocumentsResource`](src/main/java/io/gluonify/source/documents/DocumentsResource.java), [detalles](#documents-crystallon) |
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

**1. La dependencia.** Una dependencia aporta todo el lado SDK: `gluonify-plasma-quarkus` (beans CDI por defecto: se inyecta un `CrystallonClient`; trae `gluonify-plasma-services`, que encuentra direcciones y tokens, y el cliente de Crystallon). `gluonify-plasma-testing` (ámbito de prueba) proporciona los servicios simulados para las pruebas. Ambas están declaradas en el `pom.xml` (ya hecho aquí):

```xml
<dependency>
  <groupId>io.gluonify.plasma</groupId>
  <artifactId>gluonify-plasma-quarkus</artifactId>
  <version>${gluonify.sdk.version}</version>
</dependency>
<dependency>
  <groupId>io.gluonify.plasma</groupId>
  <artifactId>gluonify-plasma-testing</artifactId>
  <version>${gluonify.sdk.version}</version>
  <scope>test</scope>
</dependency>
```

El SDK lo sirve el repositorio Maven de **gluonify-sea** (lectura pública: ninguna cuenta para resolverlo), añadido a Maven Central, no en su lugar; este `pom.xml` escribe su dirección como la propiedad `gluonify.sea.maven.url`, para que un espejo o una copia local pueda sustituirla (`mvn verify -Dgluonify.sea.maven.url=file:///…`). El builder de Gluonify (Bup) declara por sí mismo este repositorio para los builds que ejecuta. **Versión**: el SDK está publicado en la versión 0.7.1 en gluonify-sea, con `gluonify-plasma-quarkus` y `gluonify-plasma-testing`; `gluonify.sdk.version` vale `0.7.1` en `pom.xml`, sin nada que instalar localmente. Desde la 0.7.1, cada módulo cliente lleva un marcador (`META-INF/gluonify/services/crystallon` en `gluonify-plasma-crystallon-client`) del que el builder deduce `uses`. Con Gradle: los mismos dos artefactos (`implementation` y `testImplementation`), sin más repositorios que Maven Central y gluonify-sea.

**2. Nada que declarar.** Construida por Bup, la aplicación recibe `?crystallon` en sus `uses` sin escribirlo: Bup lee el marcador de `gluonify-plasma-crystallon-client` en el classpath de ejecución (SDK 0.7.1 y posteriores) y añade el servicio como opcional; [`gluonify.json`](gluonify.json) no lo menciona. Desplegada a mano (`PUT /apps`: sin build, luego sin deducción), la especificación lo declara: `crystallon` en los `uses` de [`deploy/appspec.json`](deploy/appspec.json). En ambos casos la plataforma **abre la red** hacia Crystallon, y solo hacia él, y proporciona su dirección y el token propio de la aplicación. **No hay propiedad, URL ni token en este repositorio**: el productor de `gluonify-plasma-quarkus` entrega al código un `CrystallonClient` (el mapper Jackson 3 de la aplicación, la dirección y el token encontrados por el SDK). [`Documents`](src/main/java/io/gluonify/source/documents/Documents.java) solo pregunta si Crystallon está disponible (`GluonifyServices.optional("crystallon")`) e inyecta el cliente como `Instance<CrystallonClient>`. **Sin ello la función está apagada y nada más cambia**: el endpoint responde 404 y dice por qué, `GET /api/platform` indica `"documents": false`, la interfaz oculta la sección, la aplicación arranca y todas las demás pruebas pasan sin Crystallon. El lugar donde se ejecuta la aplicación decide cómo el SDK encuentra Crystallon:

| Dónde | Dirección | Token |
|---|---|---|
| en la plataforma, `crystallon` en `uses` (deducido por Bup, o declarado en la especificación) | proporcionada (`GLUONIFY_SERVICE_CRYSTALLON_URL`) | el token propio de la aplicación (archivo renovado por el agente del nodo, releído en cada llamada) |
| su equipo, tras `gluonify login <zona>` | la dirección pública que publica la zona | su token de desarrollador, renovado solo |
| pruebas | el Crystallon simulado de `GluonifyTestServices.start()` | el token de prueba |
| en otro sitio | ninguna: la función está apagada | |

**3. El token y el rol.** Crystallon exige un token de Charm de audiencia `gluonify-crystallon`; el rol **`gluonify:crystallon:render`** basta para renderizar y validar (`convert` y `templates` no hacen falta para producir documentos). Por defecto la aplicación llama a Crystallon **con su propio token** (token de Charm, sujeto `app:<nombre>`, audiencia `gluonify-crystallon`, rol `gluonify:crystallon:render`), que nadie emite a mano: con `crystallon` en `uses` (deducido o declarado), el plano de control (Higgs) hace que Charm lo emita, y el agente del nodo (Strange) lo escribe en `/run/gluonify-tokens/crystallon.token` (solo lectura dentro del sandbox de la instancia) y lo renueva antes de que caduque (900 s por defecto). El SDK relee ese archivo **en cada llamada**: una renovación no exige reiniciar, y la llamada funciona también fuera de una petición (temporizador, webhook, cola de mensajes). El token que envía a `/api/documents/invoice` solo tiene que abrir esta API (`source:read`). Conviene saber:

- el token de la aplicación lleva la organización de la aplicación (claim `org`), o la organización por defecto de Crystallon (`default`) cuando no tiene ninguna: la plantilla `Gluonify invoice` debe existir allí, y es allí donde el builder la instala (paso 4);
- cuando Crystallon rechaza el token de la aplicación, la respuesta es un 502 y no un 403: quien llama no puede arreglarlo, el despliegue sí (construir con Bup y el SDK 0.7.1 o posterior, que lo deduce, o declarar `crystallon` en los `uses` de la especificación);
- con `mvn quarkus:dev` esta API no pide token, pero Crystallon sí: ejecute una vez `gluonify login gluonify.lan` (o su zona), en su navegador, y el SDK usa su token de desarrollador y la dirección pública de Crystallon: ninguna variable que definir.

**Reenviar el token de quien llama (opcional).** Si Crystallon debe ver a la **persona** que llama a la aplicación (sus roles, su organización) en lugar de la aplicación, sustituya el cliente por defecto por uno propio: un bean del mismo tipo prevalece sobre el predeterminado. El token que envía la persona debe entonces llevar, además de la audiencia y el rol de esta aplicación (`gluonify-source`, `source:read`), la audiencia `gluonify-crystallon` y el rol `gluonify:crystallon:render` (Charm pone varias audiencias en un token, 8 como máximo; el prefijo `gluonify:` está reservado a la plataforma, así que lo emite el administrador), y una llamada hecha fuera de una petición no tiene token que reenviar. No es el comportamiento por defecto y este repositorio no lo contiene:

```java
@Produces @Singleton   // a bean of your own replaces the default one
CrystallonClient crystallon(ObjectMapper mapper, JsonWebToken jwt) {          // tools.jackson.databind.ObjectMapper, io.quarkus.oidc / MicroProfile JWT
    return CrystallonClient.builder().endpoint(GluonifyServices.endpoint("crystallon"))
            .tokens(CrystallonTokens.forwarding(jwt::getRawToken))              // outside a request the supplier must return null
            .json(Jackson3Codec.of(mapper)).build();
}
```

```bash
curl -s -X POST "$CHARM_URL/v1/tokens" -H "Authorization: Bearer $GLUONIFY_CHARM_ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"sub":"ada","audiences":["gluonify-source","gluonify-crystallon"],"ttlSeconds":3600,"roles":["source:read","gluonify:crystallon:render"]}'
```

**4. La plantilla y el código generado.** El ejemplo llama a la plantilla de Crystallon llamada **`Gluonify invoice`**, y **la plantilla vive en este repositorio**: [`crystallon/templates/gluonify-invoice.json`](crystallon/templates/gluonify-invoice.json) es la plantilla exactamente como la escribe el botón *Exportar* del editor de Crystallon (la plantilla predefinida `invoice`, con su `name` exacto y su `description` en la raíz). **Nada que crear a mano**: cuando el builder (Bup) despliega una aplicación que usa Crystallon, instala cada `crystallon/templates/*.json` en la organización en la que genera el token de la aplicación (`PUT /v1/templates/by-name/{name}` de Crystallon): creada la primera vez, sin cambios si es idéntica, guardada como nueva versión cuando el contenido cambia pero los datos esperados siguen siendo los mismos, y **nunca sobrescrita** cuando esperaría otros datos (`differs`). El build informa de cada plantilla en su campo `templates` y en su registro; un fallo en este paso es un aviso, nunca un despliegue fallido. Sin el builder (despliegue a mano, `mvn quarkus:dev` contra un clúster), cree la plantilla en la consola de Crystallon (`/crystallon/`; *Nueva plantilla* llamada exactamente `Gluonify invoice`), *Importe* el archivo y guarde. **La consola sigue siendo el lugar donde se edita**: cambie allí la plantilla, *Expórtela*, sustituya el archivo y haga commit. Crystallon **genera** entonces **el código Java** de una plantilla: records que describen exactamente los datos que lee, y una clase que la llama. Abra la plantilla y su pestaña **Código** (pide guardar antes: el código se genera a partir de la versión guardada), ponga *Paquete Java* a `io.gluonify.source.documents.generated` y *Nombre de la clase* a `GluonifyInvoice`, deje sin marcar *Fijar esta versión* (el código genera entonces la versión actual de la plantilla), y luego *Descargar (zip)* o *Copiar*. Los tres archivos de [`documents/generated/`](src/main/java/io/gluonify/source/documents/generated) son exactamente esa salida (solo se añadió un comentario de cabecera), generada a partir de la versión 0 de la plantilla (la que el builder crea a partir del archivo):

- `GluonifyInvoicePayload`: un record por objeto de los datos (`Sender`, `Recipient`, `Invoice`, `Item`, `Payment`); el Javadoc de cada componente indica si es obligatorio;
- `GluonifyInvoiceDocument`: la fachada, con `render(payload)`, `render(payload, RenderOptions)` y `validate(payload)`; llama a la plantilla **por su nombre** (el mismo en cada entorno) y lleva las constantes `TEMPLATE_NAME`, `TEMPLATE_VERSION` y `SCHEMA_SHA256`;
- `package-info`.

**Para regenerarlos** (la plantilla cambió y ahora espera otros datos: su `SCHEMA_SHA256` ya no es el de la fachada), genere de nuevo en la pestaña Código con los mismos dos campos y sustituya los tres archivos; no los edite nunca a mano. El compilador muestra entonces qué adaptar en [`InvoiceMapping`](src/main/java/io/gluonify/source/documents/InvoiceMapping.java), la única clase que conoce la forma de los datos. Sin la consola, el mismo código viene de la API (rol `gluonify:crystallon:templates`, no `render`): `GET /v1/templates/<id>/code?lang=java&package=io.gluonify.source.documents.generated&name=GluonifyInvoice`. Haga commit a la vez de la plantilla exportada y del código regenerado, para que describan siempre los mismos datos. Una plantilla ya presente en Crystallon que espera otros datos queda intacta para el builder (`differs`): modifíquela en la consola, o bórrela para que el siguiente despliegue la cree de nuevo.

**Estado.** Verificado el 2026-10-11 en el clúster de desarrollo: la aplicación (SDK 0.7.1) generó la factura 2026-0042 a través del Crystallon real con su propio token (200, `application/pdf`, PDF/A-1B, una página), con una plantilla creada a mano en la consola. **Todavía no** verificado por un build real: la instalación automática de la plantilla por el builder (cubierta por pruebas contra un Crystallon falso en gluonify-bup y gluonify-crystallon).

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
| Crystallon rechaza el token de la aplicación (falta el rol `gluonify:crystallon:render`, caducado, `crystallon` ausente de `uses` en el despliegue) | 502 (quien llama no puede arreglarlo, el despliegue sí) |
| la plantilla no puede generar estos datos (Crystallon 400 / 422) | 422, con `code` y `path` |
| no hay plantilla de ese nombre para la organización del token (Crystallon 404), Crystallon falla (5XX), respuesta inesperada, inaccesible, token de la aplicación imposible de obtener | 502 |
| Crystallon demasiado lento (30 s) | 504 |
| Crystallon no disponible | 404 |

**En la interfaz**: un botón *Descargar una factura de ejemplo* en la sección *Documentos* (solo se muestra cuando `GET /api/platform` indica `"documents": true`) envía el token de la persona que ha iniciado sesión (solo tiene que abrir esta API) y una factura de ejemplo. [`DocumentsResourceTest`](src/test/java/io/gluonify/source/documents/DocumentsResourceTest.java) prueba todo contra el Crystallon simulado del SDK (`GluonifyTestServices.start()` y `close()` en [`GluonifyTestServicesResource`](src/test/java/io/gluonify/source/documents/GluonifyTestServicesResource.java), nada que configurar): la ruta llamada (`/v1/templates/by-name/Gluonify%20invoice/render`), la llamada fuera de una petición con el token de la aplicación, la carga útil y cada error anterior.

### Llamar a otra aplicación

Declárela en `"uses"`: la plataforma proporciona `GLUONIFY_SERVICE_<APP>_URL` **y abre la red** hacia ella (sin `uses`, la aplicación no la alcanza). Nunca escriba la dirección en el código: lea la variable (véase `ping` en [`PlatformResource`](src/main/java/io/gluonify/source/platform/PlatformResource.java)).

### Sitios estáticos y dominios

- **Field** sirve un sitio estático (ZIP) en su dominio; nada que programar en este servicio.
- **Su dominio**: `PUT /domains/<nombre>` en la API de Higgs (véase [`deploy/domain.json`](deploy/domain.json)) enruta una ruta hacia la aplicación y aplica **políticas**: token de Charm (audiencia, roles), límites de tasa, CORS, listas de direcciones. El certificado HTTPS se obtiene automáticamente.

## 8. Construir

```bash
mvn test                       # pruebas Java (67)
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

**Los servicios que declara el builder.** El builder envía `uses` y `gdownDatabase` a `PUT /apps/<nombre>`. `uses` se **deduce en el build**: cada módulo cliente del SDK lleva un marcador `META-INF/gluonify/services/<servicio>`; Bup lee los marcadores de los jars del classpath de ejecución (solo el grupo `io.gluonify.plasma`) y los añade como servicios opcionales (aquí `?crystallon`, de `gluonify-plasma-crystallon-client`). Los fusiona con los servicios declarados en la petición de build o, si la petición no los trae, en el [`gluonify.json`](gluonify.json) en la raíz del repositorio (la petición manda; una lista vacía en la petición significa «ningún servicio declarado», los deducidos se añaden igualmente). **Declare en `uses` solo lo que el SDK no detecta**: una llamada HTTP directa a otra aplicación, Charm para OIDC, Gdown por HTTP. Por eso el archivo de este repositorio es `{"uses": ["charm", "gdown"]}`: con `?crystallon` deducido, los mismos servicios que [`deploy/appspec.json`](deploy/appspec.json), lo que comprueba `DeploySpecsTest`. Sin el archivo, un build desplegaría la aplicación sin red hacia Charm y Gdown, porque `PUT /apps/<nombre>` **sustituye toda la especificación**. Añada `"gdownDatabase": true` a `gluonify.json` para la base de datos dedicada; un `?` inicial (`"?orders"`) marca un servicio opcional. Una aplicación que solo alcanza servicios a través del SDK no necesita `gluonify.json`.

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
| `uses` | servicios alcanzables, por nombre de aplicación (`charm`, `gdown`, `crystallon`…): `GLUONIFY_SERVICE_<APP>_URL` proporcionada, red abierta; para `crystallon`, también el token de la aplicación (`GLUONIFY_CRYSTALLON_TOKEN_FILE`). Con el builder: deducidos de los módulos del SDK, más lo que declaren `gluonify.json` o la petición de build |
| `distributed` | `["std"]` para `/distributed/std` |
| `env` | variables **no secretas**; `${NAME}` y `${NAME:-default}` se resuelven |
| `top` | `true`: el plano de control asigna a la aplicación su propio espacio de caja fuerte `<uuid>.app`, cuyas claves llegan como `APP_<CLAVE>` |
| `gdownDatabase` | `true`: una base Gdown dedicada para la aplicación (grupo Raft propio, cuenta confinada); `GLUONIFY_GDOWN_DATABASE`, `GLUONIFY_GDOWN_USER`, `GLUONIFY_GDOWN_PASSWORD` y `GLUONIFY_SERVICE_GDOWN_URL` se proporcionan; nunca eliminada con la aplicación (ver [`deploy/appspec-graph.json`](deploy/appspec-graph.json)) |
| `topNamespace` | alternativa: un espacio de caja fuerte existente, por su nombre; sus claves llegan **sin prefijo** (sin `APP_`) |
| `internal` | `true`: ninguna ruta pública (servicio interno) |

Actualizar: el mismo `PUT` (o un nuevo build); las réplicas se sustituyen **una a una**. Reiniciar sin cambios: `POST /apps/<nombre>/redeploy`. Registros: `GET /apps/<nombre>/logs`. Métricas: `/q/metrics`.

## 10. Probar

```bash
mvn test                                 # 67 pruebas Java
cd src/main/webui && npm install && npm test   # 40 pruebas de interfaz
```

| Prueba | Pruebas | Qué verifica |
|---|---|---|
| `NotesResourceTest` | 7 | 401 sin token, 403 sin el rol adecuado, ciclo de vida completo, validación 400, OpenAPI, salud, métricas |
| `AuthConfigResourceTest` / `AuthConfigConfiguredTest` | 2 + 1 | `GET /api/auth-config` es público y vacío sin cliente; las direcciones públicas de Charm se deducen del emisor, nunca de la dirección interna `GLUONIFY_SERVICE_CHARM_URL` |
| `FileNoteStoreTest` | 6 | archivos nuevos nunca renombrados, orden, dos réplicas sobre la misma carpeta, archivos ilegibles ignorados, identificadores que nunca son rutas |
| `GraphNoteStoreTest` | 4 | contra un Gdown falso: ruta, autenticación, instrucción **parametrizada**, lectura de las filas, fallo |
| `WebhookResourceTest` / `WebhookClosedTest` | 4 + 1 | clave, confirmación, **idempotencia**, rechazos; cerrado sin clave |
| `PlatformResourceTest` | 4 | variables de la plataforma leídas, **ningún secreto devuelto**, llamada de servicio a servicio limitada a los servicios declarados |
| `ConformityTest` | 4 | las normas del builder, en su máquina |
| `EventLedgerTest` | 4 | registros de idempotencia: un único ganador por evento (memoria, archivos con dos réplicas, restricción de unicidad del grafo, error del grafo distinto de un duplicado propagado) |
| `DocumentsResourceTest` | 13 | contra el **Crystallon simulado del SDK** (`GluonifyTestServices`): PDF devuelto, ruta llamada (`/v1/templates/by-name/Gluonify%20invoice/render`), **token de la aplicación** (también fuera de una petición), carga útil enviada, correspondencia de 422 / 502, validación propia (400), roles, OpenAPI |
| `DocumentsDisabledTest` | 3 | Crystallon no disponible: 404 con el motivo, `"documents": false`, el resto no se ve afectado |
| `InvoiceMappingTest` / `DocumentErrorsTest` | 3 + 7 | su factura → la carga útil de la plantilla (importes, valores por defecto, nombre de archivo seguro); cada fallo de Crystallon → un estado |
| `DocumentsReflectionTest` | 1 | salvaguarda nativa: cada record de los datos generados está declarado para la reflexión |
| `CrystallonTemplateFileTest` | 1 | la plantilla Crystallon del repositorio (`crystallon/templates/gluonify-invoice.json`): JSON válido, el nombre exacto que llama el código generado, los mismos campos de primer nivel que los datos generados (la huella `SCHEMA_SHA256` solo la calcula Crystallon) |
| `DeploySpecsTest` | 2 | las especificaciones de `deploy/` usan los nombres actuales (`uses`, `SOURCE_STORE`); `gluonify.json` (leído por el builder) solo declara lo que el SDK no detecta (`charm`, `gdown`) y, con `crystallon` deducido (marcador en el classpath), da los mismos `uses` que `deploy/appspec.json` |

Una prueba de API se copia de `NotesResourceTest`; una prueba de almacenamiento, de `FileNoteStoreTest` (sin arrancar Quarkus: rápida).

## 11. Trampas conocidas

- **Nativo**: **no** cree un `HttpClient`, un `Random` ni un `SecureRandom` en un campo `static` (el estado quedaría congelado en la compilación, no en la ejecución): créelos en el primer uso (véase `GraphNoteStore.client()`). Lea el JSON de terceros como árbol (`JsonNode`) en lugar de en clases; un tipo (de)serializado por Jackson fuera de una firma REST debe llevar `@RegisterForReflection`. **Un error que solo aparece en nativo no se ve en `mvn test`**: ejecute el ejecutable nativo antes de entregar.
- **Jackson 3**: el paquete es `tools.jackson.databind`, no `com.fasterxml.jackson.databind`.
- **Ninguna dirección, ningún puerto, ninguna contraseña en el código**: todo viene del entorno.
- **Archivos distribuidos**: véanse las reglas del [§7](#archivos-distribuidos-sourcestorefiles) (nunca reescribir, ~3 s de retraso).
- **Webhooks**: responder rápido con 2XX, ser idempotente, proteger con una clave.
- **Código generado**: los archivos de `documents/generated/` vienen de la pestaña *Código* de Crystallon: regenérelos, no los edite nunca a mano; y declare cada record de un payload generado en `DocumentsReflection` (un fallo que solo aparece en nativo).
- **Una identidad de desarrollo no tiene nada que hacer en producción**: conserve `@IfBuildProfile("dev")`.
- **Quinoa y las direcciones de API**: añada todo nuevo prefijo de API a `quarkus.quinoa.ignored-path-prefixes`.
- **Bajo emulación x86_64** (Docker en Apple Silicon): construya con `--build-arg MAVEN_OPTS=-Djdk.lang.Process.launchMechanism=VFORK`.

## 12. Solución de problemas

| Síntoma | Causa probable |
|---|---|
| 401 en producción | sin token, token caducado, o **audiencia** distinta de `GLUONIFY_SOURCE_OIDC_AUDIENCE`; o `GLUONIFY_SOURCE_OIDC_ISSUER` no coincide con el `iss` del token |
| 403 | token válido pero sin el rol (`source:read` / `source:write`) |
| No hay botón *Iniciar sesión*, la página nombra `GLUONIFY_SOURCE_OIDC_CLIENT_ID` | falta el identificador de cliente, o se desconoce la dirección pública de Charm (`GLUONIFY_SOURCE_OIDC_ISSUER` en `any` y sin `GLUONIFY_SOURCE_CHARM_PUBLIC_URL`) |
| Charm rechaza el inicio de sesión (`redirect_uri`, `invalid_target`) | la redirección del cliente no es exactamente la dirección de la interfaz (barra final incluida), o sus audiencias no incluyen `gluonify-source` |
| Sesión iniciada, pero 403 en las notas | la cuenta no tiene `source:read` / `source:write` (dar los roles en Charm y volver a iniciar sesión: los roles se leen al iniciar sesión) |
| La aplicación no recibe tráfico | `/q/health/ready` no responde 200 (¿almacenamiento inaccesible?): `GET /apps/<nombre>` muestra las instancias listas |
| `store=gdown`: «source.graph.url is empty» | la aplicación no está desplegada con `"uses": ["gdown"]` |
| Gdown: 401 o «Unsupported property value type» | cuenta incorrecta; o un **mapa** almacenado como propiedad (véase §7) |
| Una nota creada no aparece enseguida (archivos) | caché de lista de ~3 s entre réplicas: refrescar |
| Se rechaza el build del repositorio | una regla `R-…`: el mensaje la nombra; `mvn test` (`ConformityTest`) la muestra en su máquina |
| `mvn package` no encuentra Node | acceso a la red para descargarlo, o `-Dquarkus.quinoa=false` para construir sin la interfaz |
| El nativo falla al arrancar pero no en JVM | un estado congelado en la compilación (`static`) o una reflexión que falta (§11) |
| `POST /api/documents/invoice`: 404 «documents are off» | Crystallon no está disponible: la aplicación no está desplegada con `"uses": ["crystallon"]` (por el builder: SDK anterior a la 0.7.1, o deducción desactivada) o, en su equipo, falta `gluonify login <zona>` |
| Documentos: 502 «did not accept the token of the application» | la aplicación no está desplegada con `"uses": ["crystallon"]`, la plataforma aún no emite tokens de aplicación, o el archivo del token falta, está vacío o caducado; en su equipo, vuelva a ejecutar `gluonify login <zona>` |
| Documentos: 502 «does not know the template» | no hay plantilla llamada `Gluonify invoice` en la organización del token (token de la aplicación: la organización de la aplicación, o la de por defecto de Crystallon): lea `templates` en el build del builder (`GET /v1/builds/<id>`: `failed` con su motivo; ausente cuando la aplicación no usa Crystallon o falta el archivo); sin el builder, importe `crystallon/templates/gluonify-invoice.json` en la consola de Crystallon |
| Builder: plantilla `Gluonify invoice` señalada `differs` | la plantilla de Crystallon espera otros datos que el archivo: no se ha escrito nada; modifíquela en la consola (o bórrela), expórtela, regenere el código y haga commit de ambos |
| Documentos: 422 con `code` y `path` | la plantilla no puede generar estos datos: `path` es el lugar del problema en la plantilla |
| Los documentos funcionan en JVM pero fallan en la primera llamada del ejecutable nativo | falta un record de los datos generados en `DocumentsReflection` |

---

*Gluonify es una plataforma minimalista para aplicaciones Quarkus nativas: [gluonify.cloud](https://gluonify.cloud) (presentación) y [gluonify.io](https://gluonify.io) (técnica). Este repositorio es deliberadamente pequeño: se lee en una hora.*
