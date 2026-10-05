# CLAUDE.md: guidance for an AI assistant working on this project

This repository is the **starting point** for a Quarkus 4 service that runs on Gluonify. Read `README.md` first: it explains everything, step by step (also available as `README.fr.md`, `README.es.md`, `README.it.md`, `README.de.md`). This file summarizes what you must not break.

## Commands
- `mvn quarkus:dev`: development (hot reload, UI at http://localhost:8080, **no token needed**: the "dev" identity exists only in this profile).
- `mvn test`: 30 Java tests (REST, stores, webhooks, platform, conformity). The UI is tested separately: `cd src/main/webui && npm test`.
- Native (what Gluonify runs): `docker build --target out --output type=local,dest=dist -f Dockerfile.build .` -> `dist/gluonify-source`.
- Rename the project: `python3 scripts/rename.py <groupId> <artifactId> [<package>]`, then `mvn test`.
- Java 25, Quarkus 4.0.0.Beta1, Jackson 3 (`tools.jackson.databind`, not `com.fasterxml`), GraalVM native (NIK 25).

## Platform rules (Gluonify's builder enforces them; `ConformityTest` checks them on your side)
- `quarkus-smallrye-health` is mandatory (R-SANTE). No plaintext secret in `application.properties`: use `${VARIABLE}` or `${app.key}` (R-SECRET). No `.env`, `*.pem`, `*.p12`, `*.jks` (R-FICHIER-SENSIBLE). No `quarkus-container-image-*` (R-IMAGE). `quarkus.http.host` never on loopback (R-ECOUTE). Native compilation never disabled (R-NATIF).
- **All configuration comes from the environment** (variables provided by the platform or its vault). Never hard-code a service address, a port or a password.

## Known pitfalls (do not fall into them again)
- **Native**: no `HttpClient` and no `SecureRandom`/`Random` in a `static` field (state frozen at build time): create it on first use. Read third-party JSON as a tree (`JsonNode`) rather than into undeclared classes. A type (de)serialized by Jackson outside a REST signature must carry `@RegisterForReflection`.
- **`/distributed/std`**: never rewrite a file or rename a directory that was just written; write NEW files under their final name. A directory listing can lag by ~3 s behind another replica.
- **Photon webhooks**: a 2XX status acknowledges, anything else causes a replay; be idempotent on `X-Gluonify-Event-Id`.
- **Security**: `DevAuthentication` exists only in the `dev` profile (`@IfBuildProfile`). Do not extend it to production. Roles come from the Charm token (`source:read`, `source:write`).
- Code comments are in **French** in this repository; the README exists in five languages (English by default) and this file is English only. Keep commit messages plain.

## Where to change what
`NotesResource` = the model of a REST resource; `NoteStore` + `NoteStores` = where to plug in a store; `SourceConfig` + `application.properties` = configuration; `WebhookResource` = receiving Photon; `PlatformResource` = platform variables and calling another service; `src/main/webui` = the Vue UI (Quinoa).
