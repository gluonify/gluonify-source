# gluonify-source

[English](README.md) · **Français** · [Español](README.es.md) · [Italiano](README.it.md) · [Deutsch](README.de.md)

**Le point de départ d'un service Quarkus 4 sur [Gluonify](https://gluonify.cloud).** Un petit service complet (des « notes ») qui montre, avec du code qui tourne et des tests, comment :

1. faire un **service REST** (validation, rôles, OpenAPI) ;
2. faire une **interface web** avec [Quinoa](https://quarkus.io/extensions/io.quarkiverse.quinoa/quarkus-quinoa/) (Vue 3, servie par le même exécutable) ;
3. **s'interfacer avec les services de Gluonify** : configuration et secrets (Top), jetons d'identité (Charm), données (Gdown) ou fichiers distribués, autres applications, webhooks reçus de Photon, sites statiques (Field), domaines et politiques (Higgs) ;
4. le **construire en exécutable natif** et le **déployer**.

Il s'adresse à un développeur ou à **un assistant IA** qui démarre un service de zéro : chaque choix est expliqué ici, chaque ligne de configuration est commentée dans le code, et [`CLAUDE.md`](CLAUDE.md) résume les règles pour une IA.

> Gluonify exécute des **applications Quarkus natives** (un fichier exécutable, sans JVM) sur des machines Debian. Vous donnez accès à votre code sur Git ; la plateforme le construit, le déploie, le sécurise et le fait tourner.

## Sommaire
1. [Démarrer en 5 minutes](#1-démarrer-en-5-minutes)
2. [La carte du projet](#2-la-carte-du-projet)
3. [Renommer le projet](#3-renommer-le-projet)
4. [Faire un service REST](#4-faire-un-service-rest)
5. [Faire une interface avec Quinoa](#5-faire-une-interface-avec-quinoa)
6. [Authentification : les jetons de Charm](#6-authentification--les-jetons-de-charm)
7. [S'interfacer avec les services de Gluonify](#7-sinterfacer-avec-les-services-de-gluonify)
8. [Construire](#8-construire)
9. [Déployer](#9-déployer)
10. [Tester](#10-tester)
11. [Pièges connus](#11-pièges-connus)
12. [Dépannage](#12-dépannage)

---

## 1. Démarrer en 5 minutes

**Prérequis** : Java 25 (`JAVA_HOME`), Maven 3.9+. Node.js n'est pas nécessaire : Quinoa télécharge le sien (v24.3.0) à la première construction. Docker n'est utile que pour l'exécutable natif.

```bash
git clone https://github.com/gluonify/gluonify-source.git
cd gluonify-source
mvn quarkus:dev
```

Ouvrez <http://localhost:8080> : l'interface (ajouter, lister, supprimer des notes). Aussi :

| Adresse | Contenu |
|---|---|
| `/` | l'interface Vue |
| `/api/notes` | l'API REST (JSON) |
| `/q/swagger-ui` | la documentation interactive de l'API |
| `/q/openapi` | le contrat OpenAPI (JSON ou YAML) |
| `/q/health/ready` · `/q/health/live` | santé : « prêt » et « vivant » (voir [§7](#7-sinterfacer-avec-les-services-de-gluonify)) |
| `/q/metrics` | mesures Prometheus |

En **développement**, aucun jeton n'est demandé : une identité « dev » (avec tous les rôles) n'existe que dans ce profil (`@IfBuildProfile("dev")`, absente de l'exécutable de production). Les notes sont **en mémoire** (`source.store=memory`) : elles disparaissent à l'arrêt.

```bash
curl -s localhost:8080/api/notes -H 'Content-Type: application/json' -d '{"title":"Courses","body":"du pain"}'
curl -s localhost:8080/api/notes
```

## 2. La carte du projet

```
pom.xml                         projet Maven AUTONOME (Quarkus 4.0.0.Beta1, Java 25) : aucun parent Gluonify
Dockerfile.build                construit l'exécutable natif dans Docker
src/main/resources/
  application.properties        TOUTE la configuration, commentée ligne par ligne
src/main/java/io/gluonify/source/
  SourceConfig.java             la configuration de l'application (préfixe « source. »)
  notes/
    Note.java, NewNote.java     le modèle (records) et le corps de création (validé)
    NotesResource.java          ← LE MODÈLE D'UNE RESSOURCE REST (à copier)
    NoteStore.java              l'interface du stockage ; trois réalisations :
    MemoryNoteStore.java          en mémoire (développement)
    FileNoteStore.java            fichiers dans /distributed/std (durable, partagé par les répliques)
    GraphNoteStore.java           Gdown, la base graphe de la plateforme
    NoteStores.java             choisit la réalisation (source.store) : l'endroit où brancher la vôtre
    StoreHealth.java            la condition « prêt » (/q/health/ready)
  platform/
    PlatformResource.java       variables fournies par la plateforme ; appel d'une autre application
    WebhookResource.java        recevoir les livraisons de Photon
  security/DevAuthentication.java   identité de développement (profil dev seulement)
src/main/webui/                 l'interface Vue 3 (Vite + vitest), construite par Quinoa
src/test/java/…                 34 tests Java ; src/main/webui/src/App.test.js : 3 tests d'interface
deploy/                         exemples : AppSpec, domaine, webhook Photon, base Gdown
scripts/rename.py               renomme le projet
```

## 3. Renommer le projet

Votre service ne s'appelle pas `gluonify-source`. Une commande change les coordonnées Maven, le paquet Java (dossiers compris), le nom de l'exécutable, le titre OpenAPI et l'**audience** attendue des jetons :

```bash
python3 scripts/rename.py com.acme shop-api com.acme.shop
mvn test
```

(`groupId` `com.acme`, `artifactId` `shop-api`, paquet Java `com.acme.shop`. Le script se relance sans risque.) Le nom de l'artefact devient aussi le nom de l'application sur Gluonify ; choisissez-le comme un nom DNS : minuscules, chiffres, tirets.

## 4. Faire un service REST

Lisez [`NotesResource.java`](src/main/java/io/gluonify/source/notes/NotesResource.java) : c'est le modèle. Pour ajouter **votre** ressource, par exemple des commandes :

1. **Le modèle** : un `record` Java (immuable, Jackson le lit et l'écrit, OpenAPI le décrit).
   ```java
   public record Order(String id, String customer, int quantity) {}
   public record NewOrder(@NotBlank String customer, @Min(1) int quantity) {}   // validé
   ```
2. **La ressource** : une classe avec `@Path`, des méthodes `@GET`/`@POST`…, les rôles avec `@RolesAllowed`, la validation avec `@Valid`.
   ```java
   @Path("/api/orders")
   @Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
   public class OrdersResource {
       @POST @RolesAllowed("shop:write")
       public Response create(@Valid NewOrder in) { … return Response.created(uri).entity(order).build(); }
   }
   ```
3. **La documentation** : `@Operation(summary = "…")` et `@Tag(name = "…")` (microprofile-openapi) : elles apparaissent dans `/q/swagger-ui`. Le contrat est régénéré à chaque démarrage.
4. **Les tests** : copiez [`NotesResourceTest`](src/test/java/io/gluonify/source/NotesResourceTest.java). `@TestSecurity(user = "ada", roles = {"shop:write"})` fixe l'identité d'un test ; sans annotation, c'est un appel sans jeton (attendu : 401).
5. **Les codes HTTP** : 201 + `Location` à la création, 204 sans corps, 400 pour un corps invalide (Hibernate Validator), 401 sans jeton, 403 sans le bon rôle, 404 pour un identifiant inconnu. Les erreurs renvoient `{"error": "…"}`.

**Le stockage** est derrière l'interface [`NoteStore`](src/main/java/io/gluonify/source/notes/NoteStore.java) : la ressource ne sait pas où vivent les données. Pour votre propre stockage (une autre base, un autre dossier), implémentez l'interface et ajoutez un cas dans [`NoteStores`](src/main/java/io/gluonify/source/notes/NoteStores.java).

## 5. Faire une interface avec Quinoa

[Quinoa](https://quarkus.io/extensions/io.quarkiverse.quinoa/quarkus-quinoa/) construit un projet web (ici Vue 3 + Vite, dans `src/main/webui`) et le sert **par le même exécutable** que l'API : un seul fichier à déployer, une seule origine (pas de CORS).

- **Développer** : `mvn quarkus:dev` lance aussi le serveur Vite (port 5173, rechargement à chaud de l'interface) ; Quarkus sert l'API. Ouvrez <http://localhost:8080>.
- **Appeler l'API** : des adresses relatives (`fetch('/api/notes')`), voir [`src/api.js`](src/main/webui/src/api.js). Le jeton Charm est collé par l'utilisateur et gardé pour l'onglet (`sessionStorage`).
- **Les routes de l'interface** : `quarkus.quinoa.enable-spa-routing=true` renvoie `index.html` pour une adresse inconnue (utile avec vue-router). Mais **une adresse d'API inconnue ne doit pas renvoyer l'interface** : `quarkus.quinoa.ignored-path-prefixes=/api,/hooks,/q` (déjà réglé). Si vous ajoutez un préfixe d'API, ajoutez-le ici.
- **Construire** : `mvn package` exécute `npm install` puis `npm run build` ; `dist/` est embarqué dans l'exécutable. Sans interface : `-Dquarkus.quinoa=false`.
- **Tester l'interface** : `cd src/main/webui && npm install && npm test` (vitest + jsdom, `fetch` simulé : voir `App.test.js`). Avec `quarkus.quinoa.run-tests=true`, `mvn package` les lance aussi.
- **Mettre en cache** : les fichiers de `dist/assets` ont une empreinte dans leur nom (immuables) ; `index.html` est servi avec `Cache-Control: no-cache` (réglage `ui-entry`).

Une autre technologie (React, Svelte…) : remplacez le contenu de `src/main/webui` ; Quinoa ne demande qu'un `package.json` avec les scripts `dev` et `build` et un dossier de sortie (`quarkus.quinoa.build-dir`).

## 6. Authentification : les jetons de Charm

L'API est **fermée sans jeton**. Elle attend un jeton porteur (JWT) émis par **gluonify-charm**, le fournisseur d'identité de Gluonify (OpenID Connect, ES256) :

```
Authorization: Bearer <jeton>
```

Le service ne **crée** jamais de jeton : il en **vérifie** (mode `quarkus.oidc.application-type=service`). Ce qu'il contrôle :

| Contrôle | Réglage | Valeur |
|---|---|---|
| Signature | clés de Charm, lues à `${SERVICE_ID_URL}/realms/gluonify` | `SERVICE_ID_URL` est fournie par la plateforme si l'application est déployée avec `"uses": ["id"]` |
| Émetteur (`iss`) | `quarkus.oidc.token.issuer` | `OIDC_ISSUER` = `https://id.<votre zone>/realms/gluonify` (stable : ce n'est pas l'adresse de l'instance de Charm) |
| Audience (`aud`) | `quarkus.oidc.token.audience` | `OIDC_AUDIENCE`, par défaut le nom du projet (`gluonify-source`) |
| Expiration | automatique | jetons courts |
| Rôles | claim `roles` | `source:read` (lire), `source:write` (écrire) : voir `@RolesAllowed` |

**Obtenir un jeton de test** (l'administrateur de la plateforme, avec le jeton d'administration de Charm) :

```bash
curl -s -X POST "$CHARM_URL/v1/tokens" -H "Authorization: Bearer $ID_ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"sub":"ada","audience":"gluonify-source","ttlSeconds":3600,"roles":["source:read","source:write"]}'
# -> {"token": "eyJ…"}
curl -s https://gluonify-source.<zone>/api/notes -H "Authorization: Bearer eyJ…"
```

Une valeur d'audience différente (`"audience": "autre"`) donne **401** ; un jeton valide sans le bon rôle donne **403**.

**En local**, le profil `dev` fournit l'identité « dev » (voir [`DevAuthentication`](src/main/java/io/gluonify/source/security/DevAuthentication.java)) ; **dans les tests**, `@TestSecurity`. Jamais d'identité de ce genre en production : elle est compilée seulement dans le profil `dev`.

## 7. S'interfacer avec les services de Gluonify

Une application sur Gluonify est **isolée** : son propre compte, son propre réseau, un système de fichiers réduit. Elle ne joint que ce qu'elle **déclare** (`"uses"`) et reçoit sa configuration **par l'environnement**. Voici chaque service, avec l'endroit du code.

| Service | Ce qu'il offre | Comment l'application s'en sert | Dans ce dépôt |
|---|---|---|---|
| **Top** (coffre) | configuration et secrets par application | avec `"vault": true`, les clés de l'espace `<uuid>.app` propre à votre application arrivent en `APP_<CLÉ>` ; `${app.graph.password}` dans `application.properties` | `source.graph.password`, `source.webhook.key` |
| **Charm** (identité) | jetons JWT courts | `"uses": ["id"]` → `SERVICE_ID_URL` ; `quarkus-oidc` vérifie les jetons | [§6](#6-authentification--les-jetons-de-charm) |
| **Gdown** (base graphe) | base répliquée (Raft), Cypher par HTTP | `"uses": ["graphdb"]` → `SERVICE_GRAPHDB_URL` ; un compte local de Gdown | [`GraphNoteStore`](src/main/java/io/gluonify/source/notes/GraphNoteStore.java) |
| **Fichiers distribués** | `/distributed/std` : durable, partagé par toutes les répliques, 2 copies | `"distributed": ["std"]` au déploiement | [`FileNoteStore`](src/main/java/io/gluonify/source/notes/FileNoteStore.java) |
| **Autres applications** | appel de service à service | `"uses": ["autre"]` → `SERVICE_AUTRE_URL` | [`PlatformResource.ping`](src/main/java/io/gluonify/source/platform/PlatformResource.java) |
| **Photon** (passerelle d'API) | webhooks signés, conversion XML / SOAP / formulaire → JSON, rejeu | Photon livre à `…/hooks/events` ; votre service acquitte en 2XX | [`WebhookResource`](src/main/java/io/gluonify/source/platform/WebhookResource.java) |
| **Field** | sites statiques (ZIP) servis sur votre domaine | rien à coder : un site se dépose en ZIP | — |
| **Higgs** (orchestrateur) | domaines, certificats HTTPS automatiques, routes, politiques (jeton, limites, CORS, adresses) | `PUT /domains/<nom>` | `deploy/domain.json` |

### Configuration et secrets (Top)

- Une valeur **non secrète** : dans `"env"` du déploiement (`deploy/appspec.json`) : `"SOURCE_STORE": "files"`. Elle est stockée dans l'état du cluster.
- Une valeur **secrète** (mot de passe, clé d'API) : dans le **coffre** (Top, espace `<uuid>.app` de votre application). La clé `GRAPH_PASSWORD` arrive en variable `APP_GRAPH_PASSWORD` et se lit `${app.graph.password}`. Demandez cet espace avec `"vault": true` dans la spécification de l'application : seul cet espace `<uuid>.app`, attribué par le plan de contrôle, ajoute le préfixe `APP_`. Avec un `"vaultNamespace"` littéral (par exemple `"gluonify-source"`, seule option du builder), les clés arrivent **sans préfixe**, telles que nommées dans le coffre (`GRAPH_PASSWORD` reste `GRAPH_PASSWORD`) : nommez-les d'après les propriétés lues (par exemple `SOURCE_GRAPH_PASSWORD`). **Jamais** dans le dépôt : le builder refuse un mot de passe en clair (règle R-SECRET), et `ConformityTest` vous le dit avant.
- **Enregistrer une valeur ne redémarre rien** : après avoir modifié plusieurs clés, déclenchez **un** redéploiement (`POST /apps/<nom>/redeploy`) ; les répliques redémarrent une à une, sans coupure si vous en avez deux ou plus.
- Variables que la plateforme ajoute **toujours** : `QUARKUS_HTTP_PORT` et `QUARKUS_HTTP_HOST`, `ENV_NAME` (l'environnement : SBX, QUA, PRD…), `ENV_NODE` (rang de la réplique : 1, 2…), `GLUONIFY_SELF_URL` (adresse de cette instance). Elles sont lues et affichées par [`PlatformResource`](src/main/java/io/gluonify/source/platform/PlatformResource.java) (`GET /api/platform`, jamais de secret).

### Santé : `/q/health/ready` et `/q/health/live`

La plateforme n'envoie du trafic qu'aux instances dont **`/q/health/ready`** répond 200, et redémarre celles dont **`/q/health/live`** échoue de façon répétée. `quarkus-smallrye-health` est **obligatoire** (le builder le contrôle : règle R-SANTE). [`StoreHealth`](src/main/java/io/gluonify/source/notes/StoreHealth.java) rend l'instance « prête » seulement si le stockage répond. Gardez « vivant » indépendant des services extérieurs : une panne de Gdown ne doit pas faire redémarrer l'application en boucle.

### Données dans Gdown (`source.store=graph`)

1. Déployez avec `"uses": ["graphdb"]` (c'est aussi ce qui **ouvre le réseau** vers Gdown) et `SOURCE_STORE=graph`.
2. Un administrateur crée la base et le compte de l'application ([`deploy/gdown-setup.cypher`](deploy/gdown-setup.cypher) : base `source`, rôle confiné, compte `notes`).
3. Mettez `GRAPH_USER` (= `notes`) et `GRAPH_PASSWORD` dans le coffre.

L'API HTTP de Gdown : `POST <url>/db/<base>/query` avec `{"statement": "…", "parameters": {…}}` et une authentification de base ; réponse `{"columns": […], "rows": [[…]], "stats": {…}}`. **Toujours des paramètres** (`$id`, `$title`), jamais de valeur collée dans l'instruction (injection). Une **carte** n'est pas stockable comme propriété d'un nœud : stockez des scalaires, des listes de scalaires, ou le JSON en texte. Gdown peut aussi publier des requêtes Cypher nommées comme API REST (contrats OpenAPI, jetons, limites) : voir [gluonify.io](https://gluonify.io).

### Fichiers distribués (`source.store=files`)

Déployez avec `"distributed": ["std"]` : `/distributed/std` apparaît, partagé par toutes les répliques sur tous les nœuds, avec 2 copies sur les nœuds de stockage. **Règles** (elles expliquent la forme de [`FileNoteStore`](src/main/java/io/gluonify/source/notes/FileNoteStore.java)) :

1. **Ne jamais réécrire un fichier ni renommer un dossier qui vient d'être écrit** : écrivez de **nouveaux** fichiers sous leur nom définitif (c'est pourquoi une note est immuable) ; supprimer est permis.
2. **La liste d'un dossier peut avoir ~3 secondes de retard** sur une autre réplique : une note créée sur la réplique A peut mettre un moment à apparaître sur B.
3. Un fichier devient visible **à sa fermeture** ; tolérez quand même un fichier illisible.
4. Pas de verrous entre nœuds par défaut (option `distributedLocks`).
5. **Un corps de requête HTTP se lit jusqu'au bout**, sinon la connexion réutilisée se bloque.

### Recevoir les webhooks de Photon

Photon reçoit les messages de vos partenaires (vérifie leur signature HMAC, convertit XML, SOAP ou formulaire en JSON) puis les **livre** à votre service. Contrat de livraison :

- **un code 2XX acquitte** ; tout autre code (ou une panne) fait **rejouer** selon la politique du webhook (`retry`), puis met en file morte ;
- donc **répondez vite** et soyez **idempotent** : « au moins une fois » signifie qu'un même message peut arriver deux fois. L'en-tête `X-Gluonify-Event-Id` est la clé de déduplication ; `X-Gluonify-Delivery-Attempt` compte les essais ; `X-Gluonify-Webhook-Id` nomme le webhook. La mémoire n'est **pas** partagée entre réplicas : ici les identifiants sont conservés dans le stockage choisi par `source.store` (mémoire, un fichier par événement créé avec `CREATE_NEW` dans `/distributed/std/events`, ou un nœud protégé par une contrainte d'unicité dans Gdown), si bien qu'une seule réplique accepte un événement.
- Photon n'envoie **pas** de jeton Charm : protégez le point d'entrée par une clé que vous mettez dans la cible du webhook (`"headers": {"X-Api-Key": "…"}`, voir [`deploy/photon-webhook.json`](deploy/photon-webhook.json)) et dans le coffre (`WEBHOOK_KEY`). **Sans clé configurée, le récepteur est fermé** (404).

[`WebhookResource`](src/main/java/io/gluonify/source/platform/WebhookResource.java) montre le tout (clé comparée en temps constant, déduplication, JSON illisible → 400) ; remplacez le corps de `receive` par votre traitement.

### Appeler une autre application

Déclarez-la dans `"uses"` : la plateforme fournit `SERVICE_<APP>_URL` **et ouvre le réseau** vers elle (sans `uses`, l'application ne la joint pas). N'écrivez jamais l'adresse en dur : lisez la variable (voir `ping` dans [`PlatformResource`](src/main/java/io/gluonify/source/platform/PlatformResource.java)).

### Sites statiques et domaines

- **Field** sert un site statique (ZIP) sur votre domaine ; rien à coder dans ce service.
- **Votre domaine** : `PUT /domains/<nom>` sur l'API de Higgs (voir [`deploy/domain.json`](deploy/domain.json)) route un chemin vers l'application et applique des **politiques** : jeton Charm (audience, rôles), limites de débit, CORS, listes d'adresses. Le certificat HTTPS est obtenu automatiquement.

## 8. Construire

```bash
mvn test                       # tests Java (34)
mvn package                    # JVM : target/quarkus-app/ ; construit aussi l'interface (Quinoa)
```

**L'exécutable natif** (ce que Gluonify exécute : un fichier, sans JVM) :

```bash
docker build --target out --output type=local,dest=dist -f Dockerfile.build .
./dist/gluonify-source         # tourne sur ce Linux, port 8080
```

Le fichier `dist/gluonify-source` est un exécutable Linux **glibc** de l'architecture de votre Docker (arm64 sur Apple Silicon, amd64 sur x86_64). Pour l'autre architecture : `docker build --platform linux/amd64 …` (lent : émulation ; voir les commentaires de `Dockerfile.build`).

Sans Docker : `mvn package -Dnative` exige GraalVM (NIK 25) installé.

**Les normes de la plateforme** : après le clone, le builder de Gluonify contrôle le projet (`R-SANTE`, `R-SECRET`, `R-FICHIER-SENSIBLE`, `R-NATIF`, `R-IMAGE`, `R-ECOUTE`) et refuse un dépôt qui ne les respecte pas. [`ConformityTest`](src/test/java/io/gluonify/source/ConformityTest.java) les vérifie **chez vous** ; gardez-le.

## 9. Déployer

Il y a deux chemins.

**A. Gluonify construit pour vous** (le plus simple) : donnez l'adresse Git au builder (**gluonify-bup**) :

```bash
curl -X POST "$BUP_URL/v1/builds" -H 'Content-Type: application/json' -H "X-Git-Token: $GIT_TOKEN" \
  -d '{"name":"gluonify-source","gitUrl":"https://github.com/VOUS/VOTRE-DEPOT.git","ref":"main","deploy":true,"replicas":2,"memoryMb":128,"vaultNamespace":"gluonify-source"}'
```

Le builder clone, contrôle la conformité, compile en natif, publie l'exécutable et le déploie. Suivez-le : `GET /v1/builds/<id>` et `/logs`. (Un push Git peut aussi lancer le build par un webhook : `POST /v1/webhooks/git`.) Remarque : le builder ne connaît que `vaultNamespace`, donc avec lui les clés du coffre arrivent sans préfixe.

**B. Vous publiez l'exécutable** vous-même, puis vous déployez :

```bash
SHA=$(shasum -a 256 dist/gluonify-source | cut -d' ' -f1)
# publiez dist/gluonify-source à une adresse HTTPS, puis :
curl -X PUT "https://api.$ZONE/apps/gluonify-source" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d @deploy/appspec.json     # après avoir mis votre adresse et votre empreinte dans artifacts
```

L'application est alors à `https://gluonify-source.<zone>` (certificat automatique). Les champs de [`deploy/appspec.json`](deploy/appspec.json) :

| Champ | Rôle |
|---|---|
| `artifacts` | l'exécutable par architecture : `URL#sha256=…` (l'empreinte est **obligatoire**, vérifiée par le nœud) |
| `replicas` | nombre d'instances (2 ou plus : mises à jour sans coupure) |
| `memoryMb` | plafond mémoire par instance (un natif tient dans 64 à 128 Mo) |
| `uses` | services joignables : `SERVICE_<APP>_URL` fournie, réseau ouvert (`id` = Charm, `graphdb` = Gdown) |
| `distributed` | `["std"]` pour `/distributed/std` |
| `env` | variables **non secrètes** ; `${NOM}` et `${NOM:-défaut}` sont résolus |
| `vault` | `true` : le plan de contrôle attribue à l'application son propre espace de coffre `<uuid>.app`, dont les clés arrivent en `APP_<CLÉ>` |
| `vaultNamespace` | alternative : un espace de coffre existant, par son nom ; ses clés arrivent **sans préfixe** (pas de `APP_`) |
| `internal` | `true` : aucune route publique (service interne) |

Mettre à jour : même `PUT` (ou nouveau build) ; les répliques sont remplacées **une à une**. Redémarrer sans changer : `POST /apps/<nom>/redeploy`. Journaux : `GET /apps/<nom>/logs`. Mesures : `/q/metrics`.

## 10. Tester

```bash
mvn test                                 # 34 tests Java
cd src/main/webui && npm install && npm test   # 3 tests d'interface
```

| Test | Ce qu'il vérifie |
|---|---|
| `NotesResourceTest` | 401 sans jeton, 403 sans le bon rôle, cycle de vie complet, validation 400, OpenAPI, santé, mesures |
| `FileNoteStoreTest` | fichiers neufs jamais renommés, ordre, deux répliques sur le même dossier, fichiers illisibles ignorés, identifiants jamais des chemins |
| `GraphNoteStoreTest` | contre un faux Gdown : chemin, authentification, instruction **paramétrée**, lecture des lignes, échec |
| `WebhookResourceTest` / `WebhookClosedTest` | clé, acquittement, **idempotence**, rejets ; fermé sans clé |
| `PlatformResourceTest` | variables de la plateforme lues, **aucun secret renvoyé**, appel de service à service limité aux services déclarés |
| `ConformityTest` | les normes du builder, chez vous |

Un test d'API se copie de `NotesResourceTest` ; un test de stockage de `FileNoteStoreTest` (sans démarrer Quarkus : rapide).

## 11. Pièges connus

- **Natif** : ne créez **pas** de `HttpClient`, de `Random` ni de `SecureRandom` dans un champ `static` (l'état serait figé à la compilation, pas à l'exécution) : créez-les à la première utilisation (voir `GraphNoteStore.client()`). Lisez le JSON de tiers en arbre (`JsonNode`) plutôt que dans des classes ; un type (dé)sérialisé par Jackson hors d'une signature REST doit porter `@RegisterForReflection`. **Un bogue qui n'apparaît qu'en natif ne se voit pas dans `mvn test`** : lancez l'exécutable natif avant de livrer.
- **Jackson 3** : le paquet est `tools.jackson.databind`, pas `com.fasterxml.jackson.databind`.
- **Aucune adresse, aucun port, aucun mot de passe en dur** : tout vient de l'environnement.
- **Fichiers distribués** : voir les règles du [§7](#fichiers-distribués-sourcestorefiles) (ne jamais réécrire, ~3 s de retard).
- **Webhooks** : répondre vite en 2XX, être idempotent, protéger par une clé.
- **Une identité de développement n'a rien à faire en production** : gardez `@IfBuildProfile("dev")`.
- **Quinoa et les adresses d'API** : ajoutez tout nouveau préfixe d'API à `quarkus.quinoa.ignored-path-prefixes`.
- **Sous émulation x86_64** (Docker sur Apple Silicon) : construisez avec `--build-arg MAVEN_OPTS=-Djdk.lang.Process.launchMechanism=VFORK`.

## 12. Dépannage

| Symptôme | Cause probable |
|---|---|
| 401 en production | pas de jeton, jeton expiré, ou **audience** différente de `OIDC_AUDIENCE` ; ou `OIDC_ISSUER` ne correspond pas à l'`iss` du jeton |
| 403 | jeton valide mais sans le rôle (`source:read` / `source:write`) |
| L'application ne reçoit pas de trafic | `/q/health/ready` ne répond pas 200 (stockage injoignable ?) : `GET /apps/<nom>` montre les instances prêtes |
| `store=graph` : « source.graph.url is empty » | l'application n'est pas déployée avec `"uses": ["graphdb"]` |
| Gdown : 401 ou « Unsupported property value type » | mauvais compte ; ou une **carte** stockée comme propriété (voir §7) |
| Une note créée n'apparaît pas tout de suite (fichiers) | cache de liste de ~3 s entre répliques : rafraîchir |
| Le build du dépôt est refusé | une règle `R-…` : le message la nomme ; `mvn test` (`ConformityTest`) la montre chez vous |
| `mvn package` ne trouve pas Node | accès réseau pour le télécharger, ou `-Dquarkus.quinoa=false` pour construire sans l'interface |
| Le natif plante au démarrage mais pas en JVM | un état figé à la compilation (`static`) ou une réflexion manquante (§11) |

---

*Gluonify est une plateforme minimaliste pour applications Quarkus natives : [gluonify.cloud](https://gluonify.cloud) (présentation) et [gluonify.io](https://gluonify.io) (technique). Ce dépôt est volontairement petit : il se lit en une heure.*
