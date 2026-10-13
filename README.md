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

In **development**, no token is required: a "dev" identity (with all roles) exists only in this profile (`@IfBuildProfile("dev")`, absent from the production executable). Notes are **in memory** (`source.store=auto` falls back to memory without a dedicated database): they disappear on shutdown.

```bash
curl -s localhost:8080/api/notes -H 'Content-Type: application/json' -d '{"title":"Courses","body":"du pain"}'
curl -s localhost:8080/api/notes
```

## 2. The project map

```
pom.xml                         STANDALONE Maven project (Quarkus 4.0.0.Beta1, Java 25): no Gluonify parent
gluonify.json                   read by Gluonify's builder (Bup): only the services the SDK cannot detect (`uses`: charm, gdown); Bup infers the rest
crystallon/templates/           the Crystallon template of the example, as the console exports it; the builder installs it at deployment
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
    Documents.java              is Crystallon available? Nothing to configure: the client comes from the SDK (gluonify-plasma-quarkus)
    DocumentErrors.java         Crystallon failure -> 403 / 422 / 502 / 504
    DocumentsReflection.java    native image: the records of the generated payload
    generated/                  code generated by the "Code" tab of Crystallon (do not edit by hand)
  security/DevAuthentication.java   development identity (dev profile only)
  security/AuthConfigResource.java GET /api/auth-config: what the interface needs to sign in with Charm (public)
src/main/webui/                 the Vue 3 interface (Vite + vitest), built by Quinoa
  src/auth.js                   sign-in with Charm (authorization code + PKCE) through the kit @gluonify/ui
  vendor/gluonify-ui/           embedded copy of the kit (gluonify-ui/scripts/sync-vendor.sh, never edited by hand)
src/test/java/…                 70 Java tests; src/main/webui/src/App.test.js: 40 interface tests
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
- **Call the API**: relative addresses (`fetch('/api/notes')`), see [`src/api.js`](src/main/webui/src/api.js). The Charm token comes from the sign-in ([`src/auth.js`](src/main/webui/src/auth.js), see [§6](#6-authentication-charm-tokens)), is kept for the tab (`sessionStorage`) and added as `Authorization: Bearer`.
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
| Issuer (`iss`) | `quarkus.oidc.token.issuer` | `GLUONIFY_SOURCE_OIDC_ISSUER` = `https://id.<your zone>/realms/gluonify` (stable: it is not the address of the Charm instance) |
| Audience (`aud`) | `quarkus.oidc.token.audience` | `GLUONIFY_SOURCE_OIDC_AUDIENCE`, by default the project name (`gluonify-source`) |
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

### Signing in from the interface (Charm's login page)

The interface never asks for a password and never asks you to paste a token. It is a **public OpenID Connect client** of Charm (authorization code + PKCE S256, no secret in the browser), built on the shared kit `@gluonify/ui` (`createAuth`, the same sign-in as the platform consoles), vendored in [`src/main/webui/vendor/gluonify-ui`](src/main/webui/vendor/gluonify-ui) so that a clone builds on its own (refresh it with `gluonify-ui/scripts/sync-vendor.sh <this repository> src/main/webui`, never by hand):

1. The page asks `GET /api/auth-config` (public): the client id, Charm's **public** endpoints (`/authorize`, `/token`, `/logout`) and `signInRequired` (false under `mvn quarkus:dev`: the API asks for no token there, so the interface shows everything).
2. **Sign in** sends the browser to Charm's page: the person types the username and password **there**, never in the application. Charm sends the browser back to the interface (`https://<address of the interface>/?code=…&state=…`).
3. The interface checks `state`, exchanges the code with its PKCE verifier at Charm's `/token` and gets a token of audience `gluonify-source` whose `roles` claim holds the roles of the account (`source:read`, `source:write`). The token is kept for the tab (`sessionStorage`), renewed with the refresh token before it expires, and sent as `Authorization: Bearer` to `/api`.
4. **Sign out** forgets the token, tells the other tabs and ends Charm's session (`/logout` with `id_token_hint`). A 401 from the API (token expired or revoked) shows the sign-in again.

Signed out, the page shows only a short explanation and the **Sign in** button: notes, documents and the platform section all need a token.

**Sign-in works with no configuration when deployed on the platform.** An application that declares `"uses": ["charm"]` (Bup infers it from the code) gets its own public Charm client, `app-<name>`, created and kept up to date by the platform: its redirects are exactly the application's addresses (`https://<name>.<zone>/` and the routes of its domains), its audience is `gluonify-<name>`. The platform injects `GLUONIFY_OIDC_CLIENT_ID`, `GLUONIFY_OIDC_ISSUER` (public issuer), `GLUONIFY_OIDC_PUBLIC_URL` (Charm's public address) and `GLUONIFY_OIDC_AUDIENCE`, which are the defaults below; the `GLUONIFY_SOURCE_OIDC_*` variables still override them.

| Setting | Variable | Value |
|---|---|---|
| `source.auth.client-id` | `GLUONIFY_SOURCE_OIDC_CLIENT_ID` | the Charm client of this interface (for example `source-ui`); empty: no *Sign in* button, the page names the variable; default `GLUONIFY_OIDC_CLIENT_ID` (injected by the platform) |
| `source.auth.charm-url` | `GLUONIFY_SOURCE_CHARM_PUBLIC_URL` | Charm's **public** address, the one the browser reaches; empty: derived from `GLUONIFY_SOURCE_OIDC_ISSUER` (`https://gluonify.lan/realms/gluonify` → `https://gluonify.lan`). Never `GLUONIFY_SERVICE_CHARM_URL`: it is an internal address |
| `source.auth.scope` | `SOURCE_AUTH_SCOPE` | `openid profile` (the kit adds `offline_access` for the refresh token) |

**Outside the platform, or to use another client**, register one yourself (once, by the platform administrator) and set `GLUONIFY_SOURCE_OIDC_CLIENT_ID`: a public client whose only redirect (and post-logout redirect) is the exact address of the interface, trailing slash included, and whose audience is the application's. Charm accepts cross-origin calls to `/token` only from the origins of registered redirects.

```bash
curl -s -X PUT "$CHARM_URL/v1/admin/clients/source-ui" -H "Authorization: Bearer $GLUONIFY_CHARM_ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Source","redirectUris":["https://source.<zone>/"],"postLogoutRedirectUris":["https://source.<zone>/"],"audiences":["gluonify-source"],"scopes":["openid","profile","email","offline_access"],"accessTtl":3600}'
```

**The roles** `source:read` and `source:write` are given to the accounts, customer accounts included (the roles of an application are not `gluonify:*`, so Charm lets them be created and given): `PUT /v1/admin/roles/source:read` (and `source:write`), then `PUT /v1/admin/users/<id>/roles`. Charm puts all the roles of the account in the token: sign out and in again after a change.

**Locally**, the `dev` profile provides the "dev" identity (see [`DevAuthentication`](src/main/java/io/gluonify/source/security/DevAuthentication.java)); **in tests**, `@TestSecurity`. Never an identity like this in production: it is compiled only in the `dev` profile.

## 7. Integrating with Gluonify services

An application on Gluonify is **isolated**: its own account, its own network, a reduced file system. It only reaches what it **declares** (`"uses"`) and receives its configuration **through the environment**. Here is each service, with its place in the code.

**No configuration for the services the SDK covers.** The Gluonify SDK finds by itself where a service is and which token to present: the platform provides the address and the application's token (`"uses"`), `gluonify login <zone>` does the same on your machine, and `GluonifyTestServices` replaces the services in tests. Today the SDK covers **Crystallon** (see [Documents](#documents-crystallon)); Gdown and Charm are not covered yet and stay configured by URL (`source.graph.url`, `quarkus.oidc.auth-server-url`): they will follow.

| Service | What it offers | How the application uses it | In this repository |
|---|---|---|---|
| **Top** (vault) | per-application configuration and secrets | with `"top": true`, the keys of your application's own `<uuid>.app` space arrive as `APP_<KEY>`; `${app.gluonify.gdown.password}` in `application.properties` | `source.graph.password`, `source.webhook.key` |
| **Charm** (identity) | short-lived JWT tokens | `"uses": ["charm"]` → `GLUONIFY_SERVICE_CHARM_URL`; `quarkus-oidc` verifies tokens | [§6](#6-authentication-charm-tokens) |
| **Gdown** (graph database) | replicated database (Raft), Cypher over HTTP | `"uses": ["gdown"]` → `GLUONIFY_SERVICE_GDOWN_URL`; a local Gdown account | [`GraphNoteStore`](src/main/java/io/gluonify/source/notes/GraphNoteStore.java) |
| **Distributed files** | `/distributed/std`: durable, shared by all replicas, 2 copies | `"distributed": ["std"]` at deployment | [`FileNoteStore`](src/main/java/io/gluonify/source/notes/FileNoteStore.java) |
| **Other applications** | service-to-service calls | `"uses": ["other"]` → `GLUONIFY_SERVICE_OTHER_URL` | [`PlatformResource.ping`](src/main/java/io/gluonify/source/platform/PlatformResource.java) |
| **Photon** (API gateway) | signed webhooks, XML / SOAP / form → JSON conversion, replay | Photon delivers to `…/hooks/events`; your service acknowledges with 2XX | [`WebhookResource`](src/main/java/io/gluonify/source/platform/WebhookResource.java) |
| **Crystallon** (PDF documents) | invoices, quotes and letters as PDF/A from templates drawn in its editor; Java client of the SDK, generated code | optional, **no configuration**: nothing to declare (built by Bup, `?crystallon` is inferred from the SDK) and the SDK (`gluonify-plasma-quarkus`) finds the address and the application's own token (role `gluonify:crystallon:render`); on your machine `gluonify login` | [`DocumentsResource`](src/main/java/io/gluonify/source/documents/DocumentsResource.java), [details](#documents-crystallon) |
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

**Simplest: a dedicated database.** Nothing to declare: `application.properties` reads `${GLUONIFY_GDOWN_DATABASE}`, `${GLUONIFY_GDOWN_USER}` and `${GLUONIFY_GDOWN_PASSWORD}`, so the builder **infers** `"gdownDatabase": true` (build result field `gdownDatabaseReason`; `"gdownDatabase": false` in `gluonify.json` turns it off), and `source.store=auto` switches to `gdown` as soon as `GLUONIFY_GDOWN_DATABASE` is provided (memory otherwise; `SOURCE_STORE` forces a value): the platform creates a database of its own for the application (its own Raft group, a confined account) and provides `GLUONIFY_GDOWN_DATABASE`, `GLUONIFY_GDOWN_USER`, `GLUONIFY_GDOWN_PASSWORD` and the network access (`GLUONIFY_SERVICE_GDOWN_URL`); nothing else to do. The database is **never deleted** with the application.

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

**1. The dependency.** One dependency brings the whole SDK side: `gluonify-plasma-quarkus` (default CDI beans: you inject a `CrystallonClient`; it brings `gluonify-plasma-services`, which finds addresses and tokens, and the Crystallon client). `gluonify-plasma-testing` (test scope) provides the fake services for the tests. Both are declared in `pom.xml` (already done here):

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

The SDK is served by the Maven repository of **gluonify-sea** (public read: no account to resolve it), added to Maven Central, not instead of it; this `pom.xml` writes its address as the property `gluonify.sea.maven.url`, so that a mirror or a local copy can replace it (`mvn verify -Dgluonify.sea.maven.url=file:///…`). Gluonify's builder (Bup) declares this repository by itself for the builds it runs. **Version**: the SDK is published at 0.7.2 on gluonify-sea, with `gluonify-plasma-quarkus` and `gluonify-plasma-testing`; `gluonify.sdk.version` is `0.7.2` in `pom.xml`, nothing to install locally. Since 0.7.1 each client module carries a marker (`META-INF/gluonify/services/crystallon` in `gluonify-plasma-crystallon-client`) from which the builder infers `uses`. With Gradle: the same two artifacts (`implementation` and `testImplementation`), with no repository other than Maven Central and gluonify-sea.

**2. Nothing to declare.** Built by Bup, the application gets `?crystallon` in its `uses` without writing it: Bup reads the marker of `gluonify-plasma-crystallon-client` on the runtime classpath (SDK 0.7.1 and later) and adds the service as optional; [`gluonify.json`](gluonify.json) does not mention it. Deployed by hand (`PUT /apps`: no build, so no inference), the spec declares it: `crystallon` in the `uses` of [`deploy/appspec.json`](deploy/appspec.json). Either way the platform **opens the network** to Crystallon, and to it alone, and provides its address and the application's own token. **There is no property, no URL and no token in this repository**: the producer of `gluonify-plasma-quarkus` gives the code a `CrystallonClient` (the Jackson 3 mapper of the application, the address and the token found by the SDK). [`Documents`](src/main/java/io/gluonify/source/documents/Documents.java) only asks whether Crystallon is available (`GluonifyServices.optional("crystallon")`) and injects the client as an `Instance<CrystallonClient>`. **Without it the feature is off and nothing else changes**: the endpoint answers 404 and says why, `GET /api/platform` says `"documents": false`, the interface hides the section, the application starts and every other test runs without Crystallon. Where the application runs decides how the SDK finds Crystallon:

| Where | Address | Token |
|---|---|---|
| on the platform, `crystallon` in `uses` (inferred by Bup, or declared in the spec) | provided (`GLUONIFY_SERVICE_CRYSTALLON_URL`) | the application's own token (file renewed by the node agent, read at every call) |
| your machine, after `gluonify login <zone>` | the public address published by the zone | your developer token, renewed by itself |
| tests | the fake Crystallon of `GluonifyTestServices.start()` | the test token |
| elsewhere | none: the feature is off | |

**3. The token and the role.** Crystallon requires a Charm token of audience `gluonify-crystallon`; the role **`gluonify:crystallon:render`** is enough to render and validate (`convert` and `templates` are not needed to produce documents). By default the application calls Crystallon **with its own token** (Charm token, subject `app:<name>`, audience `gluonify-crystallon`, role `gluonify:crystallon:render`), which nobody issues by hand: with `crystallon` in `uses` (inferred or declared) the control plane (Higgs) has Charm issue it, and the agent of the node (Strange) writes it in `/run/gluonify-tokens/crystallon.token` (read-only inside the instance's sandbox) and renews it before it expires (900 s by default). The SDK reads that file **at every call**, so a renewal needs no restart, and the call also works outside a request (a timer, a webhook, a message queue). The token you send to `/api/documents/invoice` only has to open this API (`source:read`). To know:

- the application's token carries the organization of the application (claim `org`), or Crystallon's default organization (`default`) when it has none: the template `Gluonify invoice` must exist there, and that is where the builder installs it (step 4);
- when Crystallon refuses the application's token, the answer is a 502, not a 403: the caller cannot fix it, the deployment can (build with Bup and SDK 0.7.1 or later, which infers it, or declare `crystallon` in the `uses` of the spec);
- under `mvn quarkus:dev` this API asks for no token, but Crystallon still does: run `gluonify login gluonify.lan` (or your zone) once, in your browser, and the SDK uses your developer token and the public address of Crystallon: no variable to set.

**Forwarding the caller's token (optional).** If Crystallon must see the **person** who calls the application (their roles, their organization) instead of the application, replace the default client by one of your own: a bean of the same type wins over the default one. The token the person sends must then carry, besides the audience and the role of this application (`gluonify-source`, `source:read`), the audience `gluonify-crystallon` and the role `gluonify:crystallon:render` (Charm puts several audiences in one token, 8 at most; the `gluonify:` prefix is reserved to the platform, so the administrator issues it), and a call made outside a request has no token to forward. This is not the default and this repository does not contain it:

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

**4. The template and the generated code.** The example calls the Crystallon template named **`Gluonify invoice`**, and **the template lives in this repository**: [`crystallon/templates/gluonify-invoice.json`](crystallon/templates/gluonify-invoice.json) is the template exactly as the *Export* button of Crystallon's editor writes it (the `invoice` preset, with its exact `name` and its `description` at the root). **Nothing to create by hand**: when the builder (Bup) deploys an application that uses Crystallon, it installs every `crystallon/templates/*.json` in the organization the application's token renders in (`PUT /v1/templates/by-name/{name}` of Crystallon): created the first time, left unchanged when identical, saved as a new version when the content changes but the payload stays the same, and **never overwritten** when it would expect another payload (`differs`). The build reports each template in its `templates` field and its log; a failure there is a warning, never a failed deployment. Without the builder (deployment by hand, `mvn quarkus:dev` against a cluster), create the template in Crystallon's console (`/crystallon/`; *New template* named exactly `Gluonify invoice`), *Import* the file and save. **The console remains the place to edit it**: change the template there, *Export* it, replace the file and commit it. Crystallon then **generates the Java code** of a template: records that describe exactly the data it reads, and a class that calls it. Open the template and its **Code** tab (it asks you to save first: the code is generated from the saved version), set *Java package* to `io.gluonify.source.documents.generated` and *Class name* to `GluonifyInvoice`, leave *Pin this version* unchecked (the code then renders the current version of the template), then *Download (zip)* or *Copy*. The three files of [`documents/generated/`](src/main/java/io/gluonify/source/documents/generated) are exactly that output (only a header comment was added), generated from version 0 of the template (the version the builder creates from the file):

- `GluonifyInvoicePayload`: a record per object of the data (`Sender`, `Recipient`, `Invoice`, `Item`, `Payment`); the Javadoc of each component says whether it is required;
- `GluonifyInvoiceDocument`: the facade, with `render(payload)`, `render(payload, RenderOptions)` and `validate(payload)`; it calls the template **by name** (the same in every environment) and carries the constants `TEMPLATE_NAME`, `TEMPLATE_VERSION` and `SCHEMA_SHA256`;
- `package-info`.

**To regenerate them** (the template changed and now expects another payload: its `SCHEMA_SHA256` is no longer the one of the facade), generate again in the Code tab with the same two fields and replace the three files; never edit them by hand. The compiler then shows what to adapt in [`InvoiceMapping`](src/main/java/io/gluonify/source/documents/InvoiceMapping.java), the only class that knows the shape of the payload. Without the console, the same code comes from the API (role `gluonify:crystallon:templates`, not `render`): `GET /v1/templates/<id>/code?lang=java&package=io.gluonify.source.documents.generated&name=GluonifyInvoice`. Commit the exported template and the regenerated code together, so that they always describe the same payload. A template already in Crystallon that expects another payload is left alone by the builder (`differs`): change it in the console, or delete it so that the next deployment creates it again.

**Status.** Verified on 2026-10-11 on the development cluster: the application (SDK 0.7.1) rendered invoice 2026-0042 through the real Crystallon with its own token (200, `application/pdf`, PDF/A-1B, one page), with a template created by hand in the console. **Not yet** verified by a real build: the automatic installation of the template by the builder (covered by tests against a fake Crystallon in gluonify-bup and gluonify-crystallon).

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
| Crystallon refuses the application's token (role `gluonify:crystallon:render` missing, expired, not deployed with `crystallon` in `uses`) | 502 (the caller cannot fix it, the deployment can) |
| the template cannot render these data (Crystallon 400 / 422) | 422, with `code` and `path` |
| no template of this name for the organization of the token (Crystallon 404), Crystallon failing (5XX), unexpected answer, unreachable, token of the application not obtainable | 502 |
| Crystallon too slow (30 s) | 504 |
| Crystallon not available | 404 |

**In the interface**: a *Download sample invoice* button in the *Documents* section (shown only when `GET /api/platform` says `"documents": true`) sends the token of the signed-in person (it only has to open this API) and a sample invoice. [`DocumentsResourceTest`](src/test/java/io/gluonify/source/documents/DocumentsResourceTest.java) tests all of it against the fake Crystallon of the SDK (`GluonifyTestServices.start()` and `close()` in [`GluonifyTestServicesResource`](src/test/java/io/gluonify/source/documents/GluonifyTestServicesResource.java), nothing to configure): the path called (`/v1/templates/by-name/Gluonify%20invoice/render`), the call outside a request with the application's token, the payload and each error above.

### Calling another application

Declare it in `"uses"`: the platform provides `GLUONIFY_SERVICE_<APP>_URL` **and opens the network** to it (without `uses`, the application cannot reach it). Never hardcode the address: read the variable (see `ping` in [`PlatformResource`](src/main/java/io/gluonify/source/platform/PlatformResource.java)).

### Static sites and domains

- **Field** serves a static site (ZIP) on your domain; nothing to code in this service.
- **Your domain**: `PUT /domains/<name>` on Higgs's API (see [`deploy/domain.json`](deploy/domain.json)) routes a path to the application and applies **policies**: Charm token (audience, roles), rate limits, CORS, address lists. The HTTPS certificate is obtained automatically.

## 8. Building

```bash
mvn test                       # Java tests (70)
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

**The services the builder declares.** The builder sends `uses` and `gdownDatabase` to `PUT /apps/<name>`. `uses` is **inferred at build time**: each SDK client module carries a marker `META-INF/gluonify/services/<service>`; Bup reads the markers of the jars of the runtime classpath (group `io.gluonify.plasma` only) and adds them as optional services (here `?crystallon`, from `gluonify-plasma-crystallon-client`). It merges them with the services declared in the build request or, when the request has none, in the [`gluonify.json`](gluonify.json) at the root of the repository (the request wins; an empty list in the request means "no declared service", the inferred ones are still added). **Declare in `uses` only what the SDK cannot detect**: a direct HTTP call to another application, Charm for OIDC, Gdown over HTTP. This repository's file is therefore `{"uses": ["charm", "gdown"]}`: with the inferred `?crystallon`, the same services as [`deploy/appspec.json`](deploy/appspec.json), which `DeploySpecsTest` checks. Without the file, a build would deploy the application without network to Charm and Gdown, because `PUT /apps/<name>` **replaces the whole spec**. No need to add `"gdownDatabase": true`: the builder infers it from the references to `GLUONIFY_GDOWN_DATABASE|USER|PASSWORD` in `application.properties` (`GET /v1/builds/{id}/effective-spec` shows the effective `gluonify.json`); a leading `?` (`"?orders"`) marks an optional service. An application that reaches services only through the SDK needs no `gluonify.json`.

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
| `uses` | reachable services, by application name (`charm`, `gdown`, `crystallon`…): `GLUONIFY_SERVICE_<APP>_URL` provided, network opened; for `crystallon`, also the application's token (`GLUONIFY_CRYSTALLON_TOKEN_FILE`). With the builder: inferred from the SDK modules, plus what `gluonify.json` or the build request declares |
| `distributed` | `["std"]` for `/distributed/std` |
| `env` | **non-secret** variables; `${NAME}` and `${NAME:-default}` are resolved |
| `top` | `true`: the control plane gives the application its own vault space `<uuid>.app`, whose keys arrive as `APP_<KEY>` |
| `gdownDatabase` | `true`: a dedicated Gdown database for the application (own Raft group, confined account); `GLUONIFY_GDOWN_DATABASE`, `GLUONIFY_GDOWN_USER`, `GLUONIFY_GDOWN_PASSWORD` and `GLUONIFY_SERVICE_GDOWN_URL` are provided; never deleted with the application (see [`deploy/appspec-graph.json`](deploy/appspec-graph.json)) |
| `topNamespace` | alternative: an existing vault space by name; its keys arrive **unprefixed** (no `APP_`) |
| `internal` | `true`: no public route (internal service) |

To update: same `PUT` (or a new build); replicas are replaced **one by one**. To restart without changes: `POST /apps/<name>/redeploy`. Logs: `GET /apps/<name>/logs`. Metrics: `/q/metrics`.

## 10. Testing

```bash
mvn test                                 # 70 Java tests
cd src/main/webui && npm install && npm test   # 40 interface tests
```

| Test | Tests | What it checks |
|---|---|---|
| `NotesResourceTest` | 7 | 401 without a token, 403 without the right role, full lifecycle, 400 validation, OpenAPI, health, metrics |
| `AuthConfigResourceTest` / `AuthConfigConfiguredTest` | 2 + 1 | `GET /api/auth-config` is public and empty without a client; Charm's public endpoints derived from the issuer, never from the internal `GLUONIFY_SERVICE_CHARM_URL` |
| `FileNoteStoreTest` | 6 | new files never renamed, ordering, two replicas on the same folder, unreadable files ignored, identifiers never paths |
| `GraphNoteStoreTest` | 4 | against a fake Gdown: path, authentication, **parameterized** statement, row reading, failure |
| `NoteStoresTest` | 3 | storage selection: `auto` gives `gdown` when `GLUONIFY_GDOWN_DATABASE` is provided, `memory` otherwise; an explicit value wins |
| `WebhookResourceTest` / `WebhookClosedTest` | 4 + 1 | key, acknowledgment, **idempotence**, rejections; closed without a key |
| `PlatformResourceTest` | 4 | platform variables read, **no secret returned**, service-to-service call limited to declared services |
| `ConformityTest` | 4 | the builder's standards, on your side |
| `EventLedgerTest` | 4 | idempotence ledgers: exactly one winner per event (memory, files with two replicas, graph uniqueness constraint, graph error other than a duplicate propagated) |
| `DocumentsResourceTest` | 13 | against the **fake Crystallon of the SDK** (`GluonifyTestServices`): PDF returned, path called (`/v1/templates/by-name/Gluonify%20invoice/render`), the **application's token** (also outside a request), payload sent, mapping of 422 / 502, own validation (400), roles, OpenAPI |
| `DocumentsDisabledTest` | 3 | Crystallon not available: 404 with the reason, `"documents": false`, the rest unaffected |
| `InvoiceMappingTest` / `DocumentErrorsTest` | 3 + 7 | your invoice → the payload of the template (amounts, defaults, safe file name); every Crystallon failure → a status |
| `DocumentsReflectionTest` | 1 | native guard: every record of the generated payload is registered for reflection |
| `CrystallonTemplateFileTest` | 1 | the Crystallon template of the repository (`crystallon/templates/gluonify-invoice.json`): valid JSON, the exact name the generated code calls, the same top-level fields as the generated payload (the `SCHEMA_SHA256` fingerprint is computed by Crystallon only) |
| `DeploySpecsTest` | 2 | the specs of `deploy/` use current names (`uses`, `SOURCE_STORE`); `gluonify.json` (read by the builder) declares only what the SDK cannot detect (`charm`, `gdown`) and, with the inferred `crystallon` (marker on the classpath), gives the same `uses` as `deploy/appspec.json` |

An API test is copied from `NotesResourceTest`; a storage test from `FileNoteStoreTest` (without starting Quarkus: fast).

## 11. Known pitfalls

- **Native**: do **not** create an `HttpClient`, a `Random` or a `SecureRandom` in a `static` field (the state would be frozen at compile time, not at run time): create them on first use (see `GraphNoteStore.client()`). Read third-party JSON as a tree (`JsonNode`) rather than into classes; a type (de)serialized by Jackson outside a REST signature must carry `@RegisterForReflection`. **A bug that only appears in native does not show up in `mvn test`**: run the native executable before shipping.
- **Jackson 3**: the package is `tools.jackson.databind`, not `com.fasterxml.jackson.databind`.
- **No address, no port, no password hardcoded**: everything comes from the environment.
- **Distributed files**: see the rules in [§7](#distributed-files-sourcestorefiles) (never rewrite, ~3 s lag).
- **Webhooks**: answer quickly with 2XX, be idempotent, protect with a key.
- **Generated code**: the files of `documents/generated/` come from the *Code* tab of Crystallon: regenerate them, never edit them by hand; and list every record of a generated payload in `DocumentsReflection` (a failure that only shows in native).
- **A development identity has no business in production**: keep `@IfBuildProfile("dev")`.
- **Quinoa and API addresses**: add any new API prefix to `quarkus.quinoa.ignored-path-prefixes`.
- **Under x86_64 emulation** (Docker on Apple Silicon): build with `--build-arg MAVEN_OPTS=-Djdk.lang.Process.launchMechanism=VFORK`.

## 12. Troubleshooting

| Symptom | Probable cause |
|---|---|
| 401 in production | no token, expired token, or **audience** different from `GLUONIFY_SOURCE_OIDC_AUDIENCE`; or `GLUONIFY_SOURCE_OIDC_ISSUER` does not match the token's `iss` |
| 403 | valid token but without the role (`source:read` / `source:write`) |
| No *Sign in* button, the page names `GLUONIFY_SOURCE_OIDC_CLIENT_ID` | the client id is not set, or Charm's public address is unknown (`GLUONIFY_SOURCE_OIDC_ISSUER` left at `any` and no `GLUONIFY_SOURCE_CHARM_PUBLIC_URL`) |
| Charm refuses the sign-in (`redirect_uri`, `invalid_target`) | the client's redirect is not exactly the address of the interface (trailing slash included), or its audiences do not include `gluonify-source` |
| Signed in, but 403 on the notes | the account lacks `source:read` / `source:write` (give the roles in Charm, then sign out and in again: roles are read at sign-in) |
| The application receives no traffic | `/q/health/ready` does not answer 200 (storage unreachable?): `GET /apps/<name>` shows the ready instances |
| `store=gdown`: "source.graph.url is empty" | the application is not deployed with `"uses": ["gdown"]` |
| Gdown: 401 or "Unsupported property value type" | wrong account; or a **map** stored as a property (see §7) |
| A created note does not appear right away (files) | ~3 s list cache between replicas: refresh |
| The repository build is rejected | an `R-…` rule: the message names it; `mvn test` (`ConformityTest`) shows it on your side |
| `mvn package` cannot find Node | network access to download it, or `-Dquarkus.quinoa=false` to build without the interface |
| The native executable crashes at startup but not on the JVM | state frozen at compile time (`static`) or missing reflection (§11) |
| `POST /api/documents/invoice`: 404 "documents are off" | Crystallon is not available: the application is not deployed with `"uses": ["crystallon"]` (by the builder: SDK older than 0.7.1, or inference disabled), or, on your machine, no `gluonify login <zone>` |
| Documents: 502 "did not accept the token of the application" | the application is not deployed with `"uses": ["crystallon"]`, the platform does not issue application tokens yet, or the token file is missing, empty or stale; on your machine, run `gluonify login <zone>` again |
| Documents: 502 "does not know the template" | no template named `Gluonify invoice` in the organization of the token (the application's token: the organization of the application, or Crystallon's default one): read `templates` in the build of the builder (`GET /v1/builds/<id>`: `failed` with its reason; absent when the application does not use Crystallon or the file is missing); without the builder, import `crystallon/templates/gluonify-invoice.json` in Crystallon's console |
| Builder: template `Gluonify invoice` reported `differs` | the template in Crystallon expects another payload than the file: nothing was written; edit it in the console (or delete it), export it, regenerate the code and commit both |
| Documents: 422 with `code` and `path` | the template cannot render these data: `path` is the place of the problem in the template |
| Documents work on the JVM but fail at the first call in the native executable | a record of the generated payload is missing from `DocumentsReflection` |

---

*Gluonify is a minimalist platform for native Quarkus applications: [gluonify.cloud](https://gluonify.cloud) (overview) and [gluonify.io](https://gluonify.io) (technical). This repository is deliberately small: it can be read in an hour.*
