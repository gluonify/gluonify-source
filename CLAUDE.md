# CLAUDE.md : consignes pour un assistant IA qui travaille sur ce projet

Ce dépôt est le **point de départ** d'un service Quarkus 4 qui tourne sur Gluonify. Lisez d'abord `README.md` : il explique tout, étape par étape. Ce fichier résume ce qu'il ne faut pas casser.

## Commandes
- `mvn quarkus:dev` : développement (rechargement à chaud, interface sur http://localhost:8080, **aucun jeton nécessaire** : l'identité « dev » existe seulement dans ce profil).
- `mvn test` : 30 tests Java (REST, stockages, webhooks, plateforme, conformité). L'interface se teste à part : `cd src/main/webui && npm test`.
- Natif (ce que Gluonify exécute) : `docker build --target out --output type=local,dest=dist -f Dockerfile.build .` -> `dist/gluonify-source`.
- Renommer le projet : `python3 scripts/rename.py <groupId> <artifactId> [<paquet>]`, puis `mvn test`.
- Java 25, Quarkus 4.0.0.Beta1, Jackson 3 (`tools.jackson.databind`, pas `com.fasterxml`), GraalVM natif (NIK 25).

## Règles de la plateforme (le builder de Gluonify les contrôle ; `ConformityTest` les vérifie chez vous)
- `quarkus-smallrye-health` obligatoire (R-SANTE). Aucun secret en clair dans `application.properties` : `${VARIABLE}` ou `${app.clé}` (R-SECRET). Pas de `.env`, `*.pem`, `*.p12`, `*.jks` (R-FICHIER-SENSIBLE). Pas de `quarkus-container-image-*` (R-IMAGE). `quarkus.http.host` jamais en boucle locale (R-ECOUTE). Compilation native non désactivée (R-NATIF).
- **Toute la configuration vient de l'environnement** (variables fournies par la plateforme ou son coffre). Ne jamais écrire d'adresse de service, de port, de mot de passe en dur.

## Pièges connus (n'y retombez pas)
- **Natif** : pas de `HttpClient` ni de `SecureRandom`/`Random` dans un champ `static` (état figé à la compilation) : créer à la première utilisation. Lire le JSON de tiers en arbre (`JsonNode`) plutôt que dans des classes non déclarées. Un type (dé)sérialisé par Jackson hors d'une signature REST doit porter `@RegisterForReflection`.
- **`/distributed/std`** : ne jamais réécrire un fichier ni renommer un dossier qui vient d'être écrit ; écrire de NOUVEAUX fichiers sous leur nom définitif. La liste d'un dossier peut avoir ~3 s de retard sur une autre réplique.
- **Webhooks de Photon** : un code 2XX acquitte, le reste fait rejouer ; soyez idempotent sur `X-Gluonify-Event-Id`.
- **Sécurité** : `DevAuthentication` n'existe qu'en profil `dev` (`@IfBuildProfile`). Ne l'étendez pas à la production. Les rôles viennent du jeton Charm (`source:read`, `source:write`).
- Documentation et commentaires en **français** dans ce dépôt, messages de commit sobres.

## Où modifier quoi
`NotesResource` = le modèle d'une ressource REST ; `NoteStore` + `NoteStores` = où brancher un stockage ; `SourceConfig` + `application.properties` = la configuration ; `WebhookResource` = recevoir Photon ; `PlatformResource` = variables de la plateforme et appel d'un autre service ; `src/main/webui` = l'interface Vue (Quinoa).
