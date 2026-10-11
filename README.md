# gluonify-source

**English** · [Français](README.fr.md) · [Español](README.es.md) · [Italiano](README.it.md) · [Deutsch](README.de.md)

> **Gradle variant.** The same service, built with **Gradle** (Kotlin DSL) instead of Maven, lives in [`gluonify-source-gradle`](https://github.com/gluonify/gluonify-source-gradle): same code, same tests, same platform rules.

**The starting point for a Quarkus 4 service on [Gluonify](https://gluonify.cloud).** A small, complete service (some "notes") that shows, with running code and tests, how to:

1. build a **REST service** (validation, roles, OpenAPI);
2. build a **web interface** with [Quinoa](https://quarkus.io/extensions/io.quarkiverse.quinoa/quarkus-quinoa/) (Vue 3, served by the same executable);
3. **integrate with Gluonify services**: configuration and secrets (Top), identity tokens (Charm), data (Gdown) or distributed files, other applications, webhooks received from Photon, PDF documents (Crystallon), static sites (Field), domains and policies (Higgs);
4. **build it as a native executable** and **deploy it**.

It is aimed at a developer or an **AI assistant** starting a service from scratch: every choice is explained here, every configuration line is commented in the code, and [`CLAUDE.md`](CLAUDE.md) summarizes the rules for an AI.

> Gluonify runs **native Quarkus applications** (a single executable file, no JVM) on Debian machines. You give access to your code on Git; the platform builds it, deploys it, secures it and runs it.

## Table of contents
1. [Getting started in 5 minutes](#1-getting-started-in-5-minutes)
2. [The project map](#2-the-project-map)
3. [Renaming the project](#3-renaming-the-project)
4. [Building a REST service](#4-building-a-rest-service)
5. [Building a UI with Quinoa](#5-building-a-ui-with-quinoa)
6. [Authentication: Charm tokens](#6-authentication-charm-tokens)
7. [Integrating with Gluonify services](#7-integrating-with-gluonify-services)
8. [Building](#8-building)
9. [Deploying](#9-deploying)
10. [Testing](#10-testing)
11. [Known pitfalls](#11-known-pitfalls)
12. [Troubleshooting](#12-troubleshooting)

---

## 1. Getting started in 5 minutes

**Prerequisites**: Java 25 (`JAVA_HOME`), Maven 3.9+. Node.js is not needed: Quinoa downloads its own (v24.3.0) on the first build. Docker is only useful for the native executable.

```bash
git clone https://github.com/gluonify/gluonify-source.git
cd gluonify-source
mvn quarkus:dev
```

Open <http://localhost:8080>: the interface (add, list, delete notes). Also:

| Address | Content |
|---|---|
| `/` | the Vue interface (shows the Source logo on a transparent background, "Source · role" and the particle line in the header, "© year Gluonify" at the bottom, `BrandLogo.vue` and `public/favicon.svg`: replace both with your own logo) |
| `/api/notes` | the REST API (JSON) |
| `/api/documents/invoice` | `POST`: an invoice as a PDF, produced by Crystallon when it is configured (see [§7](#documents-crystallon)) |
| `/q/swagger-ui` | the interactive API documentation |
| `/q/openapi` | the OpenAPI contract (JSON or YAML) |
| `/q/health/ready` · `/q/health/live` | health: "ready" and "alive" (see [§7](#7-integrating-with-gluonify-services)) |
| `/q/metrics` | Prometheus metrics |

In **development**, no token is required: a "dev" identity (with all roles) exists only in this profile (`@IfBuildProfile("dev")`, absent from the production executable). Notes are **in memory** (`source.store=memory`): they disappear on shutdown.

```bash
curl -s localhost:8080/api/notes -H 'Content-Type: application/json' -d '{"title":"Courses","body":"du pain"}'
curl -s localhost:8080/api/notes
```

## 2. The project map

```
pom.xml                         STANDALONE Maven project (Quarkus 4.0.0.Beta1, Java 25): no Gluonify parent
gluonify.json                   read by Gluonify's builder (Bup): the services to declare when it deploys (`uses`), as in deploy/appspec.json
Dockerfile.build                builds the native executable in Docker
src/main/resources/
  application.properties        ALL the configuration, commented line by line
src/main/java/io/gluonify/source/
  SourceConfig.java             the application configuration (prefix "source.")
  notes/
    Note.java, NewNote.java     the model (records) and the (validated) creation body
    NotesResource.java          ← THE MODEL OF A REST RESOURCE (to copy)
    NoteStore.java              the storage interface; three implementations:
    MemoryNoteStore.java          in memory (development)
    FileNoteStore.java            files in /distributed/std (durable, shared by replicas)
    GraphNoteStore.java           Gdown, the platform's graph database
    NoteStores.java             picks the implementation (source.store): the place to plug in yours
    StoreHealth.java            the "ready" condition (/q/health/ready)
  platform/
    PlatformResource.java       variables provided by the platform; calling another application
    WebhookResource.java        receiving Photon deliveries
  documents/                    PDF documents with Crystallon (optional)
    DocumentsResource.java      POST /api/documents/invoice: your invoice in, a PDF out
    InvoiceRequest.java         the invoice of YOUR domain (validated body)
    InvoiceMapping.java         your invoice -> the payload of the template (the only class that knows its shape)
    CrystallonClients.java      builds the SDK client from the configuration (no address = feature off) and picks the token
    TokenMode.java              which token Crystallon receives: the caller's (forwarding) or the application's own (platform)
    ForwardedToken.java         the caller's bearer token, forwarded to Crystallon (mode forwarding)
    DocumentErrors.java         Crystallon failure -> 403 / 422 / 502 / 504
    DocumentsReflection.java    native image: the records of the generated payload
    generated/                  code generated by the "Code" tab of Crystallon (do not edit by hand)
  security/DevAuthentication.java   development identity (dev profile only)
src/main/webui/                 the Vue 3 interface (Vite + vitest), built by Quinoa
src/test/java/…                 86 Java tests; src/main/webui/src/App.test.js: 33 interface tests
deploy/                         examples: AppSpec, domain, Photon webhook, Gdown database
scripts/rename.py               renames the project
```

## 3. Renaming the project

Your service is not called `gluonify-source`. One command changes the Maven coordinates, the Java package (folders included), the executable name, the OpenAPI title and the expected token **audience**:

```bash
python3 scripts/rename.py com.acme shop-api com.acme.shop
mvn test
```

(`groupId` `com.acme`, `artifactId` `shop-api`, Java package `com.acme.shop`. The script is safe to run again.) The artifact name also becomes the application name on Gluonify; choose it like a DNS name: lowercase letters, digits, hyphens.

## 4. Building a REST service

Read [`NotesResource.java`](src/main/java/io/gluonify/source/notes/NotesResource.java): it is the model. To add **your** resource, for example orders:

1. **The model**: a Java `record` (immutable, read and written by Jackson, described by OpenAPI).
   ```java
   public record Order(String id, String customer, int quantity) {}
   public record NewOrder(@NotBlank String customer, @Min(1) int quantity) {}   // validated
   ```
2. **The resource**: a class with `@Path`, `@GET`/`@POST`… methods, roles with `@RolesAllowed`, validation with `@Valid`.
   ```java
   @Path("/api/orders")
   @Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
   public class OrdersResource {
       @POST @RolesAllowed("shop:write")
       public Response create(@Valid NewOrder in) { … return Response.created(uri).entity(order).build(); }
   }
   ```
3. **The documentation**: `@Operation(summary = "…")` and `@Tag(name = "…")` (microprofile-openapi): they appear in `/q/swagger-ui`. The contract is regenerated at every startup.
4. **The tests**: copy [`NotesResourceTest`](src/test/java/io/gluonify/source/NotesResourceTest.java). `@TestSecurity(user = "ada", roles = {"shop:write"})` sets a test's identity; without the annotation, it is a call without a token (expected: 401).
5. **HTTP codes**: 201 + `Location` on creation, 204 with no body, 400 for an invalid body (Hibernate Validator), 401 without a token, 403 without the right role, 404 for an unknown identifier. Errors return `{"error": "…"}`.

**Storage** sits behind the [`NoteStore`](src/main/java/io/gluonify/source/notes/NoteStore.java) interface: the resource does not know where the data lives. For your own storage (another database, another folder), implement the interface and add a case in [`NoteStores`](src/main/java/io/gluonify/source/notes/NoteStores.java).

## 5. Building a UI with Quinoa

[Quinoa](https://quarkus.io/extensions/io.quarkiverse.quinoa/quarkus-quinoa/) builds a web project (here Vue 3 + Vite, in `src/main/webui`) and serves it **from the same executable** as the API: a single file to deploy, a single origin (no CORS).

- **Develop**: `mvn quarkus:dev` also starts the Vite server (port 5173, hot reload of the interface); Quarkus serves the API. Open <http://localhost:8080>.
- **Call the API**: relative addresses (`fetch('/api/notes')`), see [`src/api.js`](src/main/webui/src/api.js). The Charm token is pasted by the user and kept for the tab (`sessionStorage`).
- **Interface routes**: `quarkus.quinoa.enable-spa-routing=true` returns `index.html` for an unknown address (useful with vue-router). But **an unknown API address must not return the interface**: `quarkus.quinoa.ignored-path-prefixes=/api,/hooks,/q` (already set). If you add an API prefix, add it here.
- **Build**: `mvn package` runs `npm install` then `npm run build`; `dist/` is embedded in the executable. Without the interface: `-Dquarkus.quinoa=false`.
- **Languages (i18n)**: the interface is available in English (default), French, Spanish, Italian and German. No text is written in the components: every text is a key in [`src/i18n/`](src/main/webui/src/i18n) (`en.js`, `fr.js`, `es.js`, `it.js`, `de.js`) read through `t('key', { param })` (a tiny helper in `index.js`, no dependency). The selector in the header remembers the choice (`localStorage`); without a choice, the browser language is used if it is one of the five, otherwise English; `<html lang>` follows. To add a language: copy `en.js` to `<code>.js` and translate the values (same keys), then import it in `index.js` (`MESSAGES` and `LANGUAGES`). Error messages returned by the API are displayed as received; the 401 and 403 messages are translated.
- **Test the interface**: `cd src/main/webui && npm install && npm test` (vitest + jsdom, mocked `fetch`: see `App.test.js`). With `quarkus.quinoa.run-tests=true`, `mvn package` runs them too.
- **Caching**: files in `dist/assets` have a hash in their name (immutable); `index.html` is served with `Cache-Control: no-cache` (`ui-entry` setting).

Another technology (React, Svelte…): replace the contents of `src/main/webui`; Quinoa only requires a `package.json` with `dev` and `build` scripts and an output folder (`quarkus.quinoa.build-dir`).

## 6. Authentication: Charm tokens

The API is **closed without a token**. It expects a bearer token (JWT) issued by **gluonify-charm**, Gluonify's identity provider (OpenID Connect, ES256):

```
Authorization: Bearer <token>
```

The service never **creates** tokens: it **verifies** them (mode `quarkus.oidc.application-type=service`). What it checks:

| Check | Setting | Value |
|---|---|---|
| Signature | Charm's keys, read at `${GLUONIFY_SERVICE_CHARM_URL}/realms/gluonify` | `GLUONIFY_SERVICE_CHARM_URL` is provided by the platform if the application is deployed with `"uses": ["charm"]` |
| Issuer (`iss`) | `quarkus.oidc.token.issuer` | `GLUONIFY_ZZZNONE_OIDC_ISSUER` = `https://id.<your zone>/realms/gluonify` (stable: it is not the address of the Charm instance) |
| Audience (`aud`) | `quarkus.oidc.token.audience` | `GLUONIFY_ZZZNONE_OIDC_AUDIENCE`, by default the project name (`gluonify-source`) |
| Expiration | automatic | short-lived tokens |
| Roles | `roles` claim | `source:read` (read), `source:write` (write): see `@RolesAllowed`; the `gluonify:` prefix is reserved for the platform (Charm refuses it for application roles) |

**Getting a test token** (the platform administrator, with Charm's administration token):

```bash
curl -s -X POST "$CHARM_URL/v1/tokens" -H "Authorization: Bearer $GLUONIFY_CHARM_ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"sub":"ada","audience":"gluonify-source","ttlSeconds":3600,"roles":["source:read","source:write"]}'
# -> {"token": "eyJ…"}
curl -s https://gluonify-source.<zone>/api/notes -H "Authorization: Bearer eyJ…"
```

A different audience value (`"audience": "other"`) gives **401**; a valid token without the right role gives **403**.

**Locally**, the `dev` profile provides the "dev" identity (see [`DevAuthentication`](src/main/java/io/gluonify/source/security/DevAuthentication.java)); **in tests**, `@TestSecurity`. Never an identity like this in production: it is compiled only in the `dev` profile.

## 7. Integrating with Gluonify services

An application on Gluonify is **isolated**: its own account, its own network, a reduced file system. It only reaches what it **declares** (`"uses"`) and receives its configuration **through the environment**. Here is each service, with its place in the code.

| Service | What it offers | How the application uses it | In this repository |
|---|---|---|---|
| **Top** (vault) | per-application configuration and secrets | with `"top": true`, the keys of your application's own `<uuid>.app` space arrive as `APP_<KEY>`; `${app.gluonify.gdown.password}` in `application.properties` | `source.graph.password`, `source.webhook.key` |
| **Charm** (identity) | short-lived JWT tokens | `"uses": ["charm"]` → `GLUONIFY_SERVICE_CHARM_URL`; `quarkus-oidc` verifies tokens | [§6](#6-authentication-charm-tokens) |
| **Gdown** (graph database) | replicated database (Raft), Cypher over HTTP | `"uses": ["gdown"]` → `GLUONIFY_SERVICE_GDOWN_URL`; a local Gdown account | [`GraphNoteStore`](src/main/java/io/gluonify/source/notes/GraphNoteStore.java) |
| **Distributed files** | `/distributed/std`: durable, shared by all replicas, 2 copies | `"distributed": ["std"]` at deployment | [`FileNoteStore`](src/main/java/io/gluonify/source/notes/FileNoteStore.java) |
| **Other applications** | service-to-service calls | `"uses": ["other"]` → `GLUONIFY_SERVICE_OTHER_URL` | [`PlatformResource.ping`](src/main/java/io/gluonify/source/platform/PlatformResource.java) |
| **Photon** (API gateway) | signed webhooks, XML / SOAP / form → JSON conversion, replay | Photon delivers to `…/hooks/events`; your service acknowledges with 2XX | [`WebhookResource`](src/main/java/io/gluonify/source/platform/WebhookResource.java) |
| **Crystallon** (PDF documents) | invoices, quotes and letters as PDF/A from templates drawn in its editor; Java client of the SDK, generated code | optional: `"uses": ["crystallon"]` → `GLUONIFY_SERVICE_CRYSTALLON_URL` and the application's token file (`GLUONIFY_CRYSTALLON_TOKEN_FILE`); the caller's token is forwarded (default) or the application's own token is used (`source.crystallon.token=platform`), role `gluonify:crystallon:render` | [`DocumentsResource`](src/main/java/io/gluonify/source/documents/DocumentsResource.java), [details](#documents-crystallon) |
| **Field** | static sites (ZIP) served on your domain | nothing to code: a site is uploaded as a ZIP | — |
| **Higgs** (orchestrator) | domains, automatic HTTPS certificates, routes, policies (token, limits, CORS, addresses) | `PUT /domains/<name>` | `deploy/domain.json` |

### Configuration and secrets (Top)

- A **non-secret** value: in the deployment's `"env"` (`deploy/appspec.json`): `"SOURCE_STORE": "files"`. It is stored in the cluster state.
- A **secret** value (password, API key): in the **vault** (Top, the `<uuid>.app` space of your application). The `GLUONIFY_GDOWN_PASSWORD` key arrives as the `APP_GLUONIFY_GDOWN_PASSWORD` variable and is read as `${app.gluonify.gdown.password}`. Ask for that space with `"top": true` in the application spec: only in this control-plane-assigned `<uuid>.app` space do keys get the `APP_` prefix. With a literal `"topNamespace"` (for example `"gluonify-source"`, the only option of the builder), keys arrive **unprefixed**, exactly as named in the vault (`GLUONIFY_GDOWN_PASSWORD` stays `GLUONIFY_GDOWN_PASSWORD`): name them after the properties you read (for example `SOURCE_GRAPH_PASSWORD`). **Never** in the repository: the builder rejects a plaintext password (rule R-SECRET), and `ConformityTest` warns you beforehand.
- **Saving a value restarts nothing**: after changing several keys, trigger **one** redeployment (`POST /apps/<name>/redeploy`); replicas restart one by one, with no downtime if you have two or more.
- Variables the platform **always** adds: `QUARKUS_HTTP_PORT` and `QUARKUS_HTTP_HOST`, `GLUONIFY_ENVIRONMENT` (the environment: SBX, QUA, PRD…), `GLUONIFY_REPLICA` (replica rank: 1, 2…), `GLUONIFY_SELF_URL` (address of this instance). They are read and displayed by [`PlatformResource`](src/main/java/io/gluonify/source/platform/PlatformResource.java) (`GET /api/platform`, never a secret).

### Health: `/q/health/ready` and `/q/health/live`

The platform only sends traffic to instances whose **`/q/health/ready`** answers 200, and restarts those whose **`/q/health/live`** fails repeatedly. `quarkus-smallrye-health` is **mandatory** (the builder checks it: rule R-SANTE). [`StoreHealth`](src/main/java/io/gluonify/source/notes/StoreHealth.java) makes the instance "ready" only if storage responds. Keep "alive" independent of external services: a Gdown outage must not make the application restart in a loop.

### Data in Gdown (`source.store=gdown`)

**Simplest: a dedicated database.** Deploy with `"gdownDatabase": true` and `SOURCE_STORE=gdown`: the platform creates a database of its own for the application (its own Raft group, a confined account) and provides `GLUONIFY_GDOWN_DATABASE`, `GLUONIFY_GDOWN_USER`, `GLUONIFY_GDOWN_PASSWORD` and the network access (`GLUONIFY_SERVICE_GDOWN_URL`); nothing else to do. The database is **never deleted** with the application.

A ready-to-use spec is in [`deploy/appspec-graph.json`](deploy/appspec-graph.json).

**By hand** (shared database, your own naming):

1. Deploy with `"uses": ["gdown"]` (this is also what **opens the network** to Gdown) and `SOURCE_STORE=gdown`.
2. An administrator creates the database and the application account ([`deploy/gdown-setup.cypher`](deploy/gdown-setup.cypher): `source` database, confined role, `notes` account).
3. Put `GLUONIFY_GDOWN_USER` (= `notes`) and `GLUONIFY_GDOWN_PASSWORD` in the vault.

Gdown's HTTP API: `POST <url>/db/<database>/query` with `{"statement": "…", "parameters": {…}}` and basic authentication; response `{"columns": […], "rows": [[…]], "stats": {…}}`. **Always use parameters** (`$id`, `$title`), never a value pasted into the statement (injection). A **map** cannot be stored as a node property: store scalars, lists of scalars, or the JSON as text. Gdown can also publish named Cypher queries as a REST API (OpenAPI contracts, tokens, limits): see [gluonify.io](https://gluonify.io).

### Distributed files (`source.store=files`)

Deploy with `"distributed": ["std"]`: `/distributed/std` appears, shared by all replicas on all nodes, with 2 copies on the storage nodes. **Rules** (they explain the shape of [`FileNoteStore`](src/main/java/io/gluonify/source/notes/FileNoteStore.java)):

1. **Never rewrite a file or rename a folder that has just been written**: write **new** files under their final name (this is why a note is immutable); deleting is allowed.
2. **A folder listing can lag by ~3 seconds** on another replica: a note created on replica A may take a moment to appear on B.
3. A file becomes visible **when it is closed**; still tolerate an unreadable file.
4. No cross-node locks by default (`distributedLocks` option).
5. **An HTTP request body must be read to the end**, otherwise the reused connection hangs.

### Receiving Photon webhooks

Photon receives your partners' messages (verifies their HMAC signature, converts XML, SOAP or form to JSON) and then **delivers** them to your service. Delivery contract:

- **a 2XX code acknowledges**; any other code (or an outage) causes a **replay** according to the webhook's policy (`retry`), then a move to the dead-letter queue;
- so **answer quickly** and be **idempotent**: "at least once" means the same message may arrive twice. The `X-Gluonify-Event-Id` header is the deduplication key; `X-Gluonify-Delivery-Attempt` counts attempts; `X-Gluonify-Webhook-Id` names the webhook. The memory is **not** shared between replicas: here the identifiers are kept in the storage chosen by `source.store` (memory, one file per event created with `CREATE_NEW` in `/distributed/std/events`, or a node protected by a uniqueness constraint in Gdown), so exactly one replica accepts an event.
- Photon does **not** send a Charm token: protect the entry point with a key that you put in the webhook target (`"headers": {"X-Api-Key": "…"}`, see [`deploy/photon-webhook.json`](deploy/photon-webhook.json)) and in the vault (`WEBHOOK_KEY`). **Without a configured key, the receiver is closed** (404).

[`WebhookResource`](src/main/java/io/gluonify/source/platform/WebhookResource.java) shows it all (key compared in constant time, deduplication, unreadable JSON → 400); replace the body of `receive` with your own processing.

### Documents (Crystallon)

**Crystallon** produces PDF documents (invoices, quotes, letters; PDF/A-1B on request) from a **template** drawn in its graphical editor and kept there, with its history, for each organization. The application sends its data (the *payload*, a JSON) and receives the PDF. It is an **optional** component of the platform, so this code is also the example of an optional integration: [`DocumentsResource`](src/main/java/io/gluonify/source/documents/DocumentsResource.java) offers `POST /api/documents/invoice`, which takes an invoice of the application's own domain ([`InvoiceRequest`](src/main/java/io/gluonify/source/documents/InvoiceRequest.java)) and answers `application/pdf`.

**1. The dependency.** The Java client of Gluonify's SDK is served by the Maven repository of **gluonify-sea** (public read: no account to resolve it). Declare the repository and the dependency in `pom.xml` (already done here):

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

The repository is added to Maven Central, not instead of it. The version is the platform's (the *Code* tab of Crystallon's editor shows the one of your installation). This `pom.xml` writes the address as the property `gluonify.sea.maven.url`, so that a mirror or a local copy can replace it (`mvn verify -Dgluonify.sea.maven.url=file:///…`). Gluonify's builder (Bup) declares this repository by itself for the builds it runs. The client is Java 17 without CDI or Quarkus and uses the application's Jackson (here Jackson 3). With Gradle: `maven("https://images.gluonify.io/maven")` in `repositories` and `implementation("io.gluonify.plasma:gluonify-plasma-crystallon-client:0.6.0")`.

**2. Declaring the use.** `"uses": ["charm", "crystallon"]` in the application spec ([`deploy/appspec.json`](deploy/appspec.json)): the platform provides `GLUONIFY_SERVICE_CRYSTALLON_URL` **and opens the network** to Crystallon, and to it alone; [`CrystallonClients`](src/main/java/io/gluonify/source/documents/CrystallonClients.java) reads it (`source.crystallon.url`) and builds the client. **Without it the feature is off and nothing else changes**: the endpoint answers 404 and says why, `GET /api/platform` says `"documents": false`, the interface hides the section, the application starts and every other test runs without Crystallon.

**3. The token and the role.** Crystallon requires a Charm token of audience `gluonify-crystallon`; the role **`gluonify:crystallon:render`** is enough to render and validate (`convert` and `templates` are not needed to produce documents). The templates are those of the **organization** the token carries. The setting **`source.crystallon.token`** (environment variable `SOURCE_CRYSTALLON_TOKEN`) chooses **which** token Crystallon receives. It names a mode; it never contains a token:

| `source.crystallon.token` | Crystallon sees | Choose it when |
|---|---|---|
| `forwarding` (default) | the **person** who calls this application: their token is forwarded as it is ([`ForwardedToken`](src/main/java/io/gluonify/source/documents/ForwardedToken.java), `CrystallonTokens.forwarding`) | Crystallon must know who is asking (their roles, their organization). A call made outside a request has no token to forward |
| `platform` | the **application**: its own token, which the platform issues to an application deployed with `"uses": ["crystallon"]` (Charm token, subject `app:<name>`, audience `gluonify-crystallon`, role `gluonify:crystallon:render`) | the call is made on behalf of the application (a timer, a webhook, a message queue), or the people who call have no Crystallon token. Nothing to issue by hand |

**Forwarding (default).** The token you send to `/api/documents/invoice` must carry, besides the audience and the role of this application (`gluonify-source`, `source:read`), the audience `gluonify-crystallon` and the role `gluonify:crystallon:render`. Charm puts several audiences in one token (`audiences`, 8 at most); the `gluonify:` prefix is reserved to the platform, so the administrator issues it:

```bash
curl -s -X POST "$CHARM_URL/v1/tokens" -H "Authorization: Bearer $GLUONIFY_CHARM_ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"sub":"ada","audiences":["gluonify-source","gluonify-crystallon"],"ttlSeconds":3600,"roles":["source:read","gluonify:crystallon:render"]}'
```

**Platform.** Nothing to issue: the token you send only has to open this API (`source:read`). With `"uses": ["crystallon"]`, the control plane (Higgs) has Charm issue the application's token, and the agent of the node (Strange) writes it in the file `/run/gluonify-tokens/crystallon.token` (read-only inside the instance's sandbox) and renews it before it expires (900 s by default). The platform provides the variable `GLUONIFY_CRYSTALLON_TOKEN_FILE` with that path (setting `source.crystallon.token-file`). [`CrystallonClients`](src/main/java/io/gluonify/source/documents/CrystallonClients.java) reads the file again **at every call**, so a renewal needs no restart. To know:

- the application's token carries Crystallon's default organization (`default`), so the template `Gluonify invoice` must exist there;
- without the variable (the application is not deployed with `"uses": ["crystallon"]`, or the platform does not issue application tokens yet), `platform` **stops the application at startup** with a message that says why, instead of failing at the first request;
- when Crystallon refuses the application's token, the answer is a 502, not a 403: the caller cannot fix it, the deployment can;
- the published SDK (0.6.0) does not have `CrystallonTokens.platform()` yet (it is in the SDK sources, for a later release): `CrystallonClients.platform` does the same with `CrystallonTokens.file(…)`. With a newer SDK, replace it by that call.

Where to set `SOURCE_CRYSTALLON_TOKEN=platform`: in `"env"` with `PUT /apps/<name>` (path B of [§9](#9-deploying)); with the builder (path A), which has no `env` field, in the vault space of `topNamespace` (keys arrive unprefixed). Under `mvn quarkus:dev` this API asks for no token, but Crystallon still does: set `GLUONIFY_SERVICE_CRYSTALLON_URL` to its address, then either send (or paste in the interface) a Charm token that meets the conditions above, which is forwarded as it is, or try `platform` with a Charm token written in a file (`SOURCE_CRYSTALLON_TOKEN=platform` and `GLUONIFY_CRYSTALLON_TOKEN_FILE=<that file>`).

**4. The template and the generated code.** The example calls the Crystallon template named **`Gluonify invoice`**: create it in Crystallon's console (`/crystallon/` behind the orchestrator; *New template*, *Start from* the `invoice` preset, with exactly that name). Crystallon then **generates the Java code** of a template: records that describe exactly the data it reads, and a class that calls it. Open the template and its **Code** tab (it asks you to save first: the code is generated from the saved version), set *Java package* to `io.gluonify.source.documents.generated` and *Class name* to `GluonifyInvoice`, leave *Pin this version* unchecked (the code then renders the current version of the template), then *Download (zip)* or *Copy*. The three files of [`documents/generated/`](src/main/java/io/gluonify/source/documents/generated) are exactly that output (only a header comment was added), generated from version 1 of the template:

- `GluonifyInvoicePayload`: a record per object of the data (`Sender`, `Recipient`, `Invoice`, `Item`, `Payment`); the Javadoc of each component says whether it is required;
- `GluonifyInvoiceDocument`: the facade, with `render(payload)`, `render(payload, RenderOptions)` and `validate(payload)`; it calls the template **by name** (the same in every environment) and carries the constants `TEMPLATE_NAME`, `TEMPLATE_VERSION` and `SCHEMA_SHA256`;
- `package-info`.

**To regenerate them** (the template changed and now expects another payload: its `SCHEMA_SHA256` is no longer the one of the facade), generate again in the Code tab with the same two fields and replace the three files; never edit them by hand. The compiler then shows what to adapt in [`InvoiceMapping`](src/main/java/io/gluonify/source/documents/InvoiceMapping.java), the only class that knows the shape of the payload. Without the console, the same code comes from the API (role `gluonify:crystallon:templates`, not `render`): `GET /v1/templates/<id>/code?lang=java&package=io.gluonify.source.documents.generated&name=GluonifyInvoice`.

**Native image.** The SDK declares its own records for reflection; **those of the payload are the application's business**. The root record AND every nested record of each generated payload are listed in [`DocumentsReflection`](src/main/java/io/gluonify/source/documents/DocumentsReflection.java) (`@RegisterForReflection`). Forgetting one breaks only the native executable, at the first call: `DocumentsReflectionTest` checks it.

**5. The endpoint.**

```bash
curl -s -X POST localhost:8080/api/documents/invoice -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -o invoice.pdf -d '{
  "number": "2026-0042", "subject": "Website relaunch", "lang": "en",
  "customer": {"name": "Example Customer SA", "street": "Rue du Lac", "houseNumber": "7", "postalCode": "1003", "city": "Lausanne", "country": "CH"},
  "lines": [{"description": "Consulting, 12 hours", "quantity": 12, "unit": "h", "unitPrice": 160}]}'
```

`source:read` opens it. `date` (today), `vatRate` (8.1 %) and `unit` (`pce`) have defaults; the amount of each line is computed by the application; `lang` (`en`, `fr`, `es`, `it`, `de`) is optional, the currency, country and time zone are the template's (CHF, CH, Europe/Zurich). The answer is the PDF (`200`, `application/pdf`, PDF/A-1B, `Content-Disposition: attachment`, and the headers `X-Crystallon-Pages` and `X-Crystallon-Warnings`). Errors have the usual body `{"error": "…"}`, plus `code` and `path` (the place of the problem in the template) when Crystallon gives them ([`DocumentErrors`](src/main/java/io/gluonify/source/documents/DocumentErrors.java)):

| Situation | Answer |
|---|---|
| invalid request (blank number, no line, unknown `lang`…) | 400; Crystallon is not called |
| no token, or without `source:read` | 401 / 403 |
| Crystallon refuses the token (audience or role `gluonify:crystallon:render` missing, expired) | `forwarding`: 403, with the reason (the caller's token is not enough); `platform`: 502 (the application's token: the caller cannot fix it) |
| the template cannot render these data (Crystallon 400 / 422) | 422, with `code` and `path` |
| no template of this name for the organization of the token (Crystallon 404), Crystallon failing (5XX), unexpected answer, unreachable, unreadable token file (`platform`) | 502 |
| Crystallon too slow (30 s) | 504 |
| Crystallon not configured | 404 |

**In the interface**: a *Download sample invoice* button in the *Documents* section (shown only when `GET /api/platform` says `"documents": true`) sends your pasted token and a sample invoice; its hint says which token Crystallon receives (`GET /api/platform` answers `"documentsToken": "forwarding"` or `"platform"`). [`DocumentsResourceTest`](src/test/java/io/gluonify/source/documents/DocumentsResourceTest.java) and [`DocumentsPlatformTokenTest`](src/test/java/io/gluonify/source/documents/DocumentsPlatformTokenTest.java) test all of it against a fake Crystallon (a JDK `HttpServer`): the path called (`/v1/templates/by-name/Gluonify%20invoice/render`), the forwarded token or the one of the token file (read again after a renewal), the payload and each error above.

### Calling another application

Declare it in `"uses"`: the platform provides `GLUONIFY_SERVICE_<APP>_URL` **and opens the network** to it (without `uses`, the application cannot reach it). Never hardcode the address: read the variable (see `ping` in [`PlatformResource`](src/main/java/io/gluonify/source/platform/PlatformResource.java)).

### Static sites and domains

- **Field** serves a static site (ZIP) on your domain; nothing to code in this service.
- **Your domain**: `PUT /domains/<name>` on Higgs's API (see [`deploy/domain.json`](deploy/domain.json)) routes a path to the application and applies **policies**: Charm token (audience, roles), rate limits, CORS, address lists. The HTTPS certificate is obtained automatically.

## 8. Building

```bash
mvn test                       # Java tests (86)
mvn package                    # JVM: target/quarkus-app/; also builds the interface (Quinoa)
```

**The native executable** (what Gluonify runs: one file, no JVM):

```bash
docker build --target out --output type=local,dest=dist -f Dockerfile.build .
./dist/gluonify-source         # runs on this Linux, port 8080
```

The `dist/gluonify-source` file is a **glibc** Linux executable for your Docker's architecture (arm64 on Apple Silicon, amd64 on x86_64). For the other architecture: `docker build --platform linux/amd64 …` (slow: emulation; see the comments in `Dockerfile.build`).

Without Docker: `mvn package -Dnative` requires GraalVM (NIK 25) to be installed.

**Platform standards**: after the clone, Gluonify's builder checks the project (`R-SANTE`, `R-SECRET`, `R-FICHIER-SENSIBLE`, `R-NATIF`, `R-IMAGE`, `R-ECOUTE`) and rejects a repository that does not comply. [`ConformityTest`](src/test/java/io/gluonify/source/ConformityTest.java) checks them **on your side**; keep it.

## 9. Deploying

There are two paths.

**A. Gluonify builds for you** (the simplest): give the Git address to the builder (**gluonify-bup**):

```bash
curl -X POST "$BUP_URL/v1/builds" -H 'Content-Type: application/json' -H "X-Git-Token: $GIT_TOKEN" \
  -d '{"name":"gluonify-source","gitUrl":"https://github.com/VOUS/VOTRE-DEPOT.git","ref":"main","deploy":true,"replicas":2,"memoryMb":128,"topNamespace":"gluonify-source"}'
```

The builder clones, checks compliance, compiles natively, publishes the executable and deploys it. Follow it: `GET /v1/builds/<id>` and `/logs`. (A Git push can also trigger the build through a webhook: `POST /v1/webhooks/git`.) Note: the builder only knows `topNamespace`, so with it the vault keys arrive unprefixed.

**The services the builder declares.** The builder sends `uses` and `gdownDatabase` to `PUT /apps/<name>`, taken from the build request (`"uses": ["charm","gdown","crystallon"]`) or, when the request has none, from the [`gluonify.json`](gluonify.json) at the root of the repository (the request wins; an empty list in the request means "no service"). This repository's file is `{"uses": ["charm", "gdown", "crystallon"]}`: the same services as [`deploy/appspec.json`](deploy/appspec.json), which `DeploySpecsTest` checks. It matters because `PUT /apps/<name>` **replaces the whole spec**: a build that sent no `uses` would deploy the application without `GLUONIFY_SERVICE_*_URL`, without network to Charm, Gdown and Crystallon, and without the application's Crystallon token (so `source.crystallon.token=platform` would stop it at startup). Add `"gdownDatabase": true` to `gluonify.json` for the dedicated database; a leading `?` (`"?crystallon"`) marks an optional service. Declare `uses` in `gluonify.json` or in the build request, always.

**B. You publish the executable** yourself, then you deploy:

```bash
SHA=$(shasum -a 256 dist/gluonify-source | cut -d' ' -f1)
# publish dist/gluonify-source at an HTTPS address, then:
curl -X PUT "https://api.$ZONE/apps/gluonify-source" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d @deploy/appspec.json     # after putting your address and your hash in artifacts
```

The application is then at `https://gluonify-source.<zone>` (automatic certificate). The fields of [`deploy/appspec.json`](deploy/appspec.json):

| Field | Role |
|---|---|
| `artifacts` | the executable per architecture: `URL#sha256=…` (the hash is **mandatory**, verified by the node) |
| `replicas` | number of instances (2 or more: updates without downtime) |
| `memoryMb` | memory cap per instance (a native executable fits in 64 to 128 MB) |
| `uses` | reachable services, by application name (`charm`, `gdown`, `crystallon`…): `GLUONIFY_SERVICE_<APP>_URL` provided, network opened; for `crystallon`, also the application's token (`GLUONIFY_CRYSTALLON_TOKEN_FILE`). With the builder: in `gluonify.json` or the build request |
| `distributed` | `["std"]` for `/distributed/std` |
| `env` | **non-secret** variables; `${NAME}` and `${NAME:-default}` are resolved |
| `top` | `true`: the control plane gives the application its own vault space `<uuid>.app`, whose keys arrive as `APP_<KEY>` |
| `gdownDatabase` | `true`: a dedicated Gdown database for the application (own Raft group, confined account); `GLUONIFY_GDOWN_DATABASE`, `GLUONIFY_GDOWN_USER`, `GLUONIFY_GDOWN_PASSWORD` and `GLUONIFY_SERVICE_GDOWN_URL` are provided; never deleted with the application (see [`deploy/appspec-graph.json`](deploy/appspec-graph.json)) |
| `topNamespace` | alternative: an existing vault space by name; its keys arrive **unprefixed** (no `APP_`) |
| `internal` | `true`: no public route (internal service) |

To update: same `PUT` (or a new build); replicas are replaced **one by one**. To restart without changes: `POST /apps/<name>/redeploy`. Logs: `GET /apps/<name>/logs`. Metrics: `/q/metrics`.

## 10. Testing

```bash
mvn test                                 # 86 Java tests
cd src/main/webui && npm install && npm test   # 33 interface tests
```

| Test | Tests | What it checks |
|---|---|---|
| `NotesResourceTest` | 7 | 401 without a token, 403 without the right role, full lifecycle, 400 validation, OpenAPI, health, metrics |
| `FileNoteStoreTest` | 6 | new files never renamed, ordering, two replicas on the same folder, unreadable files ignored, identifiers never paths |
| `GraphNoteStoreTest` | 4 | against a fake Gdown: path, authentication, **parameterized** statement, row reading, failure |
| `WebhookResourceTest` / `WebhookClosedTest` | 4 + 1 | key, acknowledgment, **idempotence**, rejections; closed without a key |
| `PlatformResourceTest` | 4 | platform variables read, **no secret returned**, service-to-service call limited to declared services |
| `ConformityTest` | 4 | the builder's standards, on your side |
| `EventLedgerTest` | 4 | idempotence ledgers: exactly one winner per event (memory, files with two replicas, graph uniqueness constraint, graph error other than a duplicate propagated) |
| `DocumentsResourceTest` | 13 | against a **fake Crystallon**: PDF returned, path called (`/v1/templates/by-name/Gluonify%20invoice/render`), **forwarded token**, payload sent, mapping of 422 / 403 / 502, own validation (400), roles, OpenAPI |
| `DocumentsDisabledTest` | 4 | Crystallon not configured: 404 with the reason, `"documents": false`, the rest unaffected, token settings empty by default |
| `DocumentsPlatformTokenTest` | 8 | `source.crystallon.token=platform` against the fake Crystallon: the **application's token** (not the caller's) is sent, also outside a request, the file is **read again after a renewal**, a refusal is a 502, `"documentsToken"` |
| `CrystallonClientsTest` | 11 | no address = no client, a bad address named, the caller's token asked at every call; the two modes: unknown mode (value never repeated), `platform` without its file, token of the file re-read, ignored when `forwarding` |
| `InvoiceMappingTest` / `DocumentErrorsTest` / `ForwardedTokenTest` | 3 + 9 + 1 | your invoice → the payload of the template (amounts, defaults, safe file name); every Crystallon failure → a status, in each token mode; which headers give a token |
| `DocumentsReflectionTest` | 1 | native guard: every record of the generated payload is registered for reflection |
| `DeploySpecsTest` | 2 | the specs of `deploy/` use current names (`uses`, `SOURCE_STORE`); `gluonify.json` (read by the builder) declares the same `uses` as `deploy/appspec.json` |

An API test is copied from `NotesResourceTest`; a storage test from `FileNoteStoreTest` (without starting Quarkus: fast).

## 11. Known pitfalls

- **Native**: do **not** create an `HttpClient`, a `Random` or a `SecureRandom` in a `static` field (the state would be frozen at compile time, not at run time): create them on first use (see `GraphNoteStore.client()`). Read third-party JSON as a tree (`JsonNode`) rather than into classes; a type (de)serialized by Jackson outside a REST signature must carry `@RegisterForReflection`. **A bug that only appears in native does not show up in `mvn test`**: run the native executable before shipping.
- **Jackson 3**: the package is `tools.jackson.databind`, not `com.fasterxml.jackson.databind`.
- **No address, no port, no password hardcoded**: everything comes from the environment.
- **Distributed files**: see the rules in [§7](#distributed-files-sourcestorefiles) (never rewrite, ~3 s lag).
- **Webhooks**: answer quickly with 2XX, be idempotent, protect with a key.
- **Generated code**: the files of `documents/generated/` come from the *Code* tab of Crystallon: regenerate them, never edit them by hand; and list every record of a generated payload in `DocumentsReflection` (a failure that only shows in native).
- **Names that look secret**: the builder's rule R-SECRET rejects a plaintext value on a setting whose name contains `token`, `password`, `secret` or `key`. `source.crystallon.token` holds a mode, not a token, yet it is written `${SOURCE_CRYSTALLON_TOKEN:}`; today the builder accepts `${VARIABLE}` and `${VARIABLE:}` (empty default) there, **not** `${VARIABLE:forwarding}`: the default lives in the code (`TokenMode`). `ConformityTest` is more lenient than the builder on this point (it accepts any `${…}`): keep the default empty.
- **A development identity has no business in production**: keep `@IfBuildProfile("dev")`.
- **Quinoa and API addresses**: add any new API prefix to `quarkus.quinoa.ignored-path-prefixes`.
- **Under x86_64 emulation** (Docker on Apple Silicon): build with `--build-arg MAVEN_OPTS=-Djdk.lang.Process.launchMechanism=VFORK`.

## 12. Troubleshooting

| Symptom | Probable cause |
|---|---|
| 401 in production | no token, expired token, or **audience** different from `GLUONIFY_ZZZNONE_OIDC_AUDIENCE`; or `GLUONIFY_ZZZNONE_OIDC_ISSUER` does not match the token's `iss` |
| 403 | valid token but without the role (`source:read` / `source:write`) |
| The application receives no traffic | `/q/health/ready` does not answer 200 (storage unreachable?): `GET /apps/<name>` shows the ready instances |
| `store=gdown`: "source.graph.url is empty" | the application is not deployed with `"uses": ["gdown"]` |
| Gdown: 401 or "Unsupported property value type" | wrong account; or a **map** stored as a property (see §7) |
| A created note does not appear right away (files) | ~3 s list cache between replicas: refresh |
| The repository build is rejected | an `R-…` rule: the message names it; `mvn test` (`ConformityTest`) shows it on your side |
| `mvn package` cannot find Node | network access to download it, or `-Dquarkus.quinoa=false` to build without the interface |
| The native executable crashes at startup but not on the JVM | state frozen at compile time (`static`) or missing reflection (§11) |
| `POST /api/documents/invoice`: 404 "documents are off" | the application is not deployed with `"uses": ["crystallon"]` (no `GLUONIFY_SERVICE_CRYSTALLON_URL`) |
| Documents: 403 "Crystallon did not accept your token" | `forwarding` mode: the token lacks the audience `gluonify-crystallon` or the role `gluonify:crystallon:render`, or has expired (see [§7](#documents-crystallon)); or choose `source.crystallon.token=platform` |
| Documents: 502 "did not accept the token of the application" | `platform` mode: the application is not deployed with `"uses": ["crystallon"]`, the platform does not issue application tokens yet, or `GLUONIFY_CRYSTALLON_TOKEN_FILE` is missing, empty or stale |
| The application does not start: "source.crystallon.token=platform needs the file of the application token" | `platform` without `GLUONIFY_CRYSTALLON_TOKEN_FILE`: declare `"uses": ["crystallon"]` (in `gluonify.json` too, for a build by the builder) or use `forwarding` |
| Documents: 502 "does not know the template" | no template named `Gluonify invoice` in the organization of the token (`platform`: Crystallon's default organization): create it in Crystallon's console |
| Documents: 422 with `code` and `path` | the template cannot render these data: `path` is the place of the problem in the template |
| Documents work on the JVM but fail at the first call in the native executable | a record of the generated payload is missing from `DocumentsReflection` |

---

*Gluonify is a minimalist platform for native Quarkus applications: [gluonify.cloud](https://gluonify.cloud) (overview) and [gluonify.io](https://gluonify.io) (technical). This repository is deliberately small: it can be read in an hour.*
