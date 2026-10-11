# gluonify-source

[English](README.md) · **Français** · [Español](README.es.md) · [Italiano](README.it.md) · [Deutsch](README.de.md)

> **Variante Gradle.** Le même service, construit avec **Gradle** (Kotlin DSL) au lieu de Maven, existe dans [`gluonify-source-gradle`](https://github.com/gluonify/gluonify-source-gradle) : même code, mêmes tests, mêmes règles de plateforme.

**Le point de départ d'un service Quarkus 4 sur [Gluonify](https://gluonify.cloud).** Un petit service complet (des « notes ») qui montre, avec du code qui tourne et des tests, comment :

1. faire un **service REST** (validation, rôles, OpenAPI) ;
2. faire une **interface web** avec [Quinoa](https://quarkus.io/extensions/io.quarkiverse.quinoa/quarkus-quinoa/) (Vue 3, servie par le même exécutable) ;
3. **s'interfacer avec les services de Gluonify** : configuration et secrets (Top), jetons d'identité (Charm), données (Gdown) ou fichiers distribués, autres applications, webhooks reçus de Photon, documents PDF (Crystallon), sites statiques (Field), domaines et politiques (Higgs) ;
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
| `/api/documents/invoice` | `POST` : une facture en PDF, produite par Crystallon quand il est configuré (voir [§7](#documents-crystallon)) |
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
gluonify.json                   lu par le builder de Gluonify (Bup) : les services à déclarer quand il déploie (`uses`), comme dans deploy/appspec.json
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
  documents/                    documents PDF avec Crystallon (facultatif)
    DocumentsResource.java      POST /api/documents/invoice : votre facture en entrée, un PDF en sortie
    InvoiceRequest.java         la facture de VOTRE domaine (corps validé)
    InvoiceMapping.java         votre facture -> les données du modèle (la seule classe qui en connaît la forme)
    CrystallonClients.java      construit le client du SDK depuis la configuration (pas d'adresse = fonction éteinte) et choisit le jeton
    TokenMode.java              quel jeton reçoit Crystallon : celui de l'appelant (forwarding) ou celui de l'application (platform)
    ForwardedToken.java         le jeton Bearer de l'appelant, transmis à Crystallon (mode forwarding)
    DocumentErrors.java         échec de Crystallon -> 403 / 422 / 502 / 504
    DocumentsReflection.java    image native : les records des données générées
    generated/                  code généré par l'onglet « Code » de Crystallon (à ne pas modifier à la main)
  security/DevAuthentication.java   identité de développement (profil dev seulement)
src/main/webui/                 l'interface Vue 3 (Vite + vitest), construite par Quinoa
src/test/java/…                 86 tests Java ; src/main/webui/src/App.test.js : 33 tests d'interface
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
- **Langues (i18n)** : l'interface existe en anglais (par défaut), français, espagnol, italien et allemand. Aucun texte n'est écrit dans les composants : chaque texte est une clé de [`src/i18n/`](src/main/webui/src/i18n) (`en.js`, `fr.js`, `es.js`, `it.js`, `de.js`) lue par `t('clé', { paramètre })` (un petit utilitaire dans `index.js`, sans dépendance). Le sélecteur de l'en-tête mémorise le choix (`localStorage`) ; sans choix, la langue du navigateur est utilisée si c'est l'une des cinq, sinon l'anglais ; `<html lang>` suit. Pour ajouter une langue : copiez `en.js` en `<code>.js` et traduisez les valeurs (mêmes clés), puis importez-le dans `index.js` (`MESSAGES` et `LANGUAGES`). Les messages d'erreur renvoyés par l'API sont affichés tels que reçus ; ceux des codes 401 et 403 sont traduits.
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
| Signature | clés de Charm, lues à `${GLUONIFY_SERVICE_CHARM_URL}/realms/gluonify` | `GLUONIFY_SERVICE_CHARM_URL` est fournie par la plateforme si l'application est déployée avec `"uses": ["charm"]` |
| Émetteur (`iss`) | `quarkus.oidc.token.issuer` | `GLUONIFY_ZZZNONE_OIDC_ISSUER` = `https://id.<votre zone>/realms/gluonify` (stable : ce n'est pas l'adresse de l'instance de Charm) |
| Audience (`aud`) | `quarkus.oidc.token.audience` | `GLUONIFY_ZZZNONE_OIDC_AUDIENCE`, par défaut le nom du projet (`gluonify-source`) |
| Expiration | automatique | jetons courts |
| Rôles | claim `roles` | `source:read` (lire), `source:write` (écrire) : voir `@RolesAllowed` ; le préfixe `gluonify:` est réservé à la plateforme (Charm le refuse pour les rôles d’une application) |

**Obtenir un jeton de test** (l'administrateur de la plateforme, avec le jeton d'administration de Charm) :

```bash
curl -s -X POST "$CHARM_URL/v1/tokens" -H "Authorization: Bearer $GLUONIFY_CHARM_ADMIN_TOKEN" -H 'Content-Type: application/json' \
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
| **Top** (coffre) | configuration et secrets par application | avec `"top": true`, les clés de l'espace `<uuid>.app` propre à votre application arrivent en `APP_<CLÉ>` ; `${app.gluonify.gdown.password}` dans `application.properties` | `source.graph.password`, `source.webhook.key` |
| **Charm** (identité) | jetons JWT courts | `"uses": ["charm"]` → `GLUONIFY_SERVICE_CHARM_URL` ; `quarkus-oidc` vérifie les jetons | [§6](#6-authentification--les-jetons-de-charm) |
| **Gdown** (base graphe) | base répliquée (Raft), Cypher par HTTP | `"uses": ["gdown"]` → `GLUONIFY_SERVICE_GDOWN_URL` ; un compte local de Gdown | [`GraphNoteStore`](src/main/java/io/gluonify/source/notes/GraphNoteStore.java) |
| **Fichiers distribués** | `/distributed/std` : durable, partagé par toutes les répliques, 2 copies | `"distributed": ["std"]` au déploiement | [`FileNoteStore`](src/main/java/io/gluonify/source/notes/FileNoteStore.java) |
| **Autres applications** | appel de service à service | `"uses": ["other"]` → `GLUONIFY_SERVICE_OTHER_URL` | [`PlatformResource.ping`](src/main/java/io/gluonify/source/platform/PlatformResource.java) |
| **Photon** (passerelle d'API) | webhooks signés, conversion XML / SOAP / formulaire → JSON, rejeu | Photon livre à `…/hooks/events` ; votre service acquitte en 2XX | [`WebhookResource`](src/main/java/io/gluonify/source/platform/WebhookResource.java) |
| **Crystallon** (documents PDF) | factures, devis et lettres en PDF/A depuis des modèles dessinés dans son éditeur ; client Java du SDK, code généré | facultatif : `"uses": ["crystallon"]` → `GLUONIFY_SERVICE_CRYSTALLON_URL` et le fichier du jeton de l'application (`GLUONIFY_CRYSTALLON_TOKEN_FILE`) ; le jeton de l'appelant est transmis (par défaut) ou le jeton propre de l'application est utilisé (`source.crystallon.token=platform`), rôle `gluonify:crystallon:render` | [`DocumentsResource`](src/main/java/io/gluonify/source/documents/DocumentsResource.java), [détails](#documents-crystallon) |
| **Field** | sites statiques (ZIP) servis sur votre domaine | rien à coder : un site se dépose en ZIP | — |
| **Higgs** (orchestrateur) | domaines, certificats HTTPS automatiques, routes, politiques (jeton, limites, CORS, adresses) | `PUT /domains/<nom>` | `deploy/domain.json` |

### Configuration et secrets (Top)

- Une valeur **non secrète** : dans `"env"` du déploiement (`deploy/appspec.json`) : `"SOURCE_STORE": "files"`. Elle est stockée dans l'état du cluster.
- Une valeur **secrète** (mot de passe, clé d'API) : dans le **coffre** (Top, espace `<uuid>.app` de votre application). La clé `GLUONIFY_GDOWN_PASSWORD` arrive en variable `APP_GLUONIFY_GDOWN_PASSWORD` et se lit `${app.gluonify.gdown.password}`. Demandez cet espace avec `"top": true` dans la spécification de l'application : seul cet espace `<uuid>.app`, attribué par le plan de contrôle, ajoute le préfixe `APP_`. Avec un `"topNamespace"` littéral (par exemple `"gluonify-source"`, seule option du builder), les clés arrivent **sans préfixe**, telles que nommées dans le coffre (`GLUONIFY_GDOWN_PASSWORD` reste `GLUONIFY_GDOWN_PASSWORD`) : nommez-les d'après les propriétés lues (par exemple `SOURCE_GRAPH_PASSWORD`). **Jamais** dans le dépôt : le builder refuse un mot de passe en clair (règle R-SECRET), et `ConformityTest` vous le dit avant.
- **Enregistrer une valeur ne redémarre rien** : après avoir modifié plusieurs clés, déclenchez **un** redéploiement (`POST /apps/<nom>/redeploy`) ; les répliques redémarrent une à une, sans coupure si vous en avez deux ou plus.
- Variables que la plateforme ajoute **toujours** : `QUARKUS_HTTP_PORT` et `QUARKUS_HTTP_HOST`, `GLUONIFY_ENVIRONMENT` (l'environnement : SBX, QUA, PRD…), `GLUONIFY_REPLICA` (rang de la réplique : 1, 2…), `GLUONIFY_SELF_URL` (adresse de cette instance). Elles sont lues et affichées par [`PlatformResource`](src/main/java/io/gluonify/source/platform/PlatformResource.java) (`GET /api/platform`, jamais de secret).

### Santé : `/q/health/ready` et `/q/health/live`

La plateforme n'envoie du trafic qu'aux instances dont **`/q/health/ready`** répond 200, et redémarre celles dont **`/q/health/live`** échoue de façon répétée. `quarkus-smallrye-health` est **obligatoire** (le builder le contrôle : règle R-SANTE). [`StoreHealth`](src/main/java/io/gluonify/source/notes/StoreHealth.java) rend l'instance « prête » seulement si le stockage répond. Gardez « vivant » indépendant des services extérieurs : une panne de Gdown ne doit pas faire redémarrer l'application en boucle.

### Données dans Gdown (`source.store=gdown`)

**Le plus simple : une base dédiée.** Déployez avec `"gdownDatabase": true` et `SOURCE_STORE=gdown` : la plateforme crée une base propre à l'application (son propre groupe Raft, un compte confiné) et fournit `GLUONIFY_GDOWN_DATABASE`, `GLUONIFY_GDOWN_USER`, `GLUONIFY_GDOWN_PASSWORD` et l'accès réseau (`GLUONIFY_SERVICE_GDOWN_URL`) ; il n'y a rien d'autre à faire. La base n'est **jamais supprimée** avec l'application.

Un exemple prêt à l'emploi se trouve dans [`deploy/appspec-graph.json`](deploy/appspec-graph.json).

**À la main** (base partagée, vos propres noms) :

1. Déployez avec `"uses": ["gdown"]` (c'est aussi ce qui **ouvre le réseau** vers Gdown) et `SOURCE_STORE=gdown`.
2. Un administrateur crée la base et le compte de l'application ([`deploy/gdown-setup.cypher`](deploy/gdown-setup.cypher) : base `source`, rôle confiné, compte `notes`).
3. Mettez `GLUONIFY_GDOWN_USER` (= `notes`) et `GLUONIFY_GDOWN_PASSWORD` dans le coffre.

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

### Documents (Crystallon)

**Crystallon** produit des documents PDF (factures, devis, lettres ; PDF/A-1B sur demande) à partir d'un **modèle** dessiné dans son éditeur graphique et conservé là, avec son historique, pour chaque organisation. L'application envoie ses données (le *payload*, un JSON) et reçoit le PDF. C'est un composant **facultatif** de la plateforme : ce code est donc aussi l'exemple d'une intégration facultative. [`DocumentsResource`](src/main/java/io/gluonify/source/documents/DocumentsResource.java) offre `POST /api/documents/invoice`, qui prend une facture du domaine propre à l'application ([`InvoiceRequest`](src/main/java/io/gluonify/source/documents/InvoiceRequest.java)) et répond `application/pdf`.

**1. La dépendance.** Le client Java du SDK de Gluonify est servi par le dépôt Maven de **gluonify-sea** (lecture publique : aucun compte pour le résoudre). Déclarez le dépôt et la dépendance dans `pom.xml` (déjà fait ici) :

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

Le dépôt s'ajoute à Maven Central, il ne le remplace pas. La version est celle de la plateforme (l'onglet *Code* de l'éditeur de Crystallon donne celle de votre installation). Ce `pom.xml` écrit l'adresse en propriété `gluonify.sea.maven.url`, pour qu'un miroir ou une copie locale puisse la remplacer (`mvn verify -Dgluonify.sea.maven.url=file:///…`). Le builder de Gluonify (Bup) déclare lui-même ce dépôt pour les builds qu'il lance. Le client est en Java 17, sans CDI ni Quarkus, et utilise le Jackson de l'application (ici Jackson 3). Avec Gradle : `maven("https://images.gluonify.io/maven")` dans `repositories` et `implementation("io.gluonify.plasma:gluonify-plasma-crystallon-client:0.6.0")`.

**2. Déclarer l'usage.** `"uses": ["charm", "crystallon"]` dans la spécification de l'application ([`deploy/appspec.json`](deploy/appspec.json)) : la plateforme fournit `GLUONIFY_SERVICE_CRYSTALLON_URL` **et ouvre le réseau** vers Crystallon, et vers lui seul ; [`CrystallonClients`](src/main/java/io/gluonify/source/documents/CrystallonClients.java) la lit (`source.crystallon.url`) et construit le client. **Sans cela, la fonction est éteinte et rien d'autre ne change** : le point d'entrée répond 404 en disant pourquoi, `GET /api/platform` indique `"documents": false`, l'interface masque la section, l'application démarre et tous les autres tests passent sans Crystallon.

**3. Le jeton et le rôle.** Crystallon exige un jeton Charm d'audience `gluonify-crystallon` ; le rôle **`gluonify:crystallon:render`** suffit pour rendre et valider (`convert` et `templates` ne servent pas à produire des documents). Les modèles sont ceux de l'**organisation** que porte le jeton. Le réglage **`source.crystallon.token`** (variable d'environnement `SOURCE_CRYSTALLON_TOKEN`) choisit **quel** jeton Crystallon reçoit. Il désigne un mode ; il ne contient jamais de jeton :

| `source.crystallon.token` | Crystallon voit | À choisir quand |
|---|---|---|
| `forwarding` (défaut) | la **personne** qui appelle cette application : son jeton est transmis tel quel ([`ForwardedToken`](src/main/java/io/gluonify/source/documents/ForwardedToken.java), `CrystallonTokens.forwarding`) | Crystallon doit savoir qui demande (ses rôles, son organisation). Un appel fait hors d'une requête n'a aucun jeton à transmettre |
| `platform` | l'**application** : son jeton propre, que la plateforme émet pour une application déployée avec `"uses": ["crystallon"]` (jeton Charm, sujet `app:<nom>`, audience `gluonify-crystallon`, rôle `gluonify:crystallon:render`) | l'appel est fait au nom de l'application (une minuterie, un webhook, une file de messages), ou les personnes qui appellent n'ont pas de jeton Crystallon. Rien à émettre à la main |

**Transmission (défaut).** Le jeton que vous envoyez à `/api/documents/invoice` doit porter, en plus de l'audience et du rôle de cette application (`gluonify-source`, `source:read`), l'audience `gluonify-crystallon` et le rôle `gluonify:crystallon:render`. Charm met plusieurs audiences dans un même jeton (`audiences`, 8 au plus) ; le préfixe `gluonify:` étant réservé à la plateforme, c'est l'administrateur qui l'émet :

```bash
curl -s -X POST "$CHARM_URL/v1/tokens" -H "Authorization: Bearer $GLUONIFY_CHARM_ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"sub":"ada","audiences":["gluonify-source","gluonify-crystallon"],"ttlSeconds":3600,"roles":["source:read","gluonify:crystallon:render"]}'
```

**Plateforme.** Rien à émettre : le jeton que vous envoyez n'a qu'à ouvrir cette API (`source:read`). Avec `"uses": ["crystallon"]`, le plan de contrôle (Higgs) fait émettre par Charm le jeton de l'application, et l'agent du nœud (Strange) l'écrit dans le fichier `/run/gluonify-tokens/crystallon.token` (en lecture seule dans le bac à sable de l'instance) et le renouvelle avant son expiration (900 s par défaut). La plateforme fournit la variable `GLUONIFY_CRYSTALLON_TOKEN_FILE` avec ce chemin (réglage `source.crystallon.token-file`). [`CrystallonClients`](src/main/java/io/gluonify/source/documents/CrystallonClients.java) relit le fichier **à chaque appel** : un renouvellement ne demande aucun redémarrage. À savoir :

- le jeton de l'application porte l'organisation par défaut de Crystallon (`default`) : le modèle `Gluonify invoice` doit y exister ;
- sans la variable (l'application n'est pas déployée avec `"uses": ["crystallon"]`, ou la plateforme n'émet pas encore de jetons d'application), `platform` **arrête l'application au démarrage** avec un message qui dit pourquoi, au lieu d'échouer à la première requête ;
- quand Crystallon refuse le jeton de l'application, la réponse est un 502 et non un 403 : l'appelant n'y peut rien, le déploiement si ;
- le SDK publié (0.6.0) n'a pas encore `CrystallonTokens.platform()` (il est dans les sources du SDK, pour une version suivante) : `CrystallonClients.platform` fait la même chose avec `CrystallonTokens.file(…)`. Avec un SDK plus récent, remplacez-le par cet appel.

Où régler `SOURCE_CRYSTALLON_TOKEN=platform` : dans `"env"` avec `PUT /apps/<nom>` (chemin B du [§9](#9-déployer)) ; avec le builder (chemin A), qui n'a pas de champ `env`, dans l'espace du coffre de `topNamespace` (les clés arrivent sans préfixe). Sous `mvn quarkus:dev`, cette API ne demande pas de jeton, mais Crystallon si : réglez `GLUONIFY_SERVICE_CRYSTALLON_URL` sur son adresse, puis envoyez (ou collez dans l'interface) un jeton Charm qui remplit les conditions ci-dessus, transmis tel quel, ou essayez `platform` avec un jeton Charm écrit dans un fichier (`SOURCE_CRYSTALLON_TOKEN=platform` et `GLUONIFY_CRYSTALLON_TOKEN_FILE=<ce fichier>`).

**4. Le modèle et le code généré.** L'exemple appelle le modèle Crystallon nommé **`Gluonify invoice`** : créez-le dans la console de Crystallon (`/crystallon/` derrière l'orchestrateur ; *Nouveau modèle*, *Partir de* le modèle prédéfini `invoice`, avec exactement ce nom). Crystallon **génère** alors **le code Java** d'un modèle : des records qui décrivent exactement les données qu'il lit, et une classe qui l'appelle. Ouvrez le modèle et son onglet **Code** (il demande d'enregistrer d'abord : le code est généré à partir de la version enregistrée), réglez *Paquet Java* sur `io.gluonify.source.documents.generated` et *Nom de la classe* sur `GluonifyInvoice`, laissez *Figer cette version* décochée (le code rend alors la version courante du modèle), puis *Télécharger (zip)* ou *Copier*. Les trois fichiers de [`documents/generated/`](src/main/java/io/gluonify/source/documents/generated) sont exactement cette sortie (seul un commentaire d'en-tête a été ajouté), générée à partir de la version 1 du modèle :

- `GluonifyInvoicePayload` : un record par objet des données (`Sender`, `Recipient`, `Invoice`, `Item`, `Payment`) ; la Javadoc de chaque composant dit s'il est obligatoire ;
- `GluonifyInvoiceDocument` : la façade, avec `render(payload)`, `render(payload, RenderOptions)` et `validate(payload)` ; elle appelle le modèle **par son nom** (le même dans chaque environnement) et porte les constantes `TEMPLATE_NAME`, `TEMPLATE_VERSION` et `SCHEMA_SHA256` ;
- `package-info`.

**Pour les régénérer** (le modèle a changé et attend désormais d'autres données : son `SCHEMA_SHA256` n'est plus celui de la façade), générez de nouveau dans l'onglet Code avec les deux mêmes champs et remplacez les trois fichiers ; ne les modifiez jamais à la main. Le compilateur montre alors ce qu'il faut adapter dans [`InvoiceMapping`](src/main/java/io/gluonify/source/documents/InvoiceMapping.java), la seule classe qui connaît la forme des données. Sans la console, le même code vient de l'API (rôle `gluonify:crystallon:templates`, non `render`) : `GET /v1/templates/<id>/code?lang=java&package=io.gluonify.source.documents.generated&name=GluonifyInvoice`.

**Image native.** Le SDK déclare pour la réflexion ses propres records ; **ceux des données sont l'affaire de l'application**. Le record racine ET chaque record imbriqué de chaque donnée générée figurent dans [`DocumentsReflection`](src/main/java/io/gluonify/source/documents/DocumentsReflection.java) (`@RegisterForReflection`). En oublier un ne casse que l'exécutable natif, au premier appel : `DocumentsReflectionTest` le vérifie.

**5. Le point d'entrée.**

```bash
curl -s -X POST localhost:8080/api/documents/invoice -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -o invoice.pdf -d '{
  "number": "2026-0042", "subject": "Website relaunch", "lang": "en",
  "customer": {"name": "Example Customer SA", "street": "Rue du Lac", "houseNumber": "7", "postalCode": "1003", "city": "Lausanne", "country": "CH"},
  "lines": [{"description": "Consulting, 12 hours", "quantity": 12, "unit": "h", "unitPrice": 160}]}'
```

`source:read` l'ouvre. `date` (aujourd'hui), `vatRate` (8,1 %) et `unit` (`pce`) ont des valeurs par défaut ; le montant de chaque ligne est calculé par l'application ; `lang` (`en`, `fr`, `es`, `it`, `de`) est facultative, la devise, le pays et le fuseau horaire sont ceux du modèle (CHF, CH, Europe/Zurich). La réponse est le PDF (`200`, `application/pdf`, PDF/A-1B, `Content-Disposition: attachment`, et les en-têtes `X-Crystallon-Pages` et `X-Crystallon-Warnings`). Les erreurs ont le corps habituel `{"error": "…"}`, plus `code` et `path` (l'endroit du problème dans le modèle) quand Crystallon les donne ([`DocumentErrors`](src/main/java/io/gluonify/source/documents/DocumentErrors.java)) :

| Situation | Réponse |
|---|---|
| requête invalide (numéro vide, aucune ligne, `lang` inconnue…) | 400 ; Crystallon n'est pas appelé |
| pas de jeton, ou sans `source:read` | 401 / 403 |
| Crystallon refuse le jeton (audience ou rôle `gluonify:crystallon:render` manquant, expiré) | `forwarding` : 403, avec la raison (le jeton de l'appelant ne suffit pas) ; `platform` : 502 (le jeton de l'application : l'appelant n'y peut rien) |
| le modèle ne peut pas rendre ces données (Crystallon 400 / 422) | 422, avec `code` et `path` |
| aucun modèle de ce nom pour l'organisation du jeton (Crystallon 404), Crystallon en échec (5XX), réponse inattendue, injoignable, fichier du jeton illisible (`platform`) | 502 |
| Crystallon trop lent (30 s) | 504 |
| Crystallon non configuré | 404 |

**Dans l'interface** : un bouton *Télécharger une facture d'exemple* dans la section *Documents* (affichée seulement quand `GET /api/platform` indique `"documents": true`) envoie le jeton que vous avez collé et une facture d'exemple ; son aide dit quel jeton reçoit Crystallon (`GET /api/platform` répond `"documentsToken": "forwarding"` ou `"platform"`). [`DocumentsResourceTest`](src/test/java/io/gluonify/source/documents/DocumentsResourceTest.java) et [`DocumentsPlatformTokenTest`](src/test/java/io/gluonify/source/documents/DocumentsPlatformTokenTest.java) testent tout cela contre un faux Crystallon (un `HttpServer` du JDK) : le chemin appelé (`/v1/templates/by-name/Gluonify%20invoice/render`), le jeton transmis ou celui du fichier de jeton (relu après un renouvellement), les données envoyées et chaque erreur ci-dessus.

### Appeler une autre application

Déclarez-la dans `"uses"` : la plateforme fournit `GLUONIFY_SERVICE_<APP>_URL` **et ouvre le réseau** vers elle (sans `uses`, l'application ne la joint pas). N'écrivez jamais l'adresse en dur : lisez la variable (voir `ping` dans [`PlatformResource`](src/main/java/io/gluonify/source/platform/PlatformResource.java)).

### Sites statiques et domaines

- **Field** sert un site statique (ZIP) sur votre domaine ; rien à coder dans ce service.
- **Votre domaine** : `PUT /domains/<nom>` sur l'API de Higgs (voir [`deploy/domain.json`](deploy/domain.json)) route un chemin vers l'application et applique des **politiques** : jeton Charm (audience, rôles), limites de débit, CORS, listes d'adresses. Le certificat HTTPS est obtenu automatiquement.

## 8. Construire

```bash
mvn test                       # tests Java (86)
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
  -d '{"name":"gluonify-source","gitUrl":"https://github.com/VOUS/VOTRE-DEPOT.git","ref":"main","deploy":true,"replicas":2,"memoryMb":128,"topNamespace":"gluonify-source"}'
```

Le builder clone, contrôle la conformité, compile en natif, publie l'exécutable et le déploie. Suivez-le : `GET /v1/builds/<id>` et `/logs`. (Un push Git peut aussi lancer le build par un webhook : `POST /v1/webhooks/git`.) Remarque : le builder ne connaît que `topNamespace`, donc avec lui les clés du coffre arrivent sans préfixe.

**Les services que le builder déclare.** Le builder envoie `uses` et `gdownDatabase` à `PUT /apps/<nom>`, pris dans la requête de build (`"uses": ["charm","gdown","crystallon"]`) ou, si la requête n'en a pas, dans le [`gluonify.json`](gluonify.json) à la racine du dépôt (la requête l'emporte ; une liste vide dans la requête signifie « aucun service »). Le fichier de ce dépôt est `{"uses": ["charm", "gdown", "crystallon"]}` : les mêmes services que [`deploy/appspec.json`](deploy/appspec.json), ce que `DeploySpecsTest` vérifie. C'est important, car `PUT /apps/<nom>` **remplace toute la spécification** : un build qui n'enverrait pas `uses` déploierait l'application sans `GLUONIFY_SERVICE_*_URL`, sans réseau vers Charm, Gdown et Crystallon, et sans le jeton Crystallon de l'application (donc `source.crystallon.token=platform` l'arrêterait au démarrage). Ajoutez-y `"gdownDatabase": true` pour la base dédiée ; un `?` initial (`"?crystallon"`) marque un service facultatif. Déclarez toujours `uses` dans `gluonify.json` ou dans la requête de build.

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
| `uses` | services joignables, par nom d'application (`charm`, `gdown`, `crystallon`…) : `GLUONIFY_SERVICE_<APP>_URL` fournie, réseau ouvert ; pour `crystallon`, aussi le jeton de l'application (`GLUONIFY_CRYSTALLON_TOKEN_FILE`). Avec le builder : dans `gluonify.json` ou dans la requête de build |
| `distributed` | `["std"]` pour `/distributed/std` |
| `env` | variables **non secrètes** ; `${NOM}` et `${NOM:-défaut}` sont résolus |
| `top` | `true` : le plan de contrôle attribue à l'application son propre espace de coffre `<uuid>.app`, dont les clés arrivent en `APP_<CLÉ>` |
| `gdownDatabase` | `true` : une base Gdown dédiée pour l'application (groupe Raft propre, compte confiné) ; `GLUONIFY_GDOWN_DATABASE`, `GLUONIFY_GDOWN_USER`, `GLUONIFY_GDOWN_PASSWORD` et `GLUONIFY_SERVICE_GDOWN_URL` sont fournis ; jamais supprimée avec l'application (voir [`deploy/appspec-graph.json`](deploy/appspec-graph.json)) |
| `topNamespace` | alternative : un espace de coffre existant, par son nom ; ses clés arrivent **sans préfixe** (pas de `APP_`) |
| `internal` | `true` : aucune route publique (service interne) |

Mettre à jour : même `PUT` (ou nouveau build) ; les répliques sont remplacées **une à une**. Redémarrer sans changer : `POST /apps/<nom>/redeploy`. Journaux : `GET /apps/<nom>/logs`. Mesures : `/q/metrics`.

## 10. Tester

```bash
mvn test                                 # 86 tests Java
cd src/main/webui && npm install && npm test   # 33 tests d'interface
```

| Test | Tests | Ce qu'il vérifie |
|---|---|---|
| `NotesResourceTest` | 7 | 401 sans jeton, 403 sans le bon rôle, cycle de vie complet, validation 400, OpenAPI, santé, mesures |
| `FileNoteStoreTest` | 6 | fichiers neufs jamais renommés, ordre, deux répliques sur le même dossier, fichiers illisibles ignorés, identifiants jamais des chemins |
| `GraphNoteStoreTest` | 4 | contre un faux Gdown : chemin, authentification, instruction **paramétrée**, lecture des lignes, échec |
| `WebhookResourceTest` / `WebhookClosedTest` | 4 + 1 | clé, acquittement, **idempotence**, rejets ; fermé sans clé |
| `PlatformResourceTest` | 4 | variables de la plateforme lues, **aucun secret renvoyé**, appel de service à service limité aux services déclarés |
| `ConformityTest` | 4 | les normes du builder, chez vous |
| `EventLedgerTest` | 4 | registres d'idempotence : un seul gagnant par événement (mémoire, fichiers avec deux répliques, contrainte d'unicité du graphe, erreur du graphe autre qu'un doublon propagée) |
| `DocumentsResourceTest` | 13 | contre un **faux Crystallon** : PDF renvoyé, chemin appelé (`/v1/templates/by-name/Gluonify%20invoice/render`), **jeton transmis**, données envoyées, correspondance des 422 / 403 / 502, validation propre (400), rôles, OpenAPI |
| `DocumentsDisabledTest` | 4 | Crystallon non configuré : 404 avec la raison, `"documents": false`, le reste inchangé, réglages du jeton vides par défaut |
| `DocumentsPlatformTokenTest` | 8 | `source.crystallon.token=platform` contre le faux Crystallon : le **jeton de l'application** (pas celui de l'appelant) est envoyé, même hors d'une requête, le fichier est **relu après un renouvellement**, un refus donne un 502, `"documentsToken"` |
| `CrystallonClientsTest` | 11 | pas d'adresse = pas de client, une mauvaise adresse nommée, le jeton de l'appelant demandé à chaque appel ; les deux modes : mode inconnu (valeur jamais répétée), `platform` sans son fichier, jeton du fichier relu, ignoré en `forwarding` |
| `InvoiceMappingTest` / `DocumentErrorsTest` / `ForwardedTokenTest` | 3 + 9 + 1 | votre facture → les données du modèle (montants, valeurs par défaut, nom de fichier sûr) ; chaque échec de Crystallon → un statut, dans chaque mode de jeton ; quels en-têtes donnent un jeton |
| `DocumentsReflectionTest` | 1 | garde-fou natif : chaque record des données générées est déclaré pour la réflexion |
| `DeploySpecsTest` | 2 | les spécifications de `deploy/` utilisent les noms actuels (`uses`, `SOURCE_STORE`) ; `gluonify.json` (lu par le builder) déclare les mêmes `uses` que `deploy/appspec.json` |

Un test d'API se copie de `NotesResourceTest` ; un test de stockage de `FileNoteStoreTest` (sans démarrer Quarkus : rapide).

## 11. Pièges connus

- **Natif** : ne créez **pas** de `HttpClient`, de `Random` ni de `SecureRandom` dans un champ `static` (l'état serait figé à la compilation, pas à l'exécution) : créez-les à la première utilisation (voir `GraphNoteStore.client()`). Lisez le JSON de tiers en arbre (`JsonNode`) plutôt que dans des classes ; un type (dé)sérialisé par Jackson hors d'une signature REST doit porter `@RegisterForReflection`. **Un bogue qui n'apparaît qu'en natif ne se voit pas dans `mvn test`** : lancez l'exécutable natif avant de livrer.
- **Jackson 3** : le paquet est `tools.jackson.databind`, pas `com.fasterxml.jackson.databind`.
- **Aucune adresse, aucun port, aucun mot de passe en dur** : tout vient de l'environnement.
- **Fichiers distribués** : voir les règles du [§7](#fichiers-distribués-sourcestorefiles) (ne jamais réécrire, ~3 s de retard).
- **Webhooks** : répondre vite en 2XX, être idempotent, protéger par une clé.
- **Code généré** : les fichiers de `documents/generated/` viennent de l'onglet *Code* de Crystallon : régénérez-les, ne les modifiez jamais à la main ; et déclarez chaque record d'un payload généré dans `DocumentsReflection` (un échec qui n'apparaît qu'en natif).
- **Des noms qui ressemblent à des secrets** : la règle R-SECRET du builder refuse une valeur en clair sur un réglage dont le nom contient `token`, `password`, `secret` ou `key`. `source.crystallon.token` contient un mode, pas un jeton, et s'écrit pourtant `${SOURCE_CRYSTALLON_TOKEN:}` ; aujourd'hui le builder y accepte `${VARIABLE}` et `${VARIABLE:}` (défaut vide), **pas** `${VARIABLE:forwarding}` : le défaut vit dans le code (`TokenMode`). `ConformityTest` est plus indulgent que le builder sur ce point (il accepte tout `${…}`) : gardez le défaut vide.
- **Une identité de développement n'a rien à faire en production** : gardez `@IfBuildProfile("dev")`.
- **Quinoa et les adresses d'API** : ajoutez tout nouveau préfixe d'API à `quarkus.quinoa.ignored-path-prefixes`.
- **Sous émulation x86_64** (Docker sur Apple Silicon) : construisez avec `--build-arg MAVEN_OPTS=-Djdk.lang.Process.launchMechanism=VFORK`.

## 12. Dépannage

| Symptôme | Cause probable |
|---|---|
| 401 en production | pas de jeton, jeton expiré, ou **audience** différente de `GLUONIFY_ZZZNONE_OIDC_AUDIENCE` ; ou `GLUONIFY_ZZZNONE_OIDC_ISSUER` ne correspond pas à l'`iss` du jeton |
| 403 | jeton valide mais sans le rôle (`source:read` / `source:write`) |
| L'application ne reçoit pas de trafic | `/q/health/ready` ne répond pas 200 (stockage injoignable ?) : `GET /apps/<nom>` montre les instances prêtes |
| `store=gdown` : « source.graph.url is empty » | l'application n'est pas déployée avec `"uses": ["gdown"]` |
| Gdown : 401 ou « Unsupported property value type » | mauvais compte ; ou une **carte** stockée comme propriété (voir §7) |
| Une note créée n'apparaît pas tout de suite (fichiers) | cache de liste de ~3 s entre répliques : rafraîchir |
| Le build du dépôt est refusé | une règle `R-…` : le message la nomme ; `mvn test` (`ConformityTest`) la montre chez vous |
| `mvn package` ne trouve pas Node | accès réseau pour le télécharger, ou `-Dquarkus.quinoa=false` pour construire sans l'interface |
| Le natif plante au démarrage mais pas en JVM | un état figé à la compilation (`static`) ou une réflexion manquante (§11) |
| `POST /api/documents/invoice` : 404 « documents are off » | l'application n'est pas déployée avec `"uses": ["crystallon"]` (pas de `GLUONIFY_SERVICE_CRYSTALLON_URL`) |
| Documents : 403 « Crystallon did not accept your token » | mode `forwarding` : le jeton n'a pas l'audience `gluonify-crystallon` ou le rôle `gluonify:crystallon:render`, ou il a expiré (voir [§7](#documents-crystallon)) ; ou choisissez `source.crystallon.token=platform` |
| Documents : 502 « did not accept the token of the application » | mode `platform` : l'application n'est pas déployée avec `"uses": ["crystallon"]`, la plateforme n'émet pas encore de jetons d'application, ou `GLUONIFY_CRYSTALLON_TOKEN_FILE` est absent, vide ou périmé |
| L'application ne démarre pas : « source.crystallon.token=platform needs the file of the application token » | `platform` sans `GLUONIFY_CRYSTALLON_TOKEN_FILE` : déclarez `"uses": ["crystallon"]` (aussi dans `gluonify.json` pour un build par le builder) ou utilisez `forwarding` |
| Documents : 502 « does not know the template » | aucun modèle nommé `Gluonify invoice` dans l'organisation du jeton (`platform` : l'organisation par défaut de Crystallon) : créez-le dans la console de Crystallon |
| Documents : 422 avec `code` et `path` | le modèle ne peut pas rendre ces données : `path` est l'endroit du problème dans le modèle |
| Les documents marchent en JVM mais échouent au premier appel dans l'exécutable natif | un record des données générées manque dans `DocumentsReflection` |

---

*Gluonify est une plateforme minimaliste pour applications Quarkus natives : [gluonify.cloud](https://gluonify.cloud) (présentation) et [gluonify.io](https://gluonify.io) (technique). Ce dépôt est volontairement petit : il se lit en une heure.*
