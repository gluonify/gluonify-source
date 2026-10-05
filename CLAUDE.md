# CLAUDE.md: guidance for an AI assistant working on this project

This repository is the **starting point** for a Quarkus 4 service that runs on Gluonify. Read `README.md` first: it explains everything, step by step (also available as `README.fr.md`, `README.es.md`, `README.it.md`, `README.de.md`). This file summarizes what you must not break.

## Règles des projets

- **README.md est tenu à jour à chaque modification.** Toute évolution qui ajoute, change ou retire une fonctionnalité, une limite, une option de configuration ou un format met à jour dans le même
  changement : la matrice « Ce qui est pris en charge, et ce qui ne l'est pas encore », la section qui détaille la fonctionnalité, le tableau de configuration et `plan.md`.
- Réponses et documentation en français. Ne committer que sur demande explicite, ou sous le mandat permanent ci-dessous.
- **Enchaîner sans s'arrêter.** Quand l'utilisateur a dit de terminer le plan « tout seul » (mandat permanent en vigueur : « termine le plan tout seul », « continue sans me demander quoi faire »), ne jamais finir un
  tour par un point d'étape, une question ou « je continue ensuite » : passer au point suivant de `plan.md` dans le même tour. Ne s'arrêter que si le backlog est vide, ou si une décision appartient vraiment à
  l'utilisateur (donnée manquante, action irréversible ou hors périmètre) ; dans ce cas, poser la question une fois, avec une recommandation.
- **Attendre dans le tour, pas entre deux tours.** Une vérification longue (`mvn clean verify`, `scripts/e2e-cluster.sh`, endurance) se lance en arrière-plan, puis on attend son résultat dans le même tour
  (boucle `until … ; do sleep 30; done` en arrière-plan avec un délai maximal de 7 200 000 ms, relancée si elle expire), sans clore le tour pendant l'attente. Une notification « terminé » d'une tâche de fond ne
  dit que le lancement est fini, pas que les tests ont réussi : lire le fichier de résultat.
- **Commits sous mandat permanent** : après chaque étape, si `mvn clean verify` et `scripts/e2e-cluster.sh` passent tous les deux (même commande, `JAVA_HOME` du JDK Liberica 25 défini), committer et pousser sur
  `main` sans demander ; sinon corriger. Les messages de commit finissent par la ligne d'attribution demandée par l'environnement.
- Ne pas modifier `/Users/a/dev/gluonify-gdown` sans accord car un autre Claude travaille dessus par défaut.

## Commands
- `mvn quarkus:dev`: development (hot reload, UI at http://localhost:8080, **no token needed**: the "dev" identity exists only in this profile).
- `mvn test`: 34 Java tests (REST, stores, webhooks, platform, conformity). The UI is tested separately: `cd src/main/webui && npm test`.
- Native (what Gluonify runs): `docker build --target out --output type=local,dest=dist -f Dockerfile.build .` -> `dist/gluonify-source`.
- Rename the project: `python3 scripts/rename.py <groupId> <artifactId> [<package>]`, then `mvn test`.
- Java 25, Quarkus 4.0.0.Beta1, Jackson 3 (`tools.jackson.databind`, not `com.fasterxml`), GraalVM native (NIK 25).

## Platform rules (Gluonify's builder enforces them; `ConformityTest` checks them on your side)
- `quarkus-smallrye-health` is mandatory (R-SANTE). No plaintext secret in `application.properties`: use `${VARIABLE}` or `${app.key}` (R-SECRET). No `.env`, `*.pem`, `*.p12`, `*.jks` (R-FICHIER-SENSIBLE). No `quarkus-container-image-*` (R-IMAGE). `quarkus.http.host` never on loopback (R-ECOUTE). Native compilation never disabled (R-NATIF).
- **All configuration comes from the environment** (variables provided by the platform or its vault). Never hard-code a service address, a port or a password.

## Known pitfalls (do not fall into them again)
- **Native**: no `HttpClient` and no `SecureRandom`/`Random` in a `static` field (state frozen at build time): create it on first use. Read third-party JSON as a tree (`JsonNode`) rather than into undeclared classes. A type (de)serialized by Jackson outside a REST signature must carry `@RegisterForReflection`.
- **`/distributed/std`**: never rewrite a file or rename a directory that was just written; write NEW files under their final name. A directory listing can lag by ~3 s behind another replica.
- **Photon webhooks**: a 2XX status acknowledges, anything else causes a replay; be idempotent on `X-Gluonify-Event-Id`, across ALL replicas (`EventLedger`: never an in-memory map alone; files = `CREATE_NEW` of a new file, graph = uniqueness constraint).
- **Vault**: the `APP_` prefix exists only in the `<uuid>.app` space obtained with `"vault": true` in the app spec; with a literal `vaultNamespace` keys arrive unprefixed.
- **Security**: `DevAuthentication` exists only in the `dev` profile (`@IfBuildProfile`). Do not extend it to production. Roles come from the Charm token (`source:read`, `source:write`).
- Code comments are in **English** in this repository; the README exists in five languages (English by default) and this file is English only. Keep commit messages plain.

## Where to change what
`NotesResource` = the model of a REST resource; `NoteStore` + `NoteStores` = where to plug in a store; `SourceConfig` + `application.properties` = configuration; `WebhookResource` = receiving Photon; `PlatformResource` = platform variables and calling another service; `src/main/webui` = the Vue UI (Quinoa).
- **Multilingual Vue JS interfaces.** Every Vue JS interface is multilingual: no hard-coded text in components, translations live in language files (English by default, then fr, es, it, de like the websites), with a language selector whose choice is remembered. Test fixtures and example data are in English.
