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
| `/q/swagger-ui` | la documentation de l'API (interactive en dev et en test, en lecture seule en production : ni Authorize ni Try it out) |
| `/q/openapi` | le contrat OpenAPI (JSON ou YAML) |
| `/q/health/ready` · `/q/health/live` | santé : « prêt » et « vivant » (voir [§7](#7-sinterfacer-avec-les-services-de-gluonify)) |
| `/q/metrics` | mesures Prometheus |

En **développement**, aucun jeton n'est demandé : une identité « dev » (avec tous les rôles) n'existe que dans ce profil (`@IfBuildProfile("dev")`, absente de l'exécutable de production). Les notes sont **en mémoire** (`source.store=auto` revient à la mémoire sans base dédiée) : elles disparaissent à l'arrêt.

```bash
curl -s localhost:8080/api/notes -H 'Content-Type: application/json' -d '{"title":"Courses","body":"du pain"}'
curl -s localhost:8080/api/notes
```

## 2. La carte du projet

```
pom.xml                         projet Maven AUTONOME (Quarkus 4.0.0.Beta1, Java 25) : aucun parent Gluonify
gluonify.json                   lu par le builder de Gluonify (Bup) : seulement les services que le SDK ne détecte pas (`uses` : charm, gdown) ; Bup déduit le reste
crystallon/templates/           le modèle Crystallon de l'exemple, tel que la console l'exporte ; le builder l'installe au déploiement
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
    Documents.java              Crystallon est-il disponible ? Rien à configurer : le client vient du SDK (gluonify-plasma-quarkus)
    DocumentErrors.java         échec de Crystallon -> 403 / 422 / 502 / 504
    DocumentsReflection.java    image native : les records des données générées
    generated/                  code généré par l'onglet « Code » de Crystallon (à ne pas modifier à la main)
  security/DevAuthentication.java   identité de développement (profil dev seulement)
  security/AuthConfigResource.java GET /api/auth-config : ce dont l'interface a besoin pour se connecter avec Charm (public)
src/main/webui/                 l'interface Vue 3 (Vite + vitest), construite par Quinoa
  src/auth.js                   connexion avec Charm (code d'autorisation + PKCE) par le kit @gluonify/ui
  vendor/gluonify-ui/           copie embarquée du kit (gluonify-ui/scripts/sync-vendor.sh, jamais modifiée à la main)
src/test/java/…                 121 tests Java ; src/main/webui/src/*.test.js : 53 tests d'interface
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
- **Appeler l'API** : des adresses relatives (`fetch('/api/notes')`), voir [`src/api.js`](src/main/webui/src/api.js). Le jeton Charm vient de la connexion ([`src/auth.js`](src/main/webui/src/auth.js), voir [§6](#6-authentification--les-jetons-de-charm)), il est gardé pour l'onglet (`sessionStorage`) et ajouté en `Authorization: Bearer`.
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
| Émetteur (`iss`) | `quarkus.oidc.token.issuer` | `GLUONIFY_SOURCE_OIDC_ISSUER` = `https://id.<votre zone>/realms/gluonify` (stable : ce n'est pas l'adresse de l'instance de Charm) |
| Audience (`aud`) | `quarkus.oidc.token.audience` | `GLUONIFY_SOURCE_OIDC_AUDIENCE`, par défaut le nom du projet (`gluonify-source`) |
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

### Se connecter depuis l'interface (page de connexion de Charm)

L'interface ne demande jamais de mot de passe et ne vous fait jamais coller de jeton. C'est un **client OpenID Connect public** de Charm (code d'autorisation + PKCE S256, aucun secret dans le navigateur), construit sur le kit commun `@gluonify/ui` (`createAuth`, la même connexion que les consoles de la plateforme), embarqué dans [`src/main/webui/vendor/gluonify-ui`](src/main/webui/vendor/gluonify-ui) pour qu'un clone se construise seul (le rafraîchir par `gluonify-ui/scripts/sync-vendor.sh <ce dépôt> src/main/webui`, jamais à la main) :

1. La page demande `GET /api/auth-config` (public) : l'identifiant de client, les adresses **publiques** de Charm (`/authorize`, `/token`, `/logout`) et `signInRequired` (faux sous `mvn quarkus:dev` : l'API n'y demande aucun jeton, l'interface montre donc tout).
2. **Se connecter** envoie le navigateur sur la page de Charm : la personne y tape son nom d'utilisateur et son mot de passe, **là**, jamais dans l'application. Charm renvoie le navigateur vers l'interface (`https://<adresse de l'interface>/?code=…&state=…`).
3. L'interface vérifie `state`, échange le code avec son vérificateur PKCE sur `/token` de Charm et obtient un jeton d'audience `gluonify-source` dont la revendication `roles` porte les rôles du compte (`source:read`, `source:write`). Le jeton est gardé pour l'onglet (`sessionStorage`), renouvelé par le jeton de rafraîchissement avant son expiration et envoyé en `Authorization: Bearer` à `/api`.
4. **Se déconnecter** oublie le jeton, prévient les autres onglets et ferme la session de Charm (`/logout` avec `id_token_hint`). Un 401 de l'API (jeton expiré ou révoqué) remontre la connexion.

Déconnectée, la page ne montre qu'une courte explication et le bouton **Se connecter** : les notes, les documents et la section de la plateforme demandent tous un jeton.

**La connexion fonctionne sans aucune configuration une fois déployée sur la plateforme.** Une application qui déclare `"uses": ["charm"]` (Bup le déduit du code) reçoit son propre client public de Charm, `app-<nom>`, créé et tenu à jour par la plateforme : ses redirections sont exactement les adresses de l'application (`https://<nom>.<zone>/` et les routes de ses domaines), son audience est `gluonify-<nom>`. La plateforme injecte `GLUONIFY_OIDC_CLIENT_ID`, `GLUONIFY_OIDC_ISSUER` (émetteur public), `GLUONIFY_OIDC_PUBLIC_URL` (adresse publique de Charm) et `GLUONIFY_OIDC_AUDIENCE`, valeurs par défaut ci-dessous ; les variables `GLUONIFY_SOURCE_OIDC_*` l'emportent toujours. **Pour une application d'organisation** (depuis le 2026-10-19), le client se trouve dans le realm de son organisation, `org-<org>` : l'émetteur est `https://<charm>/realms/org-<org>`, les utilisateurs sont ceux que l'organisation gère dans ce realm (pas des comptes Gluonify), la plateforme injecte aussi `GLUONIFY_OIDC_REALM` (`org-<org>`, le realm dont l'API lit la découverte et les clés par `GLUONIFY_SERVICE_CHARM_URL`), et l'interface utilise les points d'accès du realm (`/realms/org-<org>/protocol/openid-connect/auth`, `/token`, `/logout`). Créez les rôles `source:read` et `source:write` dans le realm et donnez-les à ses utilisateurs.

| Réglage | Variable | Valeur |
|---|---|---|
| `source.auth.client-id` | `GLUONIFY_SOURCE_OIDC_CLIENT_ID` | le client Charm de cette interface (par exemple `source-ui`) ; vide : pas de bouton *Se connecter*, la page cite la variable ; par défaut `GLUONIFY_OIDC_CLIENT_ID` (injectée par la plateforme) |
| `source.auth.charm-url` | `GLUONIFY_SOURCE_CHARM_PUBLIC_URL` | l'adresse **publique** de Charm, celle qu'atteint le navigateur ; vide : déduite de `GLUONIFY_SOURCE_OIDC_ISSUER` (`https://gluonify.lan/realms/gluonify` → `https://gluonify.lan`). Jamais `GLUONIFY_SERVICE_CHARM_URL` : c'est une adresse interne |
| `source.auth.scope` | `SOURCE_AUTH_SCOPE` | `openid profile` (le kit ajoute `offline_access` pour le jeton de rafraîchissement) |

**Hors de la plateforme, ou pour un autre client**, l'enregistrer soi-même (une fois, par l'administrateur de la plateforme) et fournir `GLUONIFY_SOURCE_OIDC_CLIENT_ID` : un client public dont la seule redirection (et redirection après déconnexion) est l'adresse exacte de l'interface, barre finale comprise, et dont l'audience est celle de l'application. Charm n'accepte les appels d'une autre origine vers `/token` que depuis les origines des redirections enregistrées.

```bash
curl -s -X PUT "$CHARM_URL/v1/admin/clients/source-ui" -H "Authorization: Bearer $GLUONIFY_CHARM_ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Source","redirectUris":["https://source.<zone>/"],"postLogoutRedirectUris":["https://source.<zone>/"],"audiences":["gluonify-source"],"scopes":["openid","profile","email","offline_access"],"accessTtl":3600}'
```

**Les rôles** `source:read` et `source:write` sont donnés aux comptes, comptes clients compris (les rôles d'une application ne sont pas des `gluonify:*`, Charm permet donc de les créer et de les donner) : `PUT /v1/admin/roles/source:read` (et `source:write`), puis `PUT /v1/admin/users/<id>/roles`. Charm met tous les rôles du compte dans le jeton : se déconnecter et se reconnecter après un changement.

**En local**, le profil `dev` fournit l'identité « dev » (voir [`DevAuthentication`](src/main/java/io/gluonify/source/security/DevAuthentication.java)) ; **dans les tests**, `@TestSecurity`. Jamais d'identité de ce genre en production : elle est compilée seulement dans le profil `dev`.

## 7. S'interfacer avec les services de Gluonify

Une application sur Gluonify est **isolée** : son propre compte, son propre réseau, un système de fichiers réduit. Elle ne joint que ce qu'elle **déclare** (`"uses"`) et reçoit sa configuration **par l'environnement**. Voici chaque service, avec l'endroit du code.

**Aucune configuration pour les services que couvre le SDK.** Le SDK de Gluonify trouve tout seul où se trouve un service et quel jeton présenter : la plateforme fournit l'adresse et le jeton de l'application (`"uses"`), `gluonify login <zone>` fait de même sur votre poste, et `GluonifyTestServices` remplace les services dans les tests. Aujourd'hui le SDK couvre **Crystallon** (voir [Documents](#documents-crystallon)) ; Gdown et Charm ne sont pas encore couverts et restent configurés par URL (`source.graph.url`, `quarkus.oidc.auth-server-url`) : ils suivront.

| Service | Ce qu'il offre | Comment l'application s'en sert | Dans ce dépôt |
|---|---|---|---|
| **Top** (coffre) | configuration et secrets par application | avec `"top": true`, les clés de l'espace `<uuid>.app` propre à votre application arrivent en `APP_<CLÉ>` ; `${app.gluonify.gdown.password}` dans `application.properties` | `source.graph.password`, `source.webhook.key` |
| **Charm** (identité) | jetons JWT courts | `"uses": ["charm"]` → `GLUONIFY_SERVICE_CHARM_URL` ; `quarkus-oidc` vérifie les jetons | [§6](#6-authentification--les-jetons-de-charm) |
| **Gdown** (base graphe) | base répliquée (Raft), Cypher par HTTP | `"uses": ["gdown"]` → `GLUONIFY_SERVICE_GDOWN_URL` ; un compte local de Gdown | [`GraphNoteStore`](src/main/java/io/gluonify/source/notes/GraphNoteStore.java) |
| **Fichiers distribués** | `/distributed/std` : durable, partagé par toutes les répliques, 2 copies | `"distributed": ["std"]` au déploiement | [`FileNoteStore`](src/main/java/io/gluonify/source/notes/FileNoteStore.java) |
| **Autres applications** | appel de service à service | `"uses": ["other"]` → `GLUONIFY_SERVICE_OTHER_URL` | [`PlatformResource.ping`](src/main/java/io/gluonify/source/platform/PlatformResource.java) |
| **Photon** (passerelle d'API) | webhooks signés, conversion XML / SOAP / formulaire → JSON, rejeu | Photon livre à `…/hooks/events` ; votre service acquitte en 2XX | [`WebhookResource`](src/main/java/io/gluonify/source/platform/WebhookResource.java) |
| **Crystallon** (documents PDF) | factures, devis et lettres en PDF/A depuis des modèles dessinés dans son éditeur ; client Java du SDK, code généré | facultatif, **sans configuration** : rien à déclarer (construite par Bup, `?crystallon` est déduit du SDK) et le SDK (`gluonify-plasma-quarkus`) trouve l'adresse et le jeton propre de l'application (rôle `gluonify:crystallon:render`) ; sur votre poste `gluonify login` | [`DocumentsResource`](src/main/java/io/gluonify/source/documents/DocumentsResource.java), [détails](#documents-crystallon) |
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

**Le plus simple : une base dédiée.** Rien à déclarer : `application.properties` lit `${GLUONIFY_GDOWN_DATABASE}`, `${GLUONIFY_GDOWN_USER}` et `${GLUONIFY_GDOWN_PASSWORD}`, le builder **déduit** donc `"gdownDatabase": true` (champ `gdownDatabaseReason` du build ; `"gdownDatabase": false` dans `gluonify.json` le coupe), et `source.store=auto` passe à `gdown` dès que `GLUONIFY_GDOWN_DATABASE` est fourni (mémoire sinon ; `SOURCE_STORE` force une valeur) : la plateforme crée une base propre à l'application (son propre groupe Raft, un compte confiné) et fournit `GLUONIFY_GDOWN_DATABASE`, `GLUONIFY_GDOWN_USER`, `GLUONIFY_GDOWN_PASSWORD` et l'accès réseau (`GLUONIFY_SERVICE_GDOWN_URL`) ; il n'y a rien d'autre à faire. La base n'est **jamais supprimée** avec l'application.

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

**1. La dépendance.** Une dépendance apporte tout le côté SDK : `gluonify-plasma-quarkus` (beans CDI par défaut : on injecte un `CrystallonClient` ; elle apporte `gluonify-plasma-services`, qui trouve adresses et jetons, et le client Crystallon). `gluonify-plasma-testing` (portée test) fournit les faux services pour les tests. Les deux sont déclarées dans le `pom.xml` (déjà fait ici) :

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

Le SDK est servi par le dépôt Maven de **gluonify-sea** (lecture publique : aucun compte pour le résoudre), ajouté à Maven Central, pas à sa place ; ce `pom.xml` écrit son adresse comme la propriété `gluonify.sea.maven.url`, pour qu'un miroir ou une copie locale puisse la remplacer (`mvn verify -Dgluonify.sea.maven.url=file:///…`). Le builder de Gluonify (Bup) déclare lui-même ce dépôt pour les builds qu'il exécute. **Version** : le SDK est publié en 0.7.2 sur gluonify-sea, avec `gluonify-plasma-quarkus` et `gluonify-plasma-testing` ; `gluonify.sdk.version` vaut `0.7.2` dans `pom.xml`, rien à installer en local. Depuis la 0.7.1, chaque module client porte un marqueur (`META-INF/gluonify/services/crystallon` dans `gluonify-plasma-crystallon-client`) dont le builder déduit `uses`. Avec Gradle : les deux mêmes artefacts (`implementation` et `testImplementation`), sans autre dépôt que Maven Central et gluonify-sea.

**2. Rien à déclarer.** Construite par Bup, l'application reçoit `?crystallon` dans ses `uses` sans l'écrire : Bup lit le marqueur de `gluonify-plasma-crystallon-client` dans le chemin de classes d'exécution (SDK 0.7.1 et suivants) et ajoute le service en facultatif ; [`gluonify.json`](gluonify.json) ne le mentionne pas. Déployée à la main (`PUT /apps` : pas de build, donc pas de déduction), la spécification le déclare : `crystallon` dans les `uses` de [`deploy/appspec.json`](deploy/appspec.json). Dans les deux cas, la plateforme **ouvre le réseau** vers Crystallon, et vers lui seul, et fournit son adresse et le jeton propre de l'application. **Il n'y a ni propriété, ni URL, ni jeton dans ce dépôt** : le producteur de `gluonify-plasma-quarkus` donne au code un `CrystallonClient` (le mapper Jackson 3 de l'application, l'adresse et le jeton trouvés par le SDK). [`Documents`](src/main/java/io/gluonify/source/documents/Documents.java) demande seulement si Crystallon est disponible (`GluonifyServices.optional("crystallon")`) et injecte le client en `Instance<CrystallonClient>`. **Sans cela la fonction est éteinte et rien d'autre ne change** : le point d'accès répond 404 en disant pourquoi, `GET /api/platform` indique `"documents": false`, l'interface masque la section, l'application démarre et tous les autres tests passent sans Crystallon. L'endroit où tourne l'application décide comment le SDK trouve Crystallon :

| Où | Adresse | Jeton |
|---|---|---|
| sur la plateforme, `crystallon` dans `uses` (déduit par Bup, ou déclaré dans la spécification) | fournie (`GLUONIFY_SERVICE_CRYSTALLON_URL`) | le jeton propre de l'application (fichier renouvelé par l'agent du nœud, relu à chaque appel) |
| votre poste, après `gluonify login <zone>` | l'adresse publique publiée par la zone | votre jeton de développeur, renouvelé tout seul |
| tests | le faux Crystallon de `GluonifyTestServices.start()` | le jeton de test |
| ailleurs | aucune : la fonction est éteinte | |

**3. Le jeton et le rôle.** Crystallon exige un jeton Charm d'audience `gluonify-crystallon` ; le rôle **`gluonify:crystallon:render`** suffit pour rendre et valider (`convert` et `templates` ne servent pas à produire des documents). Par défaut l'application appelle Crystallon **avec son propre jeton** (jeton Charm, sujet `app:<nom>`, audience `gluonify-crystallon`, rôle `gluonify:crystallon:render`), que personne n'émet à la main : avec `crystallon` dans `uses` (déduit ou déclaré), le plan de contrôle (Higgs) le fait émettre par Charm, et l'agent du nœud (Strange) l'écrit dans `/run/gluonify-tokens/crystallon.token` (en lecture seule dans le bac à sable de l'instance) et le renouvelle avant son expiration (900 s par défaut). Le SDK relit ce fichier **à chaque appel** : un renouvellement n'exige aucun redémarrage, et l'appel fonctionne aussi hors requête (minuterie, webhook, file de messages). Le jeton que vous envoyez à `/api/documents/invoice` n'a qu'à ouvrir cette API (`source:read`). À savoir :

- le jeton de l'application porte l'organisation de l'application (revendication `org`), ou l'organisation par défaut de Crystallon (`default`) quand elle n'en a pas : le modèle `Gluonify invoice` doit y exister, et c'est là que le builder l'installe (étape 4) ;
- quand Crystallon refuse le jeton de l'application, la réponse est un 502 et non un 403 : l'appelant n'y peut rien, le déploiement oui (construire par Bup avec le SDK 0.7.1 ou suivant, qui le déduit, ou déclarer `crystallon` dans les `uses` de la spécification) ;
- sous `mvn quarkus:dev` cette API ne demande aucun jeton, mais Crystallon en demande un : lancez une fois `gluonify login gluonify.lan` (ou votre zone), dans votre navigateur, et le SDK utilise votre jeton de développeur et l'adresse publique de Crystallon : aucune variable à définir.

**Transmettre le jeton de l'appelant (facultatif).** Si Crystallon doit voir la **personne** qui appelle l'application (ses rôles, son organisation) plutôt que l'application, remplacez le client par défaut par le vôtre : un bean du même type l'emporte sur celui par défaut. Le jeton envoyé par la personne doit alors porter, en plus de l'audience et du rôle de cette application (`gluonify-source`, `source:read`), l'audience `gluonify-crystallon` et le rôle `gluonify:crystallon:render` (Charm met plusieurs audiences dans un jeton, 8 au plus ; le préfixe `gluonify:` est réservé à la plateforme, c'est donc l'administrateur qui l'émet), et un appel fait hors requête n'a aucun jeton à transmettre. Ce n'est pas le défaut et ce dépôt ne le contient pas :

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

**4. Le modèle et le code généré.** L'exemple appelle le modèle Crystallon nommé **`Gluonify invoice`**, et **le modèle vit dans ce dépôt** : [`crystallon/templates/gluonify-invoice.json`](crystallon/templates/gluonify-invoice.json) est le modèle exactement tel que l'écrit le bouton *Exporter* de l'éditeur de Crystallon (le modèle prédéfini `invoice`, avec à la racine son `name` exact et sa `description`). **Rien à créer à la main** : quand le builder (Bup) déploie une application qui utilise Crystallon, il installe chaque `crystallon/templates/*.json` dans l'organisation où rend le jeton de l'application (`PUT /v1/templates/by-name/{name}` de Crystallon) : créé la première fois, laissé tel quel s'il est identique, enregistré comme nouvelle version quand le contenu change mais que les données attendues restent les mêmes, et **jamais écrasé** quand il attendrait d'autres données (`differs`). Le build rend compte de chaque modèle dans son champ `templates` et son journal ; un échec à cette étape est un avertissement, jamais un déploiement en échec. Sans le builder (déploiement à la main, `mvn quarkus:dev` contre un cluster), créez le modèle dans la console de Crystallon (`/crystallon/` ; *Nouveau modèle* nommé exactement `Gluonify invoice`), *Importez* le fichier et enregistrez. **La console reste l'endroit où on le modifie** : changez-y le modèle, *Exportez*-le, remplacez le fichier et commitez-le. Crystallon **génère** alors **le code Java** d'un modèle : des records qui décrivent exactement les données qu'il lit, et une classe qui l'appelle. Ouvrez le modèle et son onglet **Code** (il demande d'enregistrer d'abord : le code est généré à partir de la version enregistrée), réglez *Paquet Java* sur `io.gluonify.source.documents.generated` et *Nom de la classe* sur `GluonifyInvoice`, laissez *Figer cette version* décochée (le code rend alors la version courante du modèle), puis *Télécharger (zip)* ou *Copier*. Les trois fichiers de [`documents/generated/`](src/main/java/io/gluonify/source/documents/generated) sont exactement cette sortie (seul un commentaire d'en-tête a été ajouté), générée à partir de la version 0 du modèle (celle que le builder crée à partir du fichier) :

- `GluonifyInvoicePayload` : un record par objet des données (`Sender`, `Recipient`, `Invoice`, `Item`, `Payment`) ; la Javadoc de chaque composant dit s'il est obligatoire ;
- `GluonifyInvoiceDocument` : la façade, avec `render(payload)`, `render(payload, RenderOptions)` et `validate(payload)` ; elle appelle le modèle **par son nom** (le même dans chaque environnement) et porte les constantes `TEMPLATE_NAME`, `TEMPLATE_VERSION` et `SCHEMA_SHA256` ;
- `package-info`.

**Pour les régénérer** (le modèle a changé et attend désormais d'autres données : son `SCHEMA_SHA256` n'est plus celui de la façade), générez de nouveau dans l'onglet Code avec les deux mêmes champs et remplacez les trois fichiers ; ne les modifiez jamais à la main. Le compilateur montre alors ce qu'il faut adapter dans [`InvoiceMapping`](src/main/java/io/gluonify/source/documents/InvoiceMapping.java), la seule classe qui connaît la forme des données. Sans la console, le même code vient de l'API (rôle `gluonify:crystallon:templates`, non `render`) : `GET /v1/templates/<id>/code?lang=java&package=io.gluonify.source.documents.generated&name=GluonifyInvoice`. Commitez ensemble le modèle exporté et le code régénéré, pour qu'ils décrivent toujours les mêmes données. Un modèle déjà présent dans Crystallon qui attend d'autres données est laissé tel quel par le builder (`differs`) : modifiez-le dans la console, ou supprimez-le pour que le déploiement suivant le recrée.

**État.** Vérifié le 2026-10-11 sur le cluster de développement : l'application (SDK 0.7.1) a rendu la facture 2026-0042 par le vrai Crystallon avec son propre jeton (200, `application/pdf`, PDF/A-1B, une page), avec un modèle créé à la main dans la console. **Pas encore** vérifié par un vrai build : l'installation automatique du modèle par le builder (couverte par des tests contre un faux Crystallon dans gluonify-bup et gluonify-crystallon).

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
| Crystallon refuse le jeton de l'application (rôle `gluonify:crystallon:render` manquant, expiré, `crystallon` absent de `uses` au déploiement) | 502 (l'appelant n'y peut rien, le déploiement oui) |
| le modèle ne peut pas rendre ces données (Crystallon 400 / 422) | 422, avec `code` et `path` |
| pas de modèle de ce nom pour l'organisation du jeton (Crystallon 404), Crystallon en panne (5XX), réponse inattendue, injoignable, jeton de l'application impossible à obtenir | 502 |
| Crystallon trop lent (30 s) | 504 |
| Crystallon indisponible | 404 |

**Dans l'interface** : un bouton *Télécharger une facture d'exemple* dans la section *Documents* (affichée seulement quand `GET /api/platform` indique `"documents": true`) envoie le jeton de la personne connectée (il n'a qu'à ouvrir cette API) et une facture d'exemple. [`DocumentsResourceTest`](src/test/java/io/gluonify/source/documents/DocumentsResourceTest.java) teste tout cela contre le faux Crystallon du SDK (`GluonifyTestServices.start()` et `close()` dans [`GluonifyTestServicesResource`](src/test/java/io/gluonify/source/documents/GluonifyTestServicesResource.java), rien à configurer) : le chemin appelé (`/v1/templates/by-name/Gluonify%20invoice/render`), l'appel hors requête avec le jeton de l'application, la charge utile et chaque erreur ci-dessus.

### Appeler une autre application

Déclarez-la dans `"uses"` : la plateforme fournit `GLUONIFY_SERVICE_<APP>_URL` **et ouvre le réseau** vers elle (sans `uses`, l'application ne la joint pas). N'écrivez jamais l'adresse en dur : lisez la variable (voir `ping` dans [`PlatformResource`](src/main/java/io/gluonify/source/platform/PlatformResource.java)).

### Sites statiques et domaines

- **Field** sert un site statique (ZIP) sur votre domaine ; rien à coder dans ce service.
- **Votre domaine** : `PUT /domains/<nom>` sur l'API de Higgs (voir [`deploy/domain.json`](deploy/domain.json)) route un chemin vers l'application et applique des **politiques** : jeton Charm (audience, rôles), limites de débit, CORS, listes d'adresses. Le certificat HTTPS est obtenu automatiquement.

## 8. Construire

```bash
mvn test                       # tests Java (121)
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

**Les services que le builder déclare.** Le builder envoie `uses` et `gdownDatabase` à `PUT /apps/<nom>`. `uses` est **déduit au build** : chaque module client du SDK porte un marqueur `META-INF/gluonify/services/<service>` ; Bup lit les marqueurs des jars du chemin de classes d'exécution (groupe `io.gluonify.plasma` seulement) et les ajoute en services facultatifs (ici `?crystallon`, venu de `gluonify-plasma-crystallon-client`). Il les fusionne avec les services déclarés dans la requête de build ou, si la requête n'en a pas, dans le [`gluonify.json`](gluonify.json) à la racine du dépôt (la requête l'emporte ; une liste vide dans la requête signifie « aucun service déclaré », les services déduits s'ajoutent quand même). **Ne déclarez dans `uses` que ce que le SDK ne détecte pas** : un appel HTTP direct à une autre application, Charm pour OIDC, Gdown par HTTP. Le fichier de ce dépôt est donc `{"uses": ["charm", "gdown"]}` : avec `?crystallon` déduit, les mêmes services que [`deploy/appspec.json`](deploy/appspec.json), ce que `DeploySpecsTest` vérifie. Sans ce fichier, un build déploierait l'application sans réseau vers Charm et Gdown, car `PUT /apps/<nom>` **remplace toute la spécification**. Inutile d'y ajouter `"gdownDatabase": true` : le builder le déduit des références à `GLUONIFY_GDOWN_DATABASE|USER|PASSWORD` dans `application.properties` (`GET /v1/builds/{id}/effective-spec` montre le `gluonify.json` effectif) ; un `?` initial (`"?orders"`) marque un service facultatif. Une application qui ne joint des services que par le SDK n'a pas besoin de `gluonify.json`.

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
| `uses` | services joignables, par nom d'application (`charm`, `gdown`, `crystallon`…) : `GLUONIFY_SERVICE_<APP>_URL` fournie, réseau ouvert ; pour `crystallon`, aussi le jeton de l'application (`GLUONIFY_CRYSTALLON_TOKEN_FILE`). Avec le builder : déduits des modules du SDK, plus ce que déclarent `gluonify.json` ou la requête de build |
| `distributed` | `["std"]` pour `/distributed/std` |
| `env` | variables **non secrètes** ; `${NOM}` et `${NOM:-défaut}` sont résolus |
| `top` | `true` : le plan de contrôle attribue à l'application son propre espace de coffre `<uuid>.app`, dont les clés arrivent en `APP_<CLÉ>` |
| `gdownDatabase` | `true` : une base Gdown dédiée pour l'application (groupe Raft propre, compte confiné) ; `GLUONIFY_GDOWN_DATABASE`, `GLUONIFY_GDOWN_USER`, `GLUONIFY_GDOWN_PASSWORD` et `GLUONIFY_SERVICE_GDOWN_URL` sont fournis ; jamais supprimée avec l'application (voir [`deploy/appspec-graph.json`](deploy/appspec-graph.json)) |
| `topNamespace` | alternative : un espace de coffre existant, par son nom ; ses clés arrivent **sans préfixe** (pas de `APP_`) |
| `internal` | `true` : aucune route publique (service interne) |

Mettre à jour : même `PUT` (ou nouveau build) ; les répliques sont remplacées **une à une**. Redémarrer sans changer : `POST /apps/<nom>/redeploy`. Journaux : `GET /apps/<nom>/logs`. Mesures : `/q/metrics`.

## 10. Tester

```bash
mvn test                                 # 121 tests Java
cd src/main/webui && npm install && npm test   # 53 tests d'interface
```

| Test | Tests | Ce qu'il vérifie |
|---|---|---|
| `NotesResourceTest` | 7 | 401 sans jeton, 403 sans le bon rôle, cycle de vie complet, validation 400, OpenAPI, santé, mesures |
| `AuthConfigResourceTest` / `AuthConfigConfiguredTest` | 2 + 1 | `GET /api/auth-config` est public et vide sans client ; les adresses publiques de Charm sont déduites de l'émetteur, jamais de l'adresse interne `GLUONIFY_SERVICE_CHARM_URL` |
| `FileNoteStoreTest` | 6 | fichiers neufs jamais renommés, ordre, deux répliques sur le même dossier, fichiers illisibles ignorés, identifiants jamais des chemins |
| `GraphNoteStoreTest` | 4 | contre un faux Gdown : chemin, authentification, instruction **paramétrée**, lecture des lignes, échec |
| `NoteStoresTest` | 3 | choix du stockage : `auto` donne `gdown` quand `GLUONIFY_GDOWN_DATABASE` est fourni, `memory` sinon ; une valeur explicite l'emporte |
| `WebhookResourceTest` / `WebhookClosedTest` | 4 + 1 | clé, acquittement, **idempotence**, rejets ; fermé sans clé |
| `PlatformResourceTest` | 4 | variables de la plateforme lues, **aucun secret renvoyé**, appel de service à service limité aux services déclarés |
| `ConformityTest` | 4 | les normes du builder, chez vous |
| `EventLedgerTest` | 4 | registres d'idempotence : un seul gagnant par événement (mémoire, fichiers avec deux répliques, contrainte d'unicité du graphe, erreur du graphe autre qu'un doublon propagée) |
| `DocumentsResourceTest` | 13 | contre le **faux Crystallon du SDK** (`GluonifyTestServices`) : PDF renvoyé, chemin appelé (`/v1/templates/by-name/Gluonify%20invoice/render`), **jeton de l'application** (aussi hors requête), charge utile envoyée, correspondance des 422 / 502, validation propre (400), rôles, OpenAPI |
| `DocumentsDisabledTest` | 3 | Crystallon indisponible : 404 avec la raison, `"documents": false`, le reste n'est pas touché |
| `InvoiceMappingTest` / `DocumentErrorsTest` | 3 + 7 | votre facture → la charge utile du modèle (montants, valeurs par défaut, nom de fichier sûr) ; chaque échec de Crystallon → un statut |
| `DocumentsReflectionTest` | 1 | garde-fou natif : chaque record des données générées est déclaré pour la réflexion |
| `CrystallonTemplateFileTest` | 1 | le modèle Crystallon du dépôt (`crystallon/templates/gluonify-invoice.json`) : JSON valide, le nom exact qu'appelle le code généré, les mêmes champs de premier niveau que les données générées (l'empreinte `SCHEMA_SHA256` n'est calculée que par Crystallon) |
| `DeploySpecsTest` | 2 | les spécifications de `deploy/` utilisent les noms actuels (`uses`, `SOURCE_STORE`) ; `gluonify.json` (lu par le builder) ne déclare que ce que le SDK ne détecte pas (`charm`, `gdown`) et, avec `crystallon` déduit (marqueur dans le chemin de classes), donne les mêmes `uses` que `deploy/appspec.json` |

Un test d'API se copie de `NotesResourceTest` ; un test de stockage de `FileNoteStoreTest` (sans démarrer Quarkus : rapide).

## 11. Pièges connus

- **Natif** : ne créez **pas** de `HttpClient`, de `Random` ni de `SecureRandom` dans un champ `static` (l'état serait figé à la compilation, pas à l'exécution) : créez-les à la première utilisation (voir `GraphNoteStore.client()`). Lisez le JSON de tiers en arbre (`JsonNode`) plutôt que dans des classes ; un type (dé)sérialisé par Jackson hors d'une signature REST doit porter `@RegisterForReflection`. **Un bogue qui n'apparaît qu'en natif ne se voit pas dans `mvn test`** : lancez l'exécutable natif avant de livrer.
- **Jackson 3** : le paquet est `tools.jackson.databind`, pas `com.fasterxml.jackson.databind`.
- **Aucune adresse, aucun port, aucun mot de passe en dur** : tout vient de l'environnement.
- **Fichiers distribués** : voir les règles du [§7](#fichiers-distribués-sourcestorefiles) (ne jamais réécrire, ~3 s de retard).
- **Webhooks** : répondre vite en 2XX, être idempotent, protéger par une clé.
- **Code généré** : les fichiers de `documents/generated/` viennent de l'onglet *Code* de Crystallon : régénérez-les, ne les modifiez jamais à la main ; et déclarez chaque record d'un payload généré dans `DocumentsReflection` (un échec qui n'apparaît qu'en natif).
- **Une identité de développement n'a rien à faire en production** : gardez `@IfBuildProfile("dev")`.
- **Quinoa et les adresses d'API** : ajoutez tout nouveau préfixe d'API à `quarkus.quinoa.ignored-path-prefixes`.
- **Sous émulation x86_64** (Docker sur Apple Silicon) : construisez avec `--build-arg MAVEN_OPTS=-Djdk.lang.Process.launchMechanism=VFORK`.

## 12. Dépannage

| Symptôme | Cause probable |
|---|---|
| 401 en production | pas de jeton, jeton expiré, ou **audience** différente de `GLUONIFY_SOURCE_OIDC_AUDIENCE` ; ou `GLUONIFY_SOURCE_OIDC_ISSUER` ne correspond pas à l'`iss` du jeton |
| 403 | jeton valide mais sans le rôle (`source:read` / `source:write`) |
| Pas de bouton *Se connecter*, la page cite `GLUONIFY_SOURCE_OIDC_CLIENT_ID` | l'identifiant de client n'est pas fourni, ou l'adresse publique de Charm est inconnue (`GLUONIFY_SOURCE_OIDC_ISSUER` laissé à `any` et pas de `GLUONIFY_SOURCE_CHARM_PUBLIC_URL`) |
| Charm refuse la connexion (`redirect_uri`, `invalid_target`) | la redirection du client n'est pas exactement l'adresse de l'interface (barre finale comprise), ou ses audiences ne contiennent pas `gluonify-source` |
| Connecté, mais 403 sur les notes | le compte n'a pas `source:read` / `source:write` (donner les rôles dans Charm, puis se déconnecter et se reconnecter : les rôles sont lus à la connexion) |
| L'application ne reçoit pas de trafic | `/q/health/ready` ne répond pas 200 (stockage injoignable ?) : `GET /apps/<nom>` montre les instances prêtes |
| `store=gdown` : « source.graph.url is empty » | l'application n'est pas déployée avec `"uses": ["gdown"]` |
| Gdown : 401 ou « Unsupported property value type » | mauvais compte ; ou une **carte** stockée comme propriété (voir §7) |
| Une note créée n'apparaît pas tout de suite (fichiers) | cache de liste de ~3 s entre répliques : rafraîchir |
| Le build du dépôt est refusé | une règle `R-…` : le message la nomme ; `mvn test` (`ConformityTest`) la montre chez vous |
| `mvn package` ne trouve pas Node | accès réseau pour le télécharger, ou `-Dquarkus.quinoa=false` pour construire sans l'interface |
| Le natif plante au démarrage mais pas en JVM | un état figé à la compilation (`static`) ou une réflexion manquante (§11) |
| `POST /api/documents/invoice` : 404 « documents are off » | Crystallon n'est pas disponible : l'application n'est pas déployée avec `"uses": ["crystallon"]` (par le builder : SDK antérieur à la 0.7.1, ou déduction désactivée) ou, sur votre poste, pas de `gluonify login <zone>` |
| Documents : 502 « did not accept the token of the application » | l'application n'est pas déployée avec `"uses": ["crystallon"]`, la plateforme n'émet pas encore de jetons d'application, ou le fichier du jeton manque, est vide ou périmé ; sur votre poste, relancez `gluonify login <zone>` |
| Documents : 502 « does not know the template » | aucun modèle nommé `Gluonify invoice` dans l'organisation du jeton (jeton de l'application : l'organisation de l'application, ou celle par défaut de Crystallon) : lisez `templates` dans le build du builder (`GET /v1/builds/<id>` : `failed` avec sa raison ; absent quand l'application n'utilise pas Crystallon ou que le fichier manque) ; sans le builder, importez `crystallon/templates/gluonify-invoice.json` dans la console de Crystallon |
| Builder : modèle `Gluonify invoice` signalé `differs` | le modèle de Crystallon attend d'autres données que le fichier : rien n'a été écrit ; modifiez-le dans la console (ou supprimez-le), exportez-le, régénérez le code et commitez les deux |
| Documents : 422 avec `code` et `path` | le modèle ne peut pas rendre ces données : `path` est l'endroit du problème dans le modèle |
| Les documents marchent en JVM mais échouent au premier appel dans l'exécutable natif | un record des données générées manque dans `DocumentsReflection` |

---

*Gluonify est une plateforme minimaliste pour applications Quarkus natives : [gluonify.cloud](https://gluonify.cloud) (présentation) et [gluonify.io](https://gluonify.io) (technique). Ce dépôt est volontairement petit : il se lit en une heure.*

## Licence

Copyright (c) 2026 Gluonify.

Ce dépôt est publié sous la [licence Apache, version 2.0](LICENSE). Vous pouvez l'utiliser, le modifier
et le redistribuer, y compris à des fins commerciales, aux conditions de cette licence ; il est fourni
« en l'état », sans garantie. Les composants tiers restent sous leurs propres licences (voir `NOTICE`
s'il existe).
