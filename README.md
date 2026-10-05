# gluonify-source

**English** · [Français](README.fr.md) · [Español](README.es.md) · [Italiano](README.it.md) · [Deutsch](README.de.md)

**The starting point for a Quarkus 4 service on [Gluonify](https://gluonify.cloud).** A small, complete service (some "notes") that shows, with running code and tests, how to:

1. build a **REST service** (validation, roles, OpenAPI);
2. build a **web interface** with [Quinoa](https://quarkus.io/extensions/io.quarkiverse.quinoa/quarkus-quinoa/) (Vue 3, served by the same executable);
3. **integrate with Gluonify services**: configuration and secrets (Top), identity tokens (Charm), data (Gdown) or distributed files, other applications, webhooks received from Photon, static sites (Field), domains and policies (Higgs);
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
| `/` | the Vue interface |
| `/api/notes` | the REST API (JSON) |
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
  security/DevAuthentication.java   development identity (dev profile only)
src/main/webui/                 the Vue 3 interface (Vite + vitest), built by Quinoa
src/test/java/…                 34 Java tests; src/main/webui/src/App.test.js: 15 interface tests
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
| Signature | Charm's keys, read at `${SERVICE_ID_URL}/realms/gluonify` | `SERVICE_ID_URL` is provided by the platform if the application is deployed with `"uses": ["id"]` |
| Issuer (`iss`) | `quarkus.oidc.token.issuer` | `OIDC_ISSUER` = `https://id.<your zone>/realms/gluonify` (stable: it is not the address of the Charm instance) |
| Audience (`aud`) | `quarkus.oidc.token.audience` | `OIDC_AUDIENCE`, by default the project name (`gluonify-source`) |
| Expiration | automatic | short-lived tokens |
| Roles | `roles` claim | `source:read` (read), `source:write` (write): see `@RolesAllowed` |

**Getting a test token** (the platform administrator, with Charm's administration token):

```bash
curl -s -X POST "$CHARM_URL/v1/tokens" -H "Authorization: Bearer $ID_ADMIN_TOKEN" -H 'Content-Type: application/json' \
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
| **Top** (vault) | per-application configuration and secrets | with `"vault": true`, the keys of your application's own `<uuid>.app` space arrive as `APP_<KEY>`; `${app.graph.password}` in `application.properties` | `source.graph.password`, `source.webhook.key` |
| **Charm** (identity) | short-lived JWT tokens | `"uses": ["id"]` → `SERVICE_ID_URL`; `quarkus-oidc` verifies tokens | [§6](#6-authentication-charm-tokens) |
| **Gdown** (graph database) | replicated database (Raft), Cypher over HTTP | `"uses": ["graphdb"]` → `SERVICE_GRAPHDB_URL`; a local Gdown account | [`GraphNoteStore`](src/main/java/io/gluonify/source/notes/GraphNoteStore.java) |
| **Distributed files** | `/distributed/std`: durable, shared by all replicas, 2 copies | `"distributed": ["std"]` at deployment | [`FileNoteStore`](src/main/java/io/gluonify/source/notes/FileNoteStore.java) |
| **Other applications** | service-to-service calls | `"uses": ["other"]` → `SERVICE_OTHER_URL` | [`PlatformResource.ping`](src/main/java/io/gluonify/source/platform/PlatformResource.java) |
| **Photon** (API gateway) | signed webhooks, XML / SOAP / form → JSON conversion, replay | Photon delivers to `…/hooks/events`; your service acknowledges with 2XX | [`WebhookResource`](src/main/java/io/gluonify/source/platform/WebhookResource.java) |
| **Field** | static sites (ZIP) served on your domain | nothing to code: a site is uploaded as a ZIP | — |
| **Higgs** (orchestrator) | domains, automatic HTTPS certificates, routes, policies (token, limits, CORS, addresses) | `PUT /domains/<name>` | `deploy/domain.json` |

### Configuration and secrets (Top)

- A **non-secret** value: in the deployment's `"env"` (`deploy/appspec.json`): `"SOURCE_STORE": "files"`. It is stored in the cluster state.
- A **secret** value (password, API key): in the **vault** (Top, the `<uuid>.app` space of your application). The `GRAPH_PASSWORD` key arrives as the `APP_GRAPH_PASSWORD` variable and is read as `${app.graph.password}`. Ask for that space with `"vault": true` in the application spec: only in this control-plane-assigned `<uuid>.app` space do keys get the `APP_` prefix. With a literal `"vaultNamespace"` (for example `"gluonify-source"`, the only option of the builder), keys arrive **unprefixed**, exactly as named in the vault (`GRAPH_PASSWORD` stays `GRAPH_PASSWORD`): name them after the properties you read (for example `SOURCE_GRAPH_PASSWORD`). **Never** in the repository: the builder rejects a plaintext password (rule R-SECRET), and `ConformityTest` warns you beforehand.
- **Saving a value restarts nothing**: after changing several keys, trigger **one** redeployment (`POST /apps/<name>/redeploy`); replicas restart one by one, with no downtime if you have two or more.
- Variables the platform **always** adds: `QUARKUS_HTTP_PORT` and `QUARKUS_HTTP_HOST`, `ENV_NAME` (the environment: SBX, QUA, PRD…), `ENV_NODE` (replica rank: 1, 2…), `GLUONIFY_SELF_URL` (address of this instance). They are read and displayed by [`PlatformResource`](src/main/java/io/gluonify/source/platform/PlatformResource.java) (`GET /api/platform`, never a secret).

### Health: `/q/health/ready` and `/q/health/live`

The platform only sends traffic to instances whose **`/q/health/ready`** answers 200, and restarts those whose **`/q/health/live`** fails repeatedly. `quarkus-smallrye-health` is **mandatory** (the builder checks it: rule R-SANTE). [`StoreHealth`](src/main/java/io/gluonify/source/notes/StoreHealth.java) makes the instance "ready" only if storage responds. Keep "alive" independent of external services: a Gdown outage must not make the application restart in a loop.

### Data in Gdown (`source.store=graph`)

1. Deploy with `"uses": ["graphdb"]` (this is also what **opens the network** to Gdown) and `SOURCE_STORE=graph`.
2. An administrator creates the database and the application account ([`deploy/gdown-setup.cypher`](deploy/gdown-setup.cypher): `source` database, confined role, `notes` account).
3. Put `GRAPH_USER` (= `notes`) and `GRAPH_PASSWORD` in the vault.

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

### Calling another application

Declare it in `"uses"`: the platform provides `SERVICE_<APP>_URL` **and opens the network** to it (without `uses`, the application cannot reach it). Never hardcode the address: read the variable (see `ping` in [`PlatformResource`](src/main/java/io/gluonify/source/platform/PlatformResource.java)).

### Static sites and domains

- **Field** serves a static site (ZIP) on your domain; nothing to code in this service.
- **Your domain**: `PUT /domains/<name>` on Higgs's API (see [`deploy/domain.json`](deploy/domain.json)) routes a path to the application and applies **policies**: Charm token (audience, roles), rate limits, CORS, address lists. The HTTPS certificate is obtained automatically.

## 8. Building

```bash
mvn test                       # Java tests (34)
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
  -d '{"name":"gluonify-source","gitUrl":"https://github.com/VOUS/VOTRE-DEPOT.git","ref":"main","deploy":true,"replicas":2,"memoryMb":128,"vaultNamespace":"gluonify-source"}'
```

The builder clones, checks compliance, compiles natively, publishes the executable and deploys it. Follow it: `GET /v1/builds/<id>` and `/logs`. (A Git push can also trigger the build through a webhook: `POST /v1/webhooks/git`.) Note: the builder only knows `vaultNamespace`, so with it the vault keys arrive unprefixed.

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
| `uses` | reachable services: `SERVICE_<APP>_URL` provided, network opened (`id` = Charm, `graphdb` = Gdown) |
| `distributed` | `["std"]` for `/distributed/std` |
| `env` | **non-secret** variables; `${NAME}` and `${NAME:-default}` are resolved |
| `vault` | `true`: the control plane gives the application its own vault space `<uuid>.app`, whose keys arrive as `APP_<KEY>` |
| `vaultNamespace` | alternative: an existing vault space by name; its keys arrive **unprefixed** (no `APP_`) |
| `internal` | `true`: no public route (internal service) |

To update: same `PUT` (or a new build); replicas are replaced **one by one**. To restart without changes: `POST /apps/<name>/redeploy`. Logs: `GET /apps/<name>/logs`. Metrics: `/q/metrics`.

## 10. Testing

```bash
mvn test                                 # 34 Java tests
cd src/main/webui && npm install && npm test   # 15 interface tests
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

An API test is copied from `NotesResourceTest`; a storage test from `FileNoteStoreTest` (without starting Quarkus: fast).

## 11. Known pitfalls

- **Native**: do **not** create an `HttpClient`, a `Random` or a `SecureRandom` in a `static` field (the state would be frozen at compile time, not at run time): create them on first use (see `GraphNoteStore.client()`). Read third-party JSON as a tree (`JsonNode`) rather than into classes; a type (de)serialized by Jackson outside a REST signature must carry `@RegisterForReflection`. **A bug that only appears in native does not show up in `mvn test`**: run the native executable before shipping.
- **Jackson 3**: the package is `tools.jackson.databind`, not `com.fasterxml.jackson.databind`.
- **No address, no port, no password hardcoded**: everything comes from the environment.
- **Distributed files**: see the rules in [§7](#distributed-files-sourcestorefiles) (never rewrite, ~3 s lag).
- **Webhooks**: answer quickly with 2XX, be idempotent, protect with a key.
- **A development identity has no business in production**: keep `@IfBuildProfile("dev")`.
- **Quinoa and API addresses**: add any new API prefix to `quarkus.quinoa.ignored-path-prefixes`.
- **Under x86_64 emulation** (Docker on Apple Silicon): build with `--build-arg MAVEN_OPTS=-Djdk.lang.Process.launchMechanism=VFORK`.

## 12. Troubleshooting

| Symptom | Probable cause |
|---|---|
| 401 in production | no token, expired token, or **audience** different from `OIDC_AUDIENCE`; or `OIDC_ISSUER` does not match the token's `iss` |
| 403 | valid token but without the role (`source:read` / `source:write`) |
| The application receives no traffic | `/q/health/ready` does not answer 200 (storage unreachable?): `GET /apps/<name>` shows the ready instances |
| `store=graph`: "source.graph.url is empty" | the application is not deployed with `"uses": ["graphdb"]` |
| Gdown: 401 or "Unsupported property value type" | wrong account; or a **map** stored as a property (see §7) |
| A created note does not appear right away (files) | ~3 s list cache between replicas: refresh |
| The repository build is rejected | an `R-…` rule: the message names it; `mvn test` (`ConformityTest`) shows it on your side |
| `mvn package` cannot find Node | network access to download it, or `-Dquarkus.quinoa=false` to build without the interface |
| The native executable crashes at startup but not on the JVM | state frozen at compile time (`static`) or missing reflection (§11) |

---

*Gluonify is a minimalist platform for native Quarkus applications: [gluonify.cloud](https://gluonify.cloud) (overview) and [gluonify.io](https://gluonify.io) (technical). This repository is deliberately small: it can be read in an hour.*
