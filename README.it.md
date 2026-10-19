# gluonify-source

[English](README.md) · [Français](README.fr.md) · [Español](README.es.md) · **Italiano** · [Deutsch](README.de.md)

> **Variante Gradle.** Lo stesso servizio, costruito con **Gradle** (Kotlin DSL) invece di Maven, esiste in [`gluonify-source-gradle`](https://github.com/gluonify/gluonify-source-gradle): stesso codice, stessi test, stesse regole della piattaforma.

**Il punto di partenza di un servizio Quarkus 4 su [Gluonify](https://gluonify.cloud).** Un piccolo servizio completo (delle «note») che mostra, con codice funzionante e test, come:

1. creare un **servizio REST** (validazione, ruoli, OpenAPI);
2. creare un'**interfaccia web** con [Quinoa](https://quarkus.io/extensions/io.quarkiverse.quinoa/quarkus-quinoa/) (Vue 3, servita dallo stesso eseguibile);
3. **interfacciarsi con i servizi di Gluonify**: configurazione e segreti (Top), token di identità (Charm), dati (Gdown) o file distribuiti, altre applicazioni, webhook ricevuti da Photon, documenti PDF (Crystallon), siti statici (Field), domini e policy (Higgs);
4. **compilarlo come eseguibile nativo** e **distribuirlo**.

È pensato per uno sviluppatore o per **un assistente IA** che parte da zero con un servizio: ogni scelta è spiegata qui, ogni riga di configurazione è commentata nel codice, e [`CLAUDE.md`](CLAUDE.md) riassume le regole per un'IA.

> Gluonify esegue **applicazioni Quarkus native** (un file eseguibile, senza JVM) su macchine Debian. Lei dà accesso al Suo codice su Git; la piattaforma lo compila, lo distribuisce, lo mette in sicurezza e lo fa girare.

## Indice
1. [Iniziare in 5 minuti](#1-iniziare-in-5-minuti)
2. [La mappa del progetto](#2-la-mappa-del-progetto)
3. [Rinominare il progetto](#3-rinominare-il-progetto)
4. [Creare un servizio REST](#4-creare-un-servizio-rest)
5. [Creare un'interfaccia con Quinoa](#5-creare-uninterfaccia-con-quinoa)
6. [Autenticazione: i token di Charm](#6-autenticazione-i-token-di-charm)
7. [Interfacciarsi con i servizi di Gluonify](#7-interfacciarsi-con-i-servizi-di-gluonify)
8. [Compilare](#8-compilare)
9. [Distribuire](#9-distribuire)
10. [Testare](#10-testare)
11. [Insidie note](#11-insidie-note)
12. [Risoluzione dei problemi](#12-risoluzione-dei-problemi)

---

## 1. Iniziare in 5 minuti

**Prerequisiti**: Java 25 (`JAVA_HOME`), Maven 3.9+. Node.js non è necessario: Quinoa scarica il proprio (v24.3.0) alla prima compilazione. Docker serve solo per l'eseguibile nativo.

```bash
git clone https://github.com/gluonify/gluonify-source.git
cd gluonify-source
mvn quarkus:dev
```

Apra <http://localhost:8080>: l'interfaccia (aggiungere, elencare, eliminare note). Inoltre:

| Indirizzo | Contenuto |
|---|---|
| `/` | l'interfaccia Vue |
| `/api/notes` | l'API REST (JSON) |
| `/api/documents/invoice` | `POST`: una fattura in PDF, prodotta da Crystallon quando è configurato (vedere [§7](#documenti-crystallon)) |
| `/q/swagger-ui` | la documentazione dell'API (interattiva in dev e test, in sola lettura in produzione: né Authorize né Try it out) |
| `/q/openapi` | il contratto OpenAPI (JSON o YAML) |
| `/q/health/ready` · `/q/health/live` | salute: «pronto» e «vivo» (vedere [§7](#7-interfacciarsi-con-i-servizi-di-gluonify)) |
| `/q/metrics` | metriche Prometheus |

In **sviluppo** non viene richiesto alcun token: un'identità «dev» (con tutti i ruoli) esiste solo in questo profilo (`@IfBuildProfile("dev")`, assente dall'eseguibile di produzione). Le note sono **in memoria** (`source.store=auto` ripiega sulla memoria senza database dedicato): scompaiono allo spegnimento.

```bash
curl -s localhost:8080/api/notes -H 'Content-Type: application/json' -d '{"title":"Courses","body":"du pain"}'
curl -s localhost:8080/api/notes
```

## 2. La mappa del progetto

```
pom.xml                         progetto Maven AUTONOMO (Quarkus 4.0.0.Beta1, Java 25): nessun parent Gluonify
gluonify.json                   letto dal builder di Gluonify (Bup): solo i servizi che l'SDK non rileva (`uses`: charm, gdown); Bup deduce il resto
crystallon/templates/           il modello Crystallon dell'esempio, come lo esporta la console; il builder lo installa alla distribuzione
Dockerfile.build                compila l'eseguibile nativo in Docker
src/main/resources/
  application.properties        TUTTA la configurazione, commentata riga per riga
src/main/java/io/gluonify/source/
  SourceConfig.java             la configurazione dell'applicazione (prefisso «source.»)
  notes/
    Note.java, NewNote.java     il modello (record) e il corpo di creazione (validato)
    NotesResource.java          ← IL MODELLO DI UNA RISORSA REST (da copiare)
    NoteStore.java              l'interfaccia dell'archiviazione; tre implementazioni:
    MemoryNoteStore.java          in memoria (sviluppo)
    FileNoteStore.java            file in /distributed/std (durevole, condiviso dalle repliche)
    GraphNoteStore.java           Gdown, il database a grafo della piattaforma
    NoteStores.java             sceglie l'implementazione (source.store): il punto dove collegare la Sua
    StoreHealth.java            la condizione «pronto» (/q/health/ready)
  platform/
    PlatformResource.java       variabili fornite dalla piattaforma; chiamata a un'altra applicazione
    WebhookResource.java        ricevere le consegne di Photon
  documents/                    documenti PDF con Crystallon (facoltativo)
    DocumentsResource.java      POST /api/documents/invoice: la Sua fattura in ingresso, un PDF in uscita
    InvoiceRequest.java         la fattura del SUO dominio (corpo validato)
    InvoiceMapping.java         la Sua fattura -> i dati del modello (l'unica classe che ne conosce la forma)
    Documents.java              Crystallon è disponibile? Nulla da configurare: il client viene dall'SDK (gluonify-plasma-quarkus)
    DocumentErrors.java         errore di Crystallon -> 403 / 422 / 502 / 504
    DocumentsReflection.java    immagine nativa: i record dei dati generati
    generated/                  codice generato dalla scheda «Codice» di Crystallon (non modificare a mano)
  security/DevAuthentication.java   identità di sviluppo (solo profilo dev)
  security/AuthConfigResource.java GET /api/auth-config: ciò che serve all'interfaccia per accedere con Charm (pubblico)
src/main/webui/                 l'interfaccia Vue 3 (Vite + vitest), compilata da Quinoa
  src/auth.js                   accesso con Charm (codice di autorizzazione + PKCE) tramite il kit @gluonify/ui
  vendor/gluonify-ui/           copia inclusa del kit (gluonify-ui/scripts/sync-vendor.sh, mai modificata a mano)
src/test/java/…                 70 test Java; src/main/webui/src/App.test.js: 40 test di interfaccia
deploy/                         esempi: AppSpec, dominio, webhook Photon, database Gdown
scripts/rename.py               rinomina il progetto
```

## 3. Rinominare il progetto

Il Suo servizio non si chiama `gluonify-source`. Un comando cambia le coordinate Maven, il package Java (cartelle comprese), il nome dell'eseguibile, il titolo OpenAPI e l'**audience** attesa dei token:

```bash
python3 scripts/rename.py com.acme shop-api com.acme.shop
mvn test
```

(`groupId` `com.acme`, `artifactId` `shop-api`, package Java `com.acme.shop`. Lo script può essere rilanciato senza rischi.) Il nome dell'artefatto diventa anche il nome dell'applicazione su Gluonify; lo scelga come un nome DNS: minuscole, cifre, trattini.

## 4. Creare un servizio REST

Legga [`NotesResource.java`](src/main/java/io/gluonify/source/notes/NotesResource.java): è il modello. Per aggiungere **la Sua** risorsa, ad esempio degli ordini:

1. **Il modello**: un `record` Java (immutabile, Jackson lo legge e lo scrive, OpenAPI lo descrive).
   ```java
   public record Order(String id, String customer, int quantity) {}
   public record NewOrder(@NotBlank String customer, @Min(1) int quantity) {}   // validato
   ```
2. **La risorsa**: una classe con `@Path`, metodi `@GET`/`@POST`…, i ruoli con `@RolesAllowed`, la validazione con `@Valid`.
   ```java
   @Path("/api/orders")
   @Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
   public class OrdersResource {
       @POST @RolesAllowed("shop:write")
       public Response create(@Valid NewOrder in) { … return Response.created(uri).entity(order).build(); }
   }
   ```
3. **La documentazione**: `@Operation(summary = "…")` e `@Tag(name = "…")` (microprofile-openapi): compaiono in `/q/swagger-ui`. Il contratto viene rigenerato a ogni avvio.
4. **I test**: copi [`NotesResourceTest`](src/test/java/io/gluonify/source/NotesResourceTest.java). `@TestSecurity(user = "ada", roles = {"shop:write"})` fissa l'identità di un test; senza annotazione, è una chiamata senza token (atteso: 401).
5. **I codici HTTP**: 201 + `Location` alla creazione, 204 senza corpo, 400 per un corpo non valido (Hibernate Validator), 401 senza token, 403 senza il ruolo giusto, 404 per un identificatore sconosciuto. Gli errori restituiscono `{"error": "…"}`.

**L'archiviazione** sta dietro l'interfaccia [`NoteStore`](src/main/java/io/gluonify/source/notes/NoteStore.java): la risorsa non sa dove vivono i dati. Per la Sua archiviazione (un altro database, un'altra cartella), implementi l'interfaccia e aggiunga un caso in [`NoteStores`](src/main/java/io/gluonify/source/notes/NoteStores.java).

## 5. Creare un'interfaccia con Quinoa

[Quinoa](https://quarkus.io/extensions/io.quarkiverse.quinoa/quarkus-quinoa/) compila un progetto web (qui Vue 3 + Vite, in `src/main/webui`) e lo serve **dallo stesso eseguibile** dell'API: un solo file da distribuire, una sola origine (niente CORS).

- **Sviluppare**: `mvn quarkus:dev` avvia anche il server Vite (porta 5173, hot reload dell'interfaccia); Quarkus serve l'API. Apra <http://localhost:8080>.
- **Chiamare l'API**: indirizzi relativi (`fetch('/api/notes')`), vedere [`src/api.js`](src/main/webui/src/api.js). Il token Charm viene dall'accesso ([`src/auth.js`](src/main/webui/src/auth.js), vedere [§6](#6-autenticazione-i-token-di-charm)), è conservato per la scheda (`sessionStorage`) e aggiunto come `Authorization: Bearer`.
- **Le rotte dell'interfaccia**: `quarkus.quinoa.enable-spa-routing=true` restituisce `index.html` per un indirizzo sconosciuto (utile con vue-router). Ma **un indirizzo d'API sconosciuto non deve restituire l'interfaccia**: `quarkus.quinoa.ignored-path-prefixes=/api,/hooks,/q` (già impostato). Se aggiunge un prefisso d'API, lo aggiunga qui.
- **Compilare**: `mvn package` esegue `npm install` poi `npm run build`; `dist/` viene incorporato nell'eseguibile. Senza interfaccia: `-Dquarkus.quinoa=false`.
- **Lingue (i18n)**: l'interfaccia è disponibile in inglese (predefinito), francese, spagnolo, italiano e tedesco. Nessun testo è scritto nei componenti: ogni testo è una chiave di [`src/i18n/`](src/main/webui/src/i18n) (`en.js`, `fr.js`, `es.js`, `it.js`, `de.js`) letta con `t('chiave', { parametro })` (una piccola utilità in `index.js`, senza dipendenze). Il selettore nell'intestazione ricorda la scelta (`localStorage`); senza scelta si usa la lingua del browser se è una delle cinque, altrimenti l'inglese; `<html lang>` si adegua. Per aggiungere una lingua: copi `en.js` in `<codice>.js` e traduca i valori (stesse chiavi), poi lo importi in `index.js` (`MESSAGES` e `LANGUAGES`). I messaggi di errore restituiti dall'API sono mostrati così come arrivano; quelli dei codici 401 e 403 sono tradotti.
- **Testare l'interfaccia**: `cd src/main/webui && npm install && npm test` (vitest + jsdom, `fetch` simulato: vedere `App.test.js`). Con `quarkus.quinoa.run-tests=true`, anche `mvn package` li esegue.
- **Cache**: i file di `dist/assets` hanno un'impronta nel nome (immutabili); `index.html` viene servito con `Cache-Control: no-cache` (impostazione `ui-entry`).

Un'altra tecnologia (React, Svelte…): sostituisca il contenuto di `src/main/webui`; Quinoa richiede solo un `package.json` con gli script `dev` e `build` e una cartella di output (`quarkus.quinoa.build-dir`).

## 6. Autenticazione: i token di Charm

L'API è **chiusa senza token**. Si aspetta un token bearer (JWT) emesso da **gluonify-charm**, il provider di identità di Gluonify (OpenID Connect, ES256):

```
Authorization: Bearer <token>
```

Il servizio non **crea** mai token: ne **verifica** (modalità `quarkus.oidc.application-type=service`). Cosa controlla:

| Controllo | Impostazione | Valore |
|---|---|---|
| Firma | chiavi di Charm, lette da `${GLUONIFY_SERVICE_CHARM_URL}/realms/gluonify` | `GLUONIFY_SERVICE_CHARM_URL` è fornita dalla piattaforma se l'applicazione è distribuita con `"uses": ["charm"]` |
| Emittente (`iss`) | `quarkus.oidc.token.issuer` | `GLUONIFY_SOURCE_OIDC_ISSUER` = `https://id.<la Sua zona>/realms/gluonify` (stabile: non è l'indirizzo dell'istanza di Charm) |
| Audience (`aud`) | `quarkus.oidc.token.audience` | `GLUONIFY_SOURCE_OIDC_AUDIENCE`, per impostazione predefinita il nome del progetto (`gluonify-source`) |
| Scadenza | automatica | token di breve durata |
| Ruoli | claim `roles` | `source:read` (leggere), `source:write` (scrivere): vedere `@RolesAllowed`; il prefisso `gluonify:` è riservato alla piattaforma (Charm lo rifiuta per i ruoli di un’applicazione) |

**Ottenere un token di test** (l'amministratore della piattaforma, con il token di amministrazione di Charm):

```bash
curl -s -X POST "$CHARM_URL/v1/tokens" -H "Authorization: Bearer $GLUONIFY_CHARM_ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"sub":"ada","audience":"gluonify-source","ttlSeconds":3600,"roles":["source:read","source:write"]}'
# -> {"token": "eyJ…"}
curl -s https://gluonify-source.<zone>/api/notes -H "Authorization: Bearer eyJ…"
```

Un valore di audience diverso (`"audience": "other"`) restituisce **401**; un token valido senza il ruolo giusto restituisce **403**.

### Accedere dall'interfaccia (pagina di accesso di Charm)

L'interfaccia non chiede mai una password e non fa mai incollare un token. È un **client OpenID Connect pubblico** di Charm (codice di autorizzazione + PKCE S256, nessun segreto nel browser), costruito sul kit comune `@gluonify/ui` (`createAuth`, lo stesso accesso delle console della piattaforma), incluso in [`src/main/webui/vendor/gluonify-ui`](src/main/webui/vendor/gluonify-ui) perché un clone si costruisca da solo (aggiornarlo con `gluonify-ui/scripts/sync-vendor.sh <questo repository> src/main/webui`, mai a mano):

1. La pagina chiede `GET /api/auth-config` (pubblico): l'identificativo del client, gli indirizzi **pubblici** di Charm (`/authorize`, `/token`, `/logout`) e `signInRequired` (falso con `mvn quarkus:dev`: lì l'API non chiede alcun token, quindi l'interfaccia mostra tutto).
2. **Accedi** porta il browser alla pagina di Charm: la persona digita nome utente e password **lì**, mai nell'applicazione. Charm riporta il browser all'interfaccia (`https://<indirizzo dell'interfaccia>/?code=…&state=…`).
3. L'interfaccia verifica `state`, scambia il codice con il suo verificatore PKCE su `/token` di Charm e ottiene un token di audience `gluonify-source` il cui claim `roles` contiene i ruoli dell'account (`source:read`, `source:write`). Il token è conservato per la scheda (`sessionStorage`), rinnovato con il refresh token prima della scadenza e inviato come `Authorization: Bearer` a `/api`.
4. **Esci** dimentica il token, avvisa le altre schede e chiude la sessione di Charm (`/logout` con `id_token_hint`). Un 401 dell'API (token scaduto o revocato) mostra di nuovo l'accesso.

Senza accesso, la pagina mostra solo una breve spiegazione e il pulsante **Accedi**: note, documenti e sezione della piattaforma richiedono tutti un token.

**L'accesso funziona senza alcuna configurazione una volta distribuita sulla piattaforma.** Un'applicazione che dichiara `"uses": ["charm"]` (Bup lo deduce dal codice) riceve un proprio client pubblico di Charm, `app-<nome>`, creato e tenuto aggiornato dalla piattaforma: i suoi reindirizzamenti sono esattamente gli indirizzi dell'applicazione (`https://<nome>.<zona>/` e le route dei suoi domini), la sua audience è `gluonify-<nome>`. La piattaforma inietta `GLUONIFY_OIDC_CLIENT_ID`, `GLUONIFY_OIDC_ISSUER` (emittente pubblico), `GLUONIFY_OIDC_PUBLIC_URL` (indirizzo pubblico di Charm) e `GLUONIFY_OIDC_AUDIENCE`, valori predefiniti qui sotto; le variabili `GLUONIFY_SOURCE_OIDC_*` hanno sempre la precedenza. **Per un'applicazione di un'organizzazione** (dal 2026-10-19), il client si trova nel realm della sua organizzazione, `org-<org>`: l'emittente è `https://<charm>/realms/org-<org>`, gli utenti sono quelli che l'organizzazione gestisce in quel realm (non account Gluonify), la piattaforma inietta anche `GLUONIFY_OIDC_REALM` (`org-<org>`, il realm di cui l'API legge discovery e chiavi tramite `GLUONIFY_SERVICE_CHARM_URL`), e l'interfaccia usa gli endpoint del realm (`/realms/org-<org>/protocol/openid-connect/auth`, `/token`, `/logout`). Crei i ruoli `source:read` e `source:write` nel realm e li assegni ai suoi utenti.

| Impostazione | Variabile | Valore |
|---|---|---|
| `source.auth.client-id` | `GLUONIFY_SOURCE_OIDC_CLIENT_ID` | il client Charm di questa interfaccia (per esempio `source-ui`); vuoto: nessun pulsante *Accedi*, la pagina nomina la variabile; predefinito `GLUONIFY_OIDC_CLIENT_ID` (iniettata dalla piattaforma) |
| `source.auth.charm-url` | `GLUONIFY_SOURCE_CHARM_PUBLIC_URL` | l'indirizzo **pubblico** di Charm, quello raggiunto dal browser; vuoto: dedotto da `GLUONIFY_SOURCE_OIDC_ISSUER` (`https://gluonify.lan/realms/gluonify` → `https://gluonify.lan`). Mai `GLUONIFY_SERVICE_CHARM_URL`: è un indirizzo interno |
| `source.auth.scope` | `SOURCE_AUTH_SCOPE` | `openid profile` (il kit aggiunge `offline_access` per il refresh token) |

**Fuori dalla piattaforma, o per usare un altro client**, registrarlo da sé (una volta, dall'amministratore della piattaforma) e impostare `GLUONIFY_SOURCE_OIDC_CLIENT_ID`: un client pubblico il cui unico reindirizzamento (e reindirizzamento dopo l'uscita) è l'indirizzo esatto dell'interfaccia, barra finale compresa, e la cui audience è quella dell'applicazione. Charm accetta chiamate da un'altra origine verso `/token` solo dalle origini dei reindirizzamenti registrati.

```bash
curl -s -X PUT "$CHARM_URL/v1/admin/clients/source-ui" -H "Authorization: Bearer $GLUONIFY_CHARM_ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Source","redirectUris":["https://source.<zone>/"],"postLogoutRedirectUris":["https://source.<zone>/"],"audiences":["gluonify-source"],"scopes":["openid","profile","email","offline_access"],"accessTtl":3600}'
```

**I ruoli** `source:read` e `source:write` si assegnano agli account, compresi quelli dei clienti (i ruoli di un'applicazione non sono `gluonify:*`, quindi Charm consente di crearli e assegnarli): `PUT /v1/admin/roles/source:read` (e `source:write`), poi `PUT /v1/admin/users/<id>/roles`. Charm mette tutti i ruoli dell'account nel token: uscire e accedere di nuovo dopo una modifica.

**In locale**, il profilo `dev` fornisce l'identità «dev» (vedere [`DevAuthentication`](src/main/java/io/gluonify/source/security/DevAuthentication.java)); **nei test**, `@TestSecurity`. Mai un'identità di questo tipo in produzione: viene compilata solo nel profilo `dev`.

## 7. Interfacciarsi con i servizi di Gluonify

Un'applicazione su Gluonify è **isolata**: un proprio account, una propria rete, un file system ridotto. Raggiunge solo ciò che **dichiara** (`"uses"`) e riceve la propria configurazione **dall'ambiente**. Ecco ogni servizio, con il punto del codice.

**Nessuna configurazione per i servizi coperti dall'SDK.** L'SDK di Gluonify trova da solo dove si trova un servizio e quale token presentare: la piattaforma fornisce l'indirizzo e il token dell'applicazione (`"uses"`), `gluonify login <zona>` fa lo stesso sulla Sua macchina e `GluonifyTestServices` sostituisce i servizi nei test. Oggi l'SDK copre **Crystallon** (vedere [Documenti](#documents-crystallon)); Gdown e Charm non sono ancora coperti e restano configurati tramite URL (`source.graph.url`, `quarkus.oidc.auth-server-url`): seguiranno.

| Servizio | Cosa offre | Come lo usa l'applicazione | In questo repository |
|---|---|---|---|
| **Top** (vault) | configurazione e segreti per applicazione | con `"top": true`, le chiavi dello spazio `<uuid>.app` proprio della Sua applicazione arrivano come `APP_<CHIAVE>`; `${app.gluonify.gdown.password}` in `application.properties` | `source.graph.password`, `source.webhook.key` |
| **Charm** (identità) | token JWT di breve durata | `"uses": ["charm"]` → `GLUONIFY_SERVICE_CHARM_URL`; `quarkus-oidc` verifica i token | [§6](#6-autenticazione-i-token-di-charm) |
| **Gdown** (database a grafo) | database replicato (Raft), Cypher via HTTP | `"uses": ["gdown"]` → `GLUONIFY_SERVICE_GDOWN_URL`; un account locale di Gdown | [`GraphNoteStore`](src/main/java/io/gluonify/source/notes/GraphNoteStore.java) |
| **File distribuiti** | `/distributed/std`: durevole, condiviso da tutte le repliche, 2 copie | `"distributed": ["std"]` alla distribuzione | [`FileNoteStore`](src/main/java/io/gluonify/source/notes/FileNoteStore.java) |
| **Altre applicazioni** | chiamata da servizio a servizio | `"uses": ["other"]` → `GLUONIFY_SERVICE_OTHER_URL` | [`PlatformResource.ping`](src/main/java/io/gluonify/source/platform/PlatformResource.java) |
| **Photon** (gateway API) | webhook firmati, conversione XML / SOAP / form → JSON, nuovi tentativi | Photon consegna a `…/hooks/events`; il Suo servizio conferma con 2XX | [`WebhookResource`](src/main/java/io/gluonify/source/platform/WebhookResource.java) |
| **Crystallon** (documenti PDF) | fatture, preventivi e lettere in PDF/A da modelli disegnati nel suo editor; client Java dell'SDK, codice generato | facoltativo, **senza configurazione**: nulla da dichiarare (costruita da Bup, `?crystallon` è dedotto dall'SDK) e l'SDK (`gluonify-plasma-quarkus`) trova l'indirizzo e il token proprio dell'applicazione (ruolo `gluonify:crystallon:render`); sulla Sua macchina `gluonify login` | [`DocumentsResource`](src/main/java/io/gluonify/source/documents/DocumentsResource.java), [dettagli](#documents-crystallon) |
| **Field** | siti statici (ZIP) serviti sul Suo dominio | niente da programmare: un sito si carica come ZIP | — |
| **Higgs** (orchestratore) | domini, certificati HTTPS automatici, rotte, policy (token, limiti, CORS, indirizzi) | `PUT /domains/<nome>` | `deploy/domain.json` |

### Configurazione e segreti (Top)

- Un valore **non segreto**: in `"env"` della distribuzione (`deploy/appspec.json`): `"SOURCE_STORE": "files"`. Viene memorizzato nello stato del cluster.
- Un valore **segreto** (password, chiave d'API): nel **vault** (Top, spazio `<uuid>.app` della Sua applicazione). La chiave `GLUONIFY_GDOWN_PASSWORD` arriva come variabile `APP_GLUONIFY_GDOWN_PASSWORD` e si legge `${app.gluonify.gdown.password}`. Richieda quello spazio con `"top": true` nella specifica dell'applicazione: solo lo spazio `<uuid>.app`, assegnato dal piano di controllo, aggiunge il prefisso `APP_`. Con un `"topNamespace"` letterale (per esempio `"gluonify-source"`, l'unica opzione del builder), le chiavi arrivano **senza prefisso**, come nominate nel vault (`GLUONIFY_GDOWN_PASSWORD` resta `GLUONIFY_GDOWN_PASSWORD`): le nomini secondo le proprietà lette (per esempio `SOURCE_GRAPH_PASSWORD`). **Mai** nel repository: il builder rifiuta una password in chiaro (regola R-SECRET), e `ConformityTest` Le segnala prima.
- **Registrare un valore non riavvia nulla**: dopo aver modificato più chiavi, avvii **una** ridistribuzione (`POST /apps/<nome>/redeploy`); le repliche si riavviano una alla volta, senza interruzioni se ne ha due o più.
- Variabili che la piattaforma aggiunge **sempre**: `QUARKUS_HTTP_PORT` e `QUARKUS_HTTP_HOST`, `GLUONIFY_ENVIRONMENT` (l'ambiente: SBX, QUA, PRD…), `GLUONIFY_REPLICA` (numero della replica: 1, 2…), `GLUONIFY_SELF_URL` (indirizzo di questa istanza). Sono lette e mostrate da [`PlatformResource`](src/main/java/io/gluonify/source/platform/PlatformResource.java) (`GET /api/platform`, mai un segreto).

### Salute: `/q/health/ready` e `/q/health/live`

La piattaforma invia traffico solo alle istanze per cui **`/q/health/ready`** risponde 200, e riavvia quelle per cui **`/q/health/live`** fallisce ripetutamente. `quarkus-smallrye-health` è **obbligatorio** (il builder lo controlla: regola R-SANTE). [`StoreHealth`](src/main/java/io/gluonify/source/notes/StoreHealth.java) rende l'istanza «pronta» solo se l'archiviazione risponde. Mantenga «vivo» indipendente dai servizi esterni: un guasto di Gdown non deve far riavviare l'applicazione in loop.

### Dati in Gdown (`source.store=gdown`)

**Il più semplice: un database dedicato.** Niente da dichiarare: `application.properties` legge `${GLUONIFY_GDOWN_DATABASE}`, `${GLUONIFY_GDOWN_USER}` e `${GLUONIFY_GDOWN_PASSWORD}`, quindi il builder **deduce** `"gdownDatabase": true` (campo `gdownDatabaseReason` del build; `"gdownDatabase": false` in `gluonify.json` lo disattiva), e `source.store=auto` passa a `gdown` appena viene fornito `GLUONIFY_GDOWN_DATABASE` (memoria altrimenti; `SOURCE_STORE` forza un valore): la piattaforma crea un database proprio per l'applicazione (il suo gruppo Raft, un account confinato) e fornisce `GLUONIFY_GDOWN_DATABASE`, `GLUONIFY_GDOWN_USER`, `GLUONIFY_GDOWN_PASSWORD` e l'accesso di rete (`GLUONIFY_SERVICE_GDOWN_URL`); non c'è altro da fare. Il database **non viene mai eliminato** con l'applicazione.

Un esempio pronto all'uso si trova in [`deploy/appspec-graph.json`](deploy/appspec-graph.json).

**A mano** (database condiviso, nomi propri):

1. Distribuisca con `"uses": ["gdown"]` (è anche ciò che **apre la rete** verso Gdown) e `SOURCE_STORE=gdown`.
2. Un amministratore crea il database e l'account dell'applicazione ([`deploy/gdown-setup.cypher`](deploy/gdown-setup.cypher): database `source`, ruolo confinato, account `notes`).
3. Metta `GLUONIFY_GDOWN_USER` (= `notes`) e `GLUONIFY_GDOWN_PASSWORD` nel vault.

L'API HTTP di Gdown: `POST <url>/db/<database>/query` con `{"statement": "…", "parameters": {…}}` e un'autenticazione di base; risposta `{"columns": […], "rows": [[…]], "stats": {…}}`. **Sempre parametri** (`$id`, `$title`), mai un valore incollato nell'istruzione (injection). Una **mappa** non è memorizzabile come proprietà di un nodo: memorizzi scalari, liste di scalari, oppure il JSON come testo. Gdown può anche pubblicare query Cypher con nome come API REST (contratti OpenAPI, token, limiti): vedere [gluonify.io](https://gluonify.io).

### File distribuiti (`source.store=files`)

Distribuisca con `"distributed": ["std"]`: `/distributed/std` compare, condiviso da tutte le repliche su tutti i nodi, con 2 copie sui nodi di archiviazione. **Regole** (spiegano la forma di [`FileNoteStore`](src/main/java/io/gluonify/source/notes/FileNoteStore.java)):

1. **Mai riscrivere un file né rinominare una cartella appena scritta**: scriva **nuovi** file con il loro nome definitivo (ecco perché una nota è immutabile); eliminare è consentito.
2. **L'elenco di una cartella può avere ~3 secondi di ritardo** rispetto a un'altra replica: una nota creata sulla replica A può impiegare un po' ad apparire su B.
3. Un file diventa visibile **alla chiusura**; tolleri comunque un file illeggibile.
4. Nessun lock tra nodi per impostazione predefinita (opzione `distributedLocks`).
5. **Il corpo di una richiesta HTTP si legge fino in fondo**, altrimenti la connessione riutilizzata si blocca.

### Ricevere i webhook di Photon

Photon riceve i messaggi dei Suoi partner (verifica la loro firma HMAC, converte XML, SOAP o form in JSON) poi li **consegna** al Suo servizio. Contratto di consegna:

- **un codice 2XX conferma**; qualsiasi altro codice (o un guasto) fa **ritentare** la consegna secondo la policy del webhook (`retry`), poi li mette nella coda di dead-letter;
- quindi **risponda in fretta** e sia **idempotente**: «almeno una volta» significa che uno stesso messaggio può arrivare due volte. L'header `X-Gluonify-Event-Id` è la chiave di deduplicazione; `X-Gluonify-Delivery-Attempt` conta i tentativi; `X-Gluonify-Webhook-Id` nomina il webhook. La memoria **non** è condivisa tra repliche: qui gli identificatori sono conservati nell'archiviazione scelta da `source.store` (memoria, un file per evento creato con `CREATE_NEW` in `/distributed/std/events`, oppure un nodo protetto da un vincolo di unicità in Gdown), così una sola replica accetta un evento.
- Photon **non** invia un token Charm: protegga il punto d'ingresso con una chiave che mette nella destinazione del webhook (`"headers": {"X-Api-Key": "…"}`, vedere [`deploy/photon-webhook.json`](deploy/photon-webhook.json)) e nel vault (`WEBHOOK_KEY`). **Senza chiave configurata, il ricevitore è chiuso** (404).

[`WebhookResource`](src/main/java/io/gluonify/source/platform/WebhookResource.java) mostra il tutto (chiave confrontata in tempo costante, deduplicazione, JSON illeggibile → 400); sostituisca il corpo di `receive` con la Sua elaborazione.

### Documenti (Crystallon)

**Crystallon** produce documenti PDF (fatture, preventivi, lettere; PDF/A-1B su richiesta) a partire da un **modello** disegnato nel suo editor grafico e conservato lì, con la sua cronologia, per ogni organizzazione. L'applicazione invia i suoi dati (il *payload*, un JSON) e riceve il PDF. È un componente **facoltativo** della piattaforma, quindi questo codice è anche l'esempio di un'integrazione facoltativa: [`DocumentsResource`](src/main/java/io/gluonify/source/documents/DocumentsResource.java) offre `POST /api/documents/invoice`, che riceve una fattura del dominio proprio dell'applicazione ([`InvoiceRequest`](src/main/java/io/gluonify/source/documents/InvoiceRequest.java)) e risponde `application/pdf`.

**1. La dipendenza.** Una dipendenza porta tutto il lato SDK: `gluonify-plasma-quarkus` (bean CDI predefiniti: si inietta un `CrystallonClient`; porta `gluonify-plasma-services`, che trova indirizzi e token, e il client di Crystallon). `gluonify-plasma-testing` (ambito test) fornisce i servizi simulati per i test. Entrambe sono dichiarate nel `pom.xml` (già fatto qui):

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

L'SDK è servito dal repository Maven di **gluonify-sea** (lettura pubblica: nessun account per risolverlo), aggiunto a Maven Central, non al suo posto; questo `pom.xml` scrive il suo indirizzo come proprietà `gluonify.sea.maven.url`, così che uno specchio o una copia locale possa sostituirlo (`mvn verify -Dgluonify.sea.maven.url=file:///…`). Il builder di Gluonify (Bup) dichiara da solo questo repository per i build che esegue. **Versione**: l'SDK è pubblicato nella versione 0.7.2 su gluonify-sea, con `gluonify-plasma-quarkus` e `gluonify-plasma-testing`; `gluonify.sdk.version` vale `0.7.2` in `pom.xml`, nulla da installare in locale. Dalla 0.7.1, ogni modulo client porta un marcatore (`META-INF/gluonify/services/crystallon` in `gluonify-plasma-crystallon-client`) da cui il builder deduce `uses`. Con Gradle: gli stessi due artefatti (`implementation` e `testImplementation`), senza altri repository oltre a Maven Central e gluonify-sea.

**2. Nulla da dichiarare.** Costruita da Bup, l'applicazione riceve `?crystallon` nei suoi `uses` senza scriverlo: Bup legge il marcatore di `gluonify-plasma-crystallon-client` nel classpath di esecuzione (SDK 0.7.1 e successivi) e aggiunge il servizio come facoltativo; [`gluonify.json`](gluonify.json) non lo menziona. Distribuita a mano (`PUT /apps`: nessun build, quindi nessuna deduzione), la specifica lo dichiara: `crystallon` negli `uses` di [`deploy/appspec.json`](deploy/appspec.json). In entrambi i casi la piattaforma **apre la rete** verso Crystallon, e solo verso di esso, e fornisce il suo indirizzo e il token proprio dell'applicazione. **In questo repository non ci sono proprietà, URL né token**: il produttore di `gluonify-plasma-quarkus` dà al codice un `CrystallonClient` (il mapper Jackson 3 dell'applicazione, l'indirizzo e il token trovati dall'SDK). [`Documents`](src/main/java/io/gluonify/source/documents/Documents.java) chiede soltanto se Crystallon è disponibile (`GluonifyServices.optional("crystallon")`) e inietta il client come `Instance<CrystallonClient>`. **Senza di esso la funzione è spenta e nient'altro cambia**: l'endpoint risponde 404 e dice perché, `GET /api/platform` indica `"documents": false`, l'interfaccia nasconde la sezione, l'applicazione si avvia e tutti gli altri test passano senza Crystallon. Il luogo in cui gira l'applicazione decide come l'SDK trova Crystallon:

| Dove | Indirizzo | Token |
|---|---|---|
| sulla piattaforma, `crystallon` in `uses` (dedotto da Bup, o dichiarato nella specifica) | fornito (`GLUONIFY_SERVICE_CRYSTALLON_URL`) | il token proprio dell'applicazione (file rinnovato dall'agente del nodo, riletto a ogni chiamata) |
| la Sua macchina, dopo `gluonify login <zona>` | l'indirizzo pubblico pubblicato dalla zona | il Suo token di sviluppatore, rinnovato da solo |
| test | il Crystallon simulato di `GluonifyTestServices.start()` | il token di test |
| altrove | nessuno: la funzione è spenta | |

**3. Il token e il ruolo.** Crystallon richiede un token Charm con audience `gluonify-crystallon`; il ruolo **`gluonify:crystallon:render`** basta per generare e validare (`convert` e `templates` non servono per produrre documenti). Per impostazione predefinita l'applicazione chiama Crystallon **con il proprio token** (token Charm, soggetto `app:<nome>`, audience `gluonify-crystallon`, ruolo `gluonify:crystallon:render`), che nessuno emette a mano: con `crystallon` in `uses` (dedotto o dichiarato), il piano di controllo (Higgs) lo fa emettere da Charm, e l'agente del nodo (Strange) lo scrive in `/run/gluonify-tokens/crystallon.token` (sola lettura nella sandbox dell'istanza) e lo rinnova prima della scadenza (900 s per impostazione predefinita). L'SDK rilegge quel file **a ogni chiamata**: un rinnovo non richiede alcun riavvio, e la chiamata funziona anche fuori da una richiesta (timer, webhook, coda di messaggi). Il token che invia a `/api/documents/invoice` deve solo aprire questa API (`source:read`). Da sapere:

- il token dell'applicazione porta l'organizzazione dell'applicazione (claim `org`), o l'organizzazione predefinita di Crystallon (`default`) quando non ne ha: il modello `Gluonify invoice` deve esistere lì, ed è lì che il builder lo installa (passo 4);
- quando Crystallon rifiuta il token dell'applicazione, la risposta è un 502 e non un 403: chi chiama non può risolvere, il deployment sì (costruire con Bup e l'SDK 0.7.1 o successivo, che lo deduce, oppure dichiarare `crystallon` negli `uses` della specifica);
- con `mvn quarkus:dev` questa API non chiede alcun token, ma Crystallon sì: esegua una volta `gluonify login gluonify.lan` (o la Sua zona), nel browser, e l'SDK usa il Suo token di sviluppatore e l'indirizzo pubblico di Crystallon: nessuna variabile da impostare.

**Inoltrare il token di chi chiama (facoltativo).** Se Crystallon deve vedere la **persona** che chiama l'applicazione (i suoi ruoli, la sua organizzazione) invece dell'applicazione, sostituisca il client predefinito con uno Suo: un bean dello stesso tipo prevale su quello predefinito. Il token inviato dalla persona deve allora portare, oltre all'audience e al ruolo di questa applicazione (`gluonify-source`, `source:read`), l'audience `gluonify-crystallon` e il ruolo `gluonify:crystallon:render` (Charm mette più audience in un token, al massimo 8; il prefisso `gluonify:` è riservato alla piattaforma, quindi lo emette l'amministratore), e una chiamata fatta fuori da una richiesta non ha alcun token da inoltrare. Non è il comportamento predefinito e questo repository non lo contiene:

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

**4. Il modello e il codice generato.** L'esempio chiama il modello di Crystallon chiamato **`Gluonify invoice`**, e **il modello vive in questo repository**: [`crystallon/templates/gluonify-invoice.json`](crystallon/templates/gluonify-invoice.json) è il modello esattamente come lo scrive il pulsante *Esporta* dell'editor di Crystallon (il modello predefinito `invoice`, con il suo `name` esatto e la sua `description` alla radice). **Niente da creare a mano**: quando il builder (Bup) distribuisce un'applicazione che usa Crystallon, installa ogni `crystallon/templates/*.json` nell'organizzazione in cui genera il token dell'applicazione (`PUT /v1/templates/by-name/{name}` di Crystallon): creato la prima volta, lasciato invariato se identico, salvato come nuova versione quando il contenuto cambia ma i dati attesi restano gli stessi, e **mai sovrascritto** quando attenderebbe altri dati (`differs`). Il build riporta ogni modello nel suo campo `templates` e nel suo log; un errore in questo passo è un avviso, mai una distribuzione fallita. Senza il builder (distribuzione a mano, `mvn quarkus:dev` contro un cluster), crei il modello nella console di Crystallon (`/crystallon/`; *Nuovo modello* chiamato esattamente `Gluonify invoice`), *Importi* il file e salvi. **La console resta il luogo in cui lo si modifica**: cambi lì il modello, lo *Esporti*, sostituisca il file e faccia commit. Crystallon **genera** allora **il codice Java** di un modello: record che descrivono esattamente i dati che legge, e una classe che lo chiama. Apra il modello e la sua scheda **Codice** (chiede prima di salvare: il codice è generato dalla versione salvata), imposti *Pacchetto Java* su `io.gluonify.source.documents.generated` e *Nome della classe* su `GluonifyInvoice`, lasci deselezionato *Blocca questa versione* (il codice genera allora la versione corrente del modello), poi *Scarica (zip)* o *Copia*. I tre file di [`documents/generated/`](src/main/java/io/gluonify/source/documents/generated) sono esattamente questo risultato (è stato aggiunto solo un commento d'intestazione), generato dalla versione 0 del modello (quella che il builder crea dal file):

- `GluonifyInvoicePayload`: un record per oggetto dei dati (`Sender`, `Recipient`, `Invoice`, `Item`, `Payment`); il Javadoc di ogni componente dice se è obbligatorio;
- `GluonifyInvoiceDocument`: la facciata, con `render(payload)`, `render(payload, RenderOptions)` e `validate(payload)`; chiama il modello **per nome** (lo stesso in ogni ambiente) e porta le costanti `TEMPLATE_NAME`, `TEMPLATE_VERSION` e `SCHEMA_SHA256`;
- `package-info`.

**Per rigenerarli** (il modello è cambiato e ora attende altri dati: il suo `SCHEMA_SHA256` non è più quello della facciata), generi di nuovo nella scheda Codice con gli stessi due campi e sostituisca i tre file; non li modifichi mai a mano. Il compilatore mostra allora cosa adattare in [`InvoiceMapping`](src/main/java/io/gluonify/source/documents/InvoiceMapping.java), l'unica classe che conosce la forma dei dati. Senza la console, lo stesso codice viene dall'API (ruolo `gluonify:crystallon:templates`, non `render`): `GET /v1/templates/<id>/code?lang=java&package=io.gluonify.source.documents.generated&name=GluonifyInvoice`. Faccia commit insieme del modello esportato e del codice rigenerato, perché descrivano sempre gli stessi dati. Un modello già presente in Crystallon che attende altri dati viene lasciato com'è dal builder (`differs`): lo modifichi nella console, o lo cancelli perché la distribuzione successiva lo ricrei.

**Stato.** Verificato il 2026-10-11 sul cluster di sviluppo: l'applicazione (SDK 0.7.1) ha generato la fattura 2026-0042 tramite il vero Crystallon con il proprio token (200, `application/pdf`, PDF/A-1B, una pagina), con un modello creato a mano nella console. **Non ancora** verificato da un build reale: l'installazione automatica del modello da parte del builder (coperta da test contro un finto Crystallon in gluonify-bup e gluonify-crystallon).

**Immagine nativa.** L'SDK dichiara per la reflection i propri record; **quelli dei dati sono affare dell'applicazione**. Il record radice E ogni record annidato di ogni payload generato figurano in [`DocumentsReflection`](src/main/java/io/gluonify/source/documents/DocumentsReflection.java) (`@RegisterForReflection`). Dimenticarne uno rompe solo l'eseguibile nativo, alla prima chiamata: `DocumentsReflectionTest` lo verifica.

**5. Il punto d'ingresso.**

```bash
curl -s -X POST localhost:8080/api/documents/invoice -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -o invoice.pdf -d '{
  "number": "2026-0042", "subject": "Website relaunch", "lang": "en",
  "customer": {"name": "Example Customer SA", "street": "Rue du Lac", "houseNumber": "7", "postalCode": "1003", "city": "Lausanne", "country": "CH"},
  "lines": [{"description": "Consulting, 12 hours", "quantity": 12, "unit": "h", "unitPrice": 160}]}'
```

`source:read` lo apre. `date` (oggi), `vatRate` (8,1 %) e `unit` (`pce`) hanno valori predefiniti; l'importo di ogni riga è calcolato dall'applicazione; `lang` (`en`, `fr`, `es`, `it`, `de`) è facoltativo, la valuta, il paese e il fuso orario sono quelli del modello (CHF, CH, Europe/Zurich). La risposta è il PDF (`200`, `application/pdf`, PDF/A-1B, `Content-Disposition: attachment` e gli header `X-Crystallon-Pages` e `X-Crystallon-Warnings`). Gli errori hanno il corpo abituale `{"error": "…"}`, più `code` e `path` (il punto del problema nel modello) quando Crystallon li fornisce ([`DocumentErrors`](src/main/java/io/gluonify/source/documents/DocumentErrors.java)):

| Situazione | Risposta |
|---|---|
| richiesta non valida (numero vuoto, nessuna riga, `lang` sconosciuto…) | 400; Crystallon non viene chiamato |
| nessun token, o senza `source:read` | 401 / 403 |
| Crystallon rifiuta il token dell'applicazione (ruolo `gluonify:crystallon:render` mancante, scaduto, `crystallon` assente da `uses` al deployment) | 502 (chi chiama non può risolvere, il deployment sì) |
| il modello non può generare questi dati (Crystallon 400 / 422) | 422, con `code` e `path` |
| nessun modello con questo nome per l'organizzazione del token (Crystallon 404), Crystallon in errore (5XX), risposta inattesa, irraggiungibile, token dell'applicazione non ottenibile | 502 |
| Crystallon troppo lento (30 s) | 504 |
| Crystallon non disponibile | 404 |

**Nell'interfaccia**: un pulsante *Scarica una fattura di esempio* nella sezione *Documenti* (mostrata solo quando `GET /api/platform` indica `"documents": true`) invia il token della persona che ha effettuato l'accesso (deve solo aprire questa API) e una fattura di esempio. [`DocumentsResourceTest`](src/test/java/io/gluonify/source/documents/DocumentsResourceTest.java) verifica tutto contro il Crystallon simulato dell'SDK (`GluonifyTestServices.start()` e `close()` in [`GluonifyTestServicesResource`](src/test/java/io/gluonify/source/documents/GluonifyTestServicesResource.java), nulla da configurare): il percorso chiamato (`/v1/templates/by-name/Gluonify%20invoice/render`), la chiamata fuori da una richiesta con il token dell'applicazione, il payload e ogni errore sopra.

### Chiamare un'altra applicazione

La dichiari in `"uses"`: la piattaforma fornisce `GLUONIFY_SERVICE_<APP>_URL` **e apre la rete** verso di essa (senza `uses`, l'applicazione non la raggiunge). Non scriva mai l'indirizzo nel codice: legga la variabile (vedere `ping` in [`PlatformResource`](src/main/java/io/gluonify/source/platform/PlatformResource.java)).

### Siti statici e domini

- **Field** serve un sito statico (ZIP) sul Suo dominio; niente da programmare in questo servizio.
- **Il Suo dominio**: `PUT /domains/<nome>` sull'API di Higgs (vedere [`deploy/domain.json`](deploy/domain.json)) instrada un percorso verso l'applicazione e applica delle **policy**: token Charm (audience, ruoli), limiti di frequenza, CORS, liste di indirizzi. Il certificato HTTPS viene ottenuto automaticamente.

## 8. Compilare

```bash
mvn test                       # test Java (70)
mvn package                    # JVM: target/quarkus-app/; compila anche l'interfaccia (Quinoa)
```

**L'eseguibile nativo** (ciò che Gluonify esegue: un file, senza JVM):

```bash
docker build --target out --output type=local,dest=dist -f Dockerfile.build .
./dist/gluonify-source         # gira su questo Linux, porta 8080
```

Il file `dist/gluonify-source` è un eseguibile Linux **glibc** dell'architettura del Suo Docker (arm64 su Apple Silicon, amd64 su x86_64). Per l'altra architettura: `docker build --platform linux/amd64 …` (lento: emulazione; vedere i commenti di `Dockerfile.build`).

Senza Docker: `mvn package -Dnative` richiede GraalVM (NIK 25) installato.

**Le norme della piattaforma**: dopo il clone, il builder di Gluonify controlla il progetto (`R-SANTE`, `R-SECRET`, `R-FICHIER-SENSIBLE`, `R-NATIF`, `R-IMAGE`, `R-ECOUTE`) e rifiuta un repository che non le rispetta. [`ConformityTest`](src/test/java/io/gluonify/source/ConformityTest.java) le verifica **in locale**; lo conservi.

## 9. Distribuire

Ci sono due strade.

**A. Gluonify compila per Lei** (la più semplice): dia l'indirizzo Git al builder (**gluonify-bup**):

```bash
curl -X POST "$BUP_URL/v1/builds" -H 'Content-Type: application/json' -H "X-Git-Token: $GIT_TOKEN" \
  -d '{"name":"gluonify-source","gitUrl":"https://github.com/VOUS/VOTRE-DEPOT.git","ref":"main","deploy":true,"replicas":2,"memoryMb":128,"topNamespace":"gluonify-source"}'
```

Il builder clona, controlla la conformità, compila in nativo, pubblica l'eseguibile e lo distribuisce. Lo segua: `GET /v1/builds/<id>` e `/logs`. (Un push Git può anche avviare il build tramite un webhook: `POST /v1/webhooks/git`.) Nota: il builder conosce solo `topNamespace`, quindi con esso le chiavi del vault arrivano senza prefisso.

**I servizi che il builder dichiara.** Il builder invia `uses` e `gdownDatabase` a `PUT /apps/<nome>`. `uses` è **dedotto al build**: ogni modulo client dell'SDK porta un marcatore `META-INF/gluonify/services/<servizio>`; Bup legge i marcatori dei jar del classpath di esecuzione (solo il gruppo `io.gluonify.plasma`) e li aggiunge come servizi facoltativi (qui `?crystallon`, da `gluonify-plasma-crystallon-client`). Li unisce ai servizi dichiarati nella richiesta di build oppure, se la richiesta non li ha, nel [`gluonify.json`](gluonify.json) alla radice del repository (la richiesta prevale; un elenco vuoto nella richiesta significa «nessun servizio dichiarato», quelli dedotti si aggiungono comunque). **Dichiari in `uses` solo ciò che l'SDK non rileva**: una chiamata HTTP diretta a un'altra applicazione, Charm per OIDC, Gdown via HTTP. Il file di questo repository è quindi `{"uses": ["charm", "gdown"]}`: con `?crystallon` dedotto, gli stessi servizi di [`deploy/appspec.json`](deploy/appspec.json), cosa che `DeploySpecsTest` verifica. Senza il file, un build distribuirebbe l'applicazione senza rete verso Charm e Gdown, perché `PUT /apps/<nome>` **sostituisce l'intera specifica**. Non serve aggiungere `"gdownDatabase": true`: il builder lo deduce dai riferimenti a `GLUONIFY_GDOWN_DATABASE|USER|PASSWORD` in `application.properties` (`GET /v1/builds/{id}/effective-spec` mostra il `gluonify.json` effettivo); un `?` iniziale (`"?orders"`) segna un servizio facoltativo. Un'applicazione che raggiunge servizi solo tramite l'SDK non ha bisogno di `gluonify.json`.

**B. Pubblica Lei stesso l'eseguibile**, poi lo distribuisca:

```bash
SHA=$(shasum -a 256 dist/gluonify-source | cut -d' ' -f1)
# pubblichi dist/gluonify-source a un indirizzo HTTPS, poi:
curl -X PUT "https://api.$ZONE/apps/gluonify-source" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d @deploy/appspec.json     # dopo aver inserito il Suo indirizzo e la Sua impronta in artifacts
```

L'applicazione è quindi su `https://gluonify-source.<zone>` (certificato automatico). I campi di [`deploy/appspec.json`](deploy/appspec.json):

| Campo | Ruolo |
|---|---|
| `artifacts` | l'eseguibile per architettura: `URL#sha256=…` (l'impronta è **obbligatoria**, verificata dal nodo) |
| `replicas` | numero di istanze (2 o più: aggiornamenti senza interruzioni) |
| `memoryMb` | limite di memoria per istanza (un nativo sta in 64-128 MB) |
| `uses` | servizi raggiungibili, per nome di applicazione (`charm`, `gdown`, `crystallon`…): `GLUONIFY_SERVICE_<APP>_URL` fornita, rete aperta; per `crystallon`, anche il token dell'applicazione (`GLUONIFY_CRYSTALLON_TOKEN_FILE`). Con il builder: dedotti dai moduli dell'SDK, più ciò che dichiarano `gluonify.json` o la richiesta di build |
| `distributed` | `["std"]` per `/distributed/std` |
| `env` | variabili **non segrete**; `${NAME}` e `${NAME:-default}` vengono risolte |
| `top` | `true`: il piano di controllo assegna all'applicazione il proprio spazio del vault `<uuid>.app`, le cui chiavi arrivano come `APP_<CHIAVE>` |
| `gdownDatabase` | `true`: un database Gdown dedicato per l'applicazione (gruppo Raft proprio, account confinato); `GLUONIFY_GDOWN_DATABASE`, `GLUONIFY_GDOWN_USER`, `GLUONIFY_GDOWN_PASSWORD` e `GLUONIFY_SERVICE_GDOWN_URL` sono forniti; mai eliminato con l'applicazione (vedere [`deploy/appspec-graph.json`](deploy/appspec-graph.json)) |
| `topNamespace` | alternativa: uno spazio del vault esistente, per nome; le sue chiavi arrivano **senza prefisso** (niente `APP_`) |
| `internal` | `true`: nessuna rotta pubblica (servizio interno) |

Aggiornare: stesso `PUT` (o nuovo build); le repliche vengono sostituite **una alla volta**. Riavviare senza modifiche: `POST /apps/<nome>/redeploy`. Log: `GET /apps/<nome>/logs`. Metriche: `/q/metrics`.

## 10. Testare

```bash
mvn test                                 # 70 test Java
cd src/main/webui && npm install && npm test   # 40 test di interfaccia
```

| Test | Numero | Cosa verifica |
|---|---|---|
| `NotesResourceTest` | 7 | 401 senza token, 403 senza il ruolo giusto, ciclo di vita completo, validazione 400, OpenAPI, salute, metriche |
| `AuthConfigResourceTest` / `AuthConfigConfiguredTest` | 2 + 1 | `GET /api/auth-config` è pubblico e vuoto senza client; gli indirizzi pubblici di Charm sono dedotti dall'emittente, mai dall'indirizzo interno `GLUONIFY_SERVICE_CHARM_URL` |
| `FileNoteStoreTest` | 6 | file nuovi mai rinominati, ordine, due repliche sulla stessa cartella, file illeggibili ignorati, identificatori mai percorsi |
| `GraphNoteStoreTest` | 4 | contro un falso Gdown: percorso, autenticazione, istruzione **parametrizzata**, lettura delle righe, errore |
| `NoteStoresTest` | 3 | scelta dell'archiviazione: `auto` dà `gdown` quando viene fornito `GLUONIFY_GDOWN_DATABASE`, `memory` altrimenti; un valore esplicito prevale |
| `WebhookResourceTest` / `WebhookClosedTest` | 4 + 1 | chiave, conferma, **idempotenza**, rifiuti; chiuso senza chiave |
| `PlatformResourceTest` | 4 | variabili della piattaforma lette, **nessun segreto restituito**, chiamata da servizio a servizio limitata ai servizi dichiarati |
| `ConformityTest` | 4 | le norme del builder, in locale |
| `EventLedgerTest` | 4 | registri di idempotenza: un solo vincitore per evento (memoria, file con due repliche, vincolo di unicità del grafo, errore del grafo diverso da un duplicato propagato) |
| `DocumentsResourceTest` | 13 | contro il **Crystallon simulato dell'SDK** (`GluonifyTestServices`): PDF restituito, percorso chiamato (`/v1/templates/by-name/Gluonify%20invoice/render`), **token dell'applicazione** (anche fuori da una richiesta), payload inviato, corrispondenza dei 422 / 502, validazione propria (400), ruoli, OpenAPI |
| `DocumentsDisabledTest` | 3 | Crystallon non disponibile: 404 con il motivo, `"documents": false`, il resto non è toccato |
| `InvoiceMappingTest` / `DocumentErrorsTest` | 3 + 7 | la Sua fattura → il payload del modello (importi, valori predefiniti, nome di file sicuro); ogni errore di Crystallon → uno stato |
| `DocumentsReflectionTest` | 1 | salvaguardia nativa: ogni record dei dati generati è dichiarato per la reflection |
| `CrystallonTemplateFileTest` | 1 | il modello Crystallon del repository (`crystallon/templates/gluonify-invoice.json`): JSON valido, il nome esatto che chiama il codice generato, gli stessi campi di primo livello dei dati generati (l'impronta `SCHEMA_SHA256` è calcolata solo da Crystallon) |
| `DeploySpecsTest` | 2 | le specifiche di `deploy/` usano i nomi attuali (`uses`, `SOURCE_STORE`); `gluonify.json` (letto dal builder) dichiara solo ciò che l'SDK non rileva (`charm`, `gdown`) e, con `crystallon` dedotto (marcatore nel classpath), dà gli stessi `uses` di `deploy/appspec.json` |

Un test d'API si copia da `NotesResourceTest`; un test di archiviazione da `FileNoteStoreTest` (senza avviare Quarkus: veloce).

## 11. Insidie note

- **Nativo**: **non** crei `HttpClient`, `Random` né `SecureRandom` in un campo `static` (lo stato verrebbe congelato alla compilazione, non all'esecuzione): li crei al primo utilizzo (vedere `GraphNoteStore.client()`). Legga il JSON di terzi come albero (`JsonNode`) piuttosto che in classi; un tipo (de)serializzato da Jackson fuori da una firma REST deve portare `@RegisterForReflection`. **Un bug che compare solo in nativo non si vede in `mvn test`**: lanci l'eseguibile nativo prima di consegnare.
- **Jackson 3**: il package è `tools.jackson.databind`, non `com.fasterxml.jackson.databind`.
- **Nessun indirizzo, nessuna porta, nessuna password nel codice**: tutto viene dall'ambiente.
- **File distribuiti**: vedere le regole del [§7](#file-distribuiti-sourcestorefiles) (mai riscrivere, ~3 s di ritardo).
- **Webhook**: rispondere in fretta con 2XX, essere idempotenti, proteggere con una chiave.
- **Codice generato**: i file di `documents/generated/` vengono dalla scheda *Codice* di Crystallon: li rigeneri, non li modifichi mai a mano; e dichiari ogni record di un payload generato in `DocumentsReflection` (un errore che compare solo in nativo).
- **Un'identità di sviluppo non ha nulla da fare in produzione**: mantenga `@IfBuildProfile("dev")`.
- **Quinoa e gli indirizzi d'API**: aggiunga ogni nuovo prefisso d'API a `quarkus.quinoa.ignored-path-prefixes`.
- **Sotto emulazione x86_64** (Docker su Apple Silicon): compili con `--build-arg MAVEN_OPTS=-Djdk.lang.Process.launchMechanism=VFORK`.

## 12. Risoluzione dei problemi

| Sintomo | Causa probabile |
|---|---|
| 401 in produzione | nessun token, token scaduto, o **audience** diversa da `GLUONIFY_SOURCE_OIDC_AUDIENCE`; oppure `GLUONIFY_SOURCE_OIDC_ISSUER` non corrisponde all'`iss` del token |
| 403 | token valido ma senza il ruolo (`source:read` / `source:write`) |
| Nessun pulsante *Accedi*, la pagina nomina `GLUONIFY_SOURCE_OIDC_CLIENT_ID` | manca l'identificativo del client, oppure l'indirizzo pubblico di Charm è sconosciuto (`GLUONIFY_SOURCE_OIDC_ISSUER` lasciato a `any` e nessun `GLUONIFY_SOURCE_CHARM_PUBLIC_URL`) |
| Charm rifiuta l'accesso (`redirect_uri`, `invalid_target`) | il reindirizzamento del client non è esattamente l'indirizzo dell'interfaccia (barra finale compresa), oppure le sue audience non contengono `gluonify-source` |
| Accesso effettuato, ma 403 sulle note | l'account non ha `source:read` / `source:write` (assegnare i ruoli in Charm, poi uscire e accedere di nuovo: i ruoli sono letti all'accesso) |
| L'applicazione non riceve traffico | `/q/health/ready` non risponde 200 (archiviazione irraggiungibile?): `GET /apps/<nome>` mostra le istanze pronte |
| `store=gdown`: «source.graph.url is empty» | l'applicazione non è distribuita con `"uses": ["gdown"]` |
| Gdown: 401 o «Unsupported property value type» | account errato; oppure una **mappa** memorizzata come proprietà (vedere §7) |
| Una nota creata non compare subito (file) | cache dell'elenco di ~3 s tra repliche: aggiornare |
| Il build del repository viene rifiutato | una regola `R-…`: il messaggio la nomina; `mvn test` (`ConformityTest`) la mostra in locale |
| `mvn package` non trova Node | accesso alla rete per scaricarlo, oppure `-Dquarkus.quinoa=false` per compilare senza l'interfaccia |
| Il nativo va in crash all'avvio ma non in JVM | uno stato congelato alla compilazione (`static`) o una reflection mancante (§11) |
| `POST /api/documents/invoice`: 404 «documents are off» | Crystallon non è disponibile: l'applicazione non è distribuita con `"uses": ["crystallon"]` (tramite il builder: SDK precedente alla 0.7.1, o deduzione disattivata) o, sulla Sua macchina, manca `gluonify login <zona>` |
| Documenti: 502 «did not accept the token of the application» | l'applicazione non è distribuita con `"uses": ["crystallon"]`, la piattaforma non emette ancora token di applicazione, oppure il file del token manca, è vuoto o scaduto; sulla Sua macchina, esegua di nuovo `gluonify login <zona>` |
| Documenti: 502 «does not know the template» | nessun modello chiamato `Gluonify invoice` nell'organizzazione del token (token dell'applicazione: l'organizzazione dell'applicazione, o quella predefinita di Crystallon): legga `templates` nel build del builder (`GET /v1/builds/<id>`: `failed` con il motivo; assente quando l'applicazione non usa Crystallon o il file manca); senza il builder, importi `crystallon/templates/gluonify-invoice.json` nella console di Crystallon |
| Builder: modello `Gluonify invoice` segnalato `differs` | il modello di Crystallon attende altri dati rispetto al file: non è stato scritto nulla; lo modifichi nella console (o lo cancelli), lo esporti, rigeneri il codice e faccia commit di entrambi |
| Documenti: 422 con `code` e `path` | il modello non può generare questi dati: `path` è il punto del problema nel modello |
| I documenti funzionano su JVM ma falliscono alla prima chiamata nell'eseguibile nativo | manca un record dei dati generati in `DocumentsReflection` |

---

*Gluonify è una piattaforma minimalista per applicazioni Quarkus native: [gluonify.cloud](https://gluonify.cloud) (presentazione) e [gluonify.io](https://gluonify.io) (tecnico). Questo repository è volutamente piccolo: si legge in un'ora.*

## Licenza

Copyright (c) 2026 Gluonify.

Questo repository è pubblicato sotto la [licenza Apache, versione 2.0](LICENSE). Potete usarlo, modificarlo e
ridistribuirlo, anche a fini commerciali, alle condizioni di tale licenza; è fornito «così com'è», senza
garanzia. I componenti di terze parti restano sotto le proprie licenze (vedere `NOTICE` se presente).
