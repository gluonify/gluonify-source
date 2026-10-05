# gluonify-source

[English](README.md) · [Français](README.fr.md) · [Español](README.es.md) · **Italiano** · [Deutsch](README.de.md)

**Il punto di partenza di un servizio Quarkus 4 su [Gluonify](https://gluonify.cloud).** Un piccolo servizio completo (delle «note») che mostra, con codice funzionante e test, come:

1. creare un **servizio REST** (validazione, ruoli, OpenAPI);
2. creare un'**interfaccia web** con [Quinoa](https://quarkus.io/extensions/io.quarkiverse.quinoa/quarkus-quinoa/) (Vue 3, servita dallo stesso eseguibile);
3. **interfacciarsi con i servizi di Gluonify**: configurazione e segreti (Top), token di identità (Charm), dati (Gdown) o file distribuiti, altre applicazioni, webhook ricevuti da Photon, siti statici (Field), domini e policy (Higgs);
4. **compilarlo come eseguibile nativo** e **distribuirlo**.

È pensato per uno sviluppatore o per **un assistente IA** che parte da zero con un servizio: ogni scelta è spiegata qui, ogni riga di configurazione è commentata nel codice, e [`CLAUDE.md`](CLAUDE.md) riassume le regole per un'IA.

> Gluonify esegue **applicazioni Quarkus native** (un file eseguibile, senza JVM) su macchine Debian. Voi date accesso al vostro codice su Git; la piattaforma lo compila, lo distribuisce, lo mette in sicurezza e lo fa girare.

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
11. [Trappole note](#11-trappole-note)
12. [Risoluzione dei problemi](#12-risoluzione-dei-problemi)

---

## 1. Iniziare in 5 minuti

**Prerequisiti**: Java 25 (`JAVA_HOME`), Maven 3.9+. Node.js non è necessario: Quinoa scarica il proprio (v24.3.0) alla prima compilazione. Docker serve solo per l'eseguibile nativo.

```bash
git clone https://github.com/gluonify/gluonify-source.git
cd gluonify-source
mvn quarkus:dev
```

Aprite <http://localhost:8080>: l'interfaccia (aggiungere, elencare, eliminare note). Inoltre:

| Indirizzo | Contenuto |
|---|---|
| `/` | l'interfaccia Vue |
| `/api/notes` | l'API REST (JSON) |
| `/q/swagger-ui` | la documentazione interattiva dell'API |
| `/q/openapi` | il contratto OpenAPI (JSON o YAML) |
| `/q/health/ready` · `/q/health/live` | salute: «pronto» e «vivo» (vedere [§7](#7-interfacciarsi-con-i-servizi-di-gluonify)) |
| `/q/metrics` | metriche Prometheus |

In **sviluppo** non viene richiesto alcun token: un'identità «dev» (con tutti i ruoli) esiste solo in questo profilo (`@IfBuildProfile("dev")`, assente dall'eseguibile di produzione). Le note sono **in memoria** (`source.store=memory`): scompaiono allo spegnimento.

```bash
curl -s localhost:8080/api/notes -H 'Content-Type: application/json' -d '{"title":"Courses","body":"du pain"}'
curl -s localhost:8080/api/notes
```

## 2. La mappa del progetto

```
pom.xml                         progetto Maven AUTONOMO (Quarkus 4.0.0.Beta1, Java 25): nessun parent Gluonify
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
    NoteStores.java             sceglie l'implementazione (source.store): il punto dove collegare la vostra
    StoreHealth.java            la condizione «pronto» (/q/health/ready)
  platform/
    PlatformResource.java       variabili fornite dalla piattaforma; chiamata a un'altra applicazione
    WebhookResource.java        ricevere le consegne di Photon
  security/DevAuthentication.java   identità di sviluppo (solo profilo dev)
src/main/webui/                 l'interfaccia Vue 3 (Vite + vitest), compilata da Quinoa
src/test/java/…                 30 test Java; src/main/webui/src/App.test.js: 3 test di interfaccia
deploy/                         esempi: AppSpec, dominio, webhook Photon, database Gdown
scripts/rename.py               rinomina il progetto
```

## 3. Rinominare il progetto

Il vostro servizio non si chiama `gluonify-source`. Un comando cambia le coordinate Maven, il package Java (cartelle comprese), il nome dell'eseguibile, il titolo OpenAPI e l'**audience** attesa dei token:

```bash
python3 scripts/rename.py com.acme shop-api com.acme.shop
mvn test
```

(`groupId` `com.acme`, `artifactId` `shop-api`, package Java `com.acme.shop`. Lo script può essere rilanciato senza rischi.) Il nome dell'artefatto diventa anche il nome dell'applicazione su Gluonify; sceglietelo come un nome DNS: minuscole, cifre, trattini.

## 4. Creare un servizio REST

Leggete [`NotesResource.java`](src/main/java/io/gluonify/source/notes/NotesResource.java): è il modello. Per aggiungere **la vostra** risorsa, ad esempio degli ordini:

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
4. **I test**: copiate [`NotesResourceTest`](src/test/java/io/gluonify/source/NotesResourceTest.java). `@TestSecurity(user = "ada", roles = {"shop:write"})` fissa l'identità di un test; senza annotazione, è una chiamata senza token (atteso: 401).
5. **I codici HTTP**: 201 + `Location` alla creazione, 204 senza corpo, 400 per un corpo non valido (Hibernate Validator), 401 senza token, 403 senza il ruolo giusto, 404 per un identificatore sconosciuto. Gli errori restituiscono `{"error": "…"}`.

**L'archiviazione** sta dietro l'interfaccia [`NoteStore`](src/main/java/io/gluonify/source/notes/NoteStore.java): la risorsa non sa dove vivono i dati. Per la vostra archiviazione (un altro database, un'altra cartella), implementate l'interfaccia e aggiungete un caso in [`NoteStores`](src/main/java/io/gluonify/source/notes/NoteStores.java).

## 5. Creare un'interfaccia con Quinoa

[Quinoa](https://quarkus.io/extensions/io.quarkiverse.quinoa/quarkus-quinoa/) compila un progetto web (qui Vue 3 + Vite, in `src/main/webui`) e lo serve **dallo stesso eseguibile** dell'API: un solo file da distribuire, una sola origine (niente CORS).

- **Sviluppare**: `mvn quarkus:dev` avvia anche il server Vite (porta 5173, hot reload dell'interfaccia); Quarkus serve l'API. Aprite <http://localhost:8080>.
- **Chiamare l'API**: indirizzi relativi (`fetch('/api/notes')`), vedere [`src/api.js`](src/main/webui/src/api.js). Il token Charm viene incollato dall'utente e conservato per la scheda (`sessionStorage`).
- **Le rotte dell'interfaccia**: `quarkus.quinoa.enable-spa-routing=true` restituisce `index.html` per un indirizzo sconosciuto (utile con vue-router). Ma **un indirizzo d'API sconosciuto non deve restituire l'interfaccia**: `quarkus.quinoa.ignored-path-prefixes=/api,/hooks,/q` (già impostato). Se aggiungete un prefisso d'API, aggiungetelo qui.
- **Compilare**: `mvn package` esegue `npm install` poi `npm run build`; `dist/` viene incorporato nell'eseguibile. Senza interfaccia: `-Dquarkus.quinoa=false`.
- **Testare l'interfaccia**: `cd src/main/webui && npm install && npm test` (vitest + jsdom, `fetch` simulato: vedere `App.test.js`). Con `quarkus.quinoa.run-tests=true`, anche `mvn package` li esegue.
- **Cache**: i file di `dist/assets` hanno un'impronta nel nome (immutabili); `index.html` viene servito con `Cache-Control: no-cache` (impostazione `ui-entry`).

Un'altra tecnologia (React, Svelte…): sostituite il contenuto di `src/main/webui`; Quinoa richiede solo un `package.json` con gli script `dev` e `build` e una cartella di output (`quarkus.quinoa.build-dir`).

## 6. Autenticazione: i token di Charm

L'API è **chiusa senza token**. Si aspetta un token bearer (JWT) emesso da **gluonify-charm**, il provider di identità di Gluonify (OpenID Connect, ES256):

```
Authorization: Bearer <token>
```

Il servizio non **crea** mai token: ne **verifica** (modalità `quarkus.oidc.application-type=service`). Cosa controlla:

| Controllo | Impostazione | Valore |
|---|---|---|
| Firma | chiavi di Charm, lette da `${SERVICE_ID_URL}/realms/gluonify` | `SERVICE_ID_URL` è fornita dalla piattaforma se l'applicazione è distribuita con `"uses": ["id"]` |
| Emittente (`iss`) | `quarkus.oidc.token.issuer` | `OIDC_ISSUER` = `https://id.<la vostra zona>/realms/gluonify` (stabile: non è l'indirizzo dell'istanza di Charm) |
| Audience (`aud`) | `quarkus.oidc.token.audience` | `OIDC_AUDIENCE`, per impostazione predefinita il nome del progetto (`gluonify-source`) |
| Scadenza | automatica | token di breve durata |
| Ruoli | claim `roles` | `source:read` (leggere), `source:write` (scrivere): vedere `@RolesAllowed` |

**Ottenere un token di test** (l'amministratore della piattaforma, con il token di amministrazione di Charm):

```bash
curl -s -X POST "$CHARM_URL/v1/tokens" -H "Authorization: Bearer $ID_ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"sub":"ada","audience":"gluonify-source","ttlSeconds":3600,"roles":["source:read","source:write"]}'
# -> {"token": "eyJ…"}
curl -s https://gluonify-source.<zone>/api/notes -H "Authorization: Bearer eyJ…"
```

Un valore di audience diverso (`"audience": "autre"`) restituisce **401**; un token valido senza il ruolo giusto restituisce **403**.

**In locale**, il profilo `dev` fornisce l'identità «dev» (vedere [`DevAuthentication`](src/main/java/io/gluonify/source/security/DevAuthentication.java)); **nei test**, `@TestSecurity`. Mai un'identità di questo tipo in produzione: viene compilata solo nel profilo `dev`.

## 7. Interfacciarsi con i servizi di Gluonify

Un'applicazione su Gluonify è **isolata**: un proprio account, una propria rete, un file system ridotto. Raggiunge solo ciò che **dichiara** (`"uses"`) e riceve la propria configurazione **dall'ambiente**. Ecco ogni servizio, con il punto del codice.

| Servizio | Cosa offre | Come lo usa l'applicazione | In questo repository |
|---|---|---|---|
| **Top** (vault) | configurazione e segreti per applicazione | le chiavi dello spazio `app` della vostra applicazione arrivano come `APP_<CHIAVE>`; `${app.graph.password}` in `application.properties` | `source.graph.password`, `source.webhook.key` |
| **Charm** (identità) | token JWT di breve durata | `"uses": ["id"]` → `SERVICE_ID_URL`; `quarkus-oidc` verifica i token | [§6](#6-autenticazione-i-token-di-charm) |
| **Gdown** (database a grafo) | database replicato (Raft), Cypher via HTTP | `"uses": ["graphdb"]` → `SERVICE_GRAPHDB_URL`; un account locale di Gdown | [`GraphNoteStore`](src/main/java/io/gluonify/source/notes/GraphNoteStore.java) |
| **File distribuiti** | `/distributed/std`: durevole, condiviso da tutte le repliche, 2 copie | `"distributed": ["std"]` alla distribuzione | [`FileNoteStore`](src/main/java/io/gluonify/source/notes/FileNoteStore.java) |
| **Altre applicazioni** | chiamata da servizio a servizio | `"uses": ["autre"]` → `SERVICE_AUTRE_URL` | [`PlatformResource.ping`](src/main/java/io/gluonify/source/platform/PlatformResource.java) |
| **Photon** (gateway API) | webhook firmati, conversione XML / SOAP / form → JSON, riproduzione | Photon consegna a `…/hooks/events`; il vostro servizio conferma con 2XX | [`WebhookResource`](src/main/java/io/gluonify/source/platform/WebhookResource.java) |
| **Field** | siti statici (ZIP) serviti sul vostro dominio | niente da programmare: un sito si carica come ZIP | — |
| **Higgs** (orchestratore) | domini, certificati HTTPS automatici, rotte, policy (token, limiti, CORS, indirizzi) | `PUT /domains/<nome>` | `deploy/domain.json` |

### Configurazione e segreti (Top)

- Un valore **non segreto**: in `"env"` della distribuzione (`deploy/appspec.json`): `"SOURCE_STORE": "files"`. Viene memorizzato nello stato del cluster.
- Un valore **segreto** (password, chiave d'API): nel **vault** (Top, spazio `<uuid>.app` della vostra applicazione). La chiave `GRAPH_PASSWORD` arriva come variabile `APP_GRAPH_PASSWORD` e si legge `${app.graph.password}`. Dichiaratela con `"vaultNamespace"` alla distribuzione. **Mai** nel repository: il builder rifiuta una password in chiaro (regola R-SECRET), e `ConformityTest` ve lo segnala prima.
- **Registrare un valore non riavvia nulla**: dopo aver modificato più chiavi, scatenate **una** ridistribuzione (`POST /apps/<nome>/redeploy`); le repliche si riavviano una alla volta, senza interruzioni se ne avete due o più.
- Variabili che la piattaforma aggiunge **sempre**: `QUARKUS_HTTP_PORT` e `QUARKUS_HTTP_HOST`, `ENV_NAME` (l'ambiente: SBX, QUA, PRD…), `ENV_NODE` (numero della replica: 1, 2…), `GLUONIFY_SELF_URL` (indirizzo di questa istanza). Sono lette e mostrate da [`PlatformResource`](src/main/java/io/gluonify/source/platform/PlatformResource.java) (`GET /api/platform`, mai un segreto).

### Salute: `/q/health/ready` e `/q/health/live`

La piattaforma invia traffico solo alle istanze per cui **`/q/health/ready`** risponde 200, e riavvia quelle per cui **`/q/health/live`** fallisce ripetutamente. `quarkus-smallrye-health` è **obbligatorio** (il builder lo controlla: regola R-SANTE). [`StoreHealth`](src/main/java/io/gluonify/source/notes/StoreHealth.java) rende l'istanza «pronta» solo se l'archiviazione risponde. Mantenete «vivo» indipendente dai servizi esterni: un guasto di Gdown non deve far riavviare l'applicazione in loop.

### Dati in Gdown (`source.store=graph`)

1. Distribuite con `"uses": ["graphdb"]` (è anche ciò che **apre la rete** verso Gdown) e `SOURCE_STORE=graph`.
2. Un amministratore crea il database e l'account dell'applicazione ([`deploy/gdown-setup.cypher`](deploy/gdown-setup.cypher): database `source`, ruolo confinato, account `notes`).
3. Mettete `GRAPH_USER` (= `notes`) e `GRAPH_PASSWORD` nel vault.

L'API HTTP di Gdown: `POST <url>/db/<database>/query` con `{"statement": "…", "parameters": {…}}` e un'autenticazione di base; risposta `{"columns": […], "rows": [[…]], "stats": {…}}`. **Sempre parametri** (`$id`, `$title`), mai un valore incollato nell'istruzione (injection). Una **mappa** non è memorizzabile come proprietà di un nodo: memorizzate scalari, liste di scalari, oppure il JSON come testo. Gdown può anche pubblicare query Cypher con nome come API REST (contratti OpenAPI, token, limiti): vedere [gluonify.io](https://gluonify.io).

### File distribuiti (`source.store=files`)

Distribuite con `"distributed": ["std"]`: `/distributed/std` compare, condiviso da tutte le repliche su tutti i nodi, con 2 copie sui nodi di archiviazione. **Regole** (spiegano la forma di [`FileNoteStore`](src/main/java/io/gluonify/source/notes/FileNoteStore.java)):

1. **Mai riscrivere un file né rinominare una cartella appena scritta**: scrivete **nuovi** file con il loro nome definitivo (ecco perché una nota è immutabile); eliminare è consentito.
2. **L'elenco di una cartella può avere ~3 secondi di ritardo** rispetto a un'altra replica: una nota creata sulla replica A può impiegare un po' ad apparire su B.
3. Un file diventa visibile **alla chiusura**; tollerate comunque un file illeggibile.
4. Nessun lock tra nodi per impostazione predefinita (opzione `distributedLocks`).
5. **Il corpo di una richiesta HTTP si legge fino in fondo**, altrimenti la connessione riutilizzata si blocca.

### Ricevere i webhook di Photon

Photon riceve i messaggi dei vostri partner (verifica la loro firma HMAC, converte XML, SOAP o form in JSON) poi li **consegna** al vostro servizio. Contratto di consegna:

- **un codice 2XX conferma**; qualsiasi altro codice (o un guasto) fa **riprovare** secondo la policy del webhook (`retry`), poi li mette in coda morta;
- quindi **rispondete in fretta** ed siate **idempotenti**: «almeno una volta» significa che uno stesso messaggio può arrivare due volte. L'header `X-Gluonify-Event-Id` è la chiave di deduplicazione; `X-Gluonify-Delivery-Attempt` conta i tentativi; `X-Gluonify-Webhook-Id` nomina il webhook;
- Photon **non** invia un token Charm: proteggete il punto d'ingresso con una chiave che mettete nella destinazione del webhook (`"headers": {"X-Api-Key": "…"}`, vedere [`deploy/photon-webhook.json`](deploy/photon-webhook.json)) e nel vault (`WEBHOOK_KEY`). **Senza chiave configurata, il ricevitore è chiuso** (404).

[`WebhookResource`](src/main/java/io/gluonify/source/platform/WebhookResource.java) mostra il tutto (chiave confrontata in tempo costante, deduplicazione, JSON illeggibile → 400); sostituite il corpo di `receive` con la vostra elaborazione.

### Chiamare un'altra applicazione

Dichiaratela in `"uses"`: la piattaforma fornisce `SERVICE_<APP>_URL` **e apre la rete** verso di essa (senza `uses`, l'applicazione non la raggiunge). Non scrivete mai l'indirizzo nel codice: leggete la variabile (vedere `ping` in [`PlatformResource`](src/main/java/io/gluonify/source/platform/PlatformResource.java)).

### Siti statici e domini

- **Field** serve un sito statico (ZIP) sul vostro dominio; niente da programmare in questo servizio.
- **Il vostro dominio**: `PUT /domains/<nome>` sull'API di Higgs (vedere [`deploy/domain.json`](deploy/domain.json)) instrada un percorso verso l'applicazione e applica delle **policy**: token Charm (audience, ruoli), limiti di frequenza, CORS, liste di indirizzi. Il certificato HTTPS viene ottenuto automaticamente.

## 8. Compilare

```bash
mvn test                       # test Java (30)
mvn package                    # JVM: target/quarkus-app/ ; compila anche l'interfaccia (Quinoa)
```

**L'eseguibile nativo** (ciò che Gluonify esegue: un file, senza JVM):

```bash
docker build --target out --output type=local,dest=dist -f Dockerfile.build .
./dist/gluonify-source         # gira su questo Linux, porta 8080
```

Il file `dist/gluonify-source` è un eseguibile Linux **glibc** dell'architettura del vostro Docker (arm64 su Apple Silicon, amd64 su x86_64). Per l'altra architettura: `docker build --platform linux/amd64 …` (lento: emulazione; vedere i commenti di `Dockerfile.build`).

Senza Docker: `mvn package -Dnative` richiede GraalVM (NIK 25) installato.

**Le norme della piattaforma**: dopo il clone, il builder di Gluonify controlla il progetto (`R-SANTE`, `R-SECRET`, `R-FICHIER-SENSIBLE`, `R-NATIF`, `R-IMAGE`, `R-ECOUTE`) e rifiuta un repository che non le rispetta. [`ConformityTest`](src/test/java/io/gluonify/source/ConformityTest.java) le verifica **da voi**; conservatelo.

## 9. Distribuire

Ci sono due strade.

**A. Gluonify compila per voi** (la più semplice): date l'indirizzo Git al builder (**gluonify-bup**):

```bash
curl -X POST "$BUP_URL/v1/builds" -H 'Content-Type: application/json' -H "X-Git-Token: $GIT_TOKEN" \
  -d '{"name":"gluonify-source","gitUrl":"https://github.com/VOUS/VOTRE-DEPOT.git","ref":"main","deploy":true,"replicas":2,"memoryMb":128,"vaultNamespace":"gluonify-source"}'
```

Il builder clona, controlla la conformità, compila in nativo, pubblica l'eseguibile e lo distribuisce. Seguitelo: `GET /v1/builds/<id>` e `/logs`. (Un push Git può anche avviare il build tramite un webhook: `POST /v1/webhooks/git`.)

**B. Pubblicate voi stessi l'eseguibile**, poi distribuite:

```bash
SHA=$(shasum -a 256 dist/gluonify-source | cut -d' ' -f1)
# pubblicate dist/gluonify-source a un indirizzo HTTPS, poi:
curl -X PUT "https://api.$ZONE/apps/gluonify-source" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d @deploy/appspec.json     # dopo aver inserito il vostro indirizzo e la vostra impronta in artifacts
```

L'applicazione è quindi su `https://gluonify-source.<zone>` (certificato automatico). I campi di [`deploy/appspec.json`](deploy/appspec.json):

| Campo | Ruolo |
|---|---|
| `artifacts` | l'eseguibile per architettura: `URL#sha256=…` (l'impronta è **obbligatoria**, verificata dal nodo) |
| `replicas` | numero di istanze (2 o più: aggiornamenti senza interruzioni) |
| `memoryMb` | limite di memoria per istanza (un nativo sta in 64-128 MB) |
| `uses` | servizi raggiungibili: `SERVICE_<APP>_URL` fornita, rete aperta (`id` = Charm, `graphdb` = Gdown) |
| `distributed` | `["std"]` per `/distributed/std` |
| `env` | variabili **non segrete**; `${NOM}` e `${NOM:-défaut}` vengono risolte |
| `vaultNamespace` | lo spazio del vault le cui chiavi arrivano come `APP_<CHIAVE>` |
| `internal` | `true`: nessuna rotta pubblica (servizio interno) |

Aggiornare: stesso `PUT` (o nuovo build); le repliche vengono sostituite **una alla volta**. Riavviare senza modifiche: `POST /apps/<nome>/redeploy`. Log: `GET /apps/<nome>/logs`. Metriche: `/q/metrics`.

## 10. Testare

```bash
mvn test                                 # 30 test Java
cd src/main/webui && npm install && npm test   # 3 test di interfaccia
```

| Test | Cosa verifica |
|---|---|
| `NotesResourceTest` | 401 senza token, 403 senza il ruolo giusto, ciclo di vita completo, validazione 400, OpenAPI, salute, metriche |
| `FileNoteStoreTest` | file nuovi mai rinominati, ordine, due repliche sulla stessa cartella, file illeggibili ignorati, identificatori mai percorsi |
| `GraphNoteStoreTest` | contro un falso Gdown: percorso, autenticazione, istruzione **parametrizzata**, lettura delle righe, errore |
| `WebhookResourceTest` / `WebhookClosedTest` | chiave, conferma, **idempotenza**, rifiuti; chiuso senza chiave |
| `PlatformResourceTest` | variabili della piattaforma lette, **nessun segreto restituito**, chiamata da servizio a servizio limitata ai servizi dichiarati |
| `ConformityTest` | le norme del builder, da voi |

Un test d'API si copia da `NotesResourceTest`; un test di archiviazione da `FileNoteStoreTest` (senza avviare Quarkus: veloce).

## 11. Trappole note

- **Nativo**: **non** create `HttpClient`, `Random` né `SecureRandom` in un campo `static` (lo stato verrebbe congelato alla compilazione, non all'esecuzione): createli al primo utilizzo (vedere `GraphNoteStore.client()`). Leggete il JSON di terzi come albero (`JsonNode`) piuttosto che in classi; un tipo (de)serializzato da Jackson fuori da una firma REST deve portare `@RegisterForReflection`. **Un bug che compare solo in nativo non si vede in `mvn test`**: lanciate l'eseguibile nativo prima di consegnare.
- **Jackson 3**: il package è `tools.jackson.databind`, non `com.fasterxml.jackson.databind`.
- **Nessun indirizzo, nessuna porta, nessuna password nel codice**: tutto viene dall'ambiente.
- **File distribuiti**: vedere le regole del [§7](#file-distribuiti-sourcestorefiles) (mai riscrivere, ~3 s di ritardo).
- **Webhook**: rispondere in fretta con 2XX, essere idempotenti, proteggere con una chiave.
- **Un'identità di sviluppo non ha nulla da fare in produzione**: mantenete `@IfBuildProfile("dev")`.
- **Quinoa e gli indirizzi d'API**: aggiungete ogni nuovo prefisso d'API a `quarkus.quinoa.ignored-path-prefixes`.
- **Sotto emulazione x86_64** (Docker su Apple Silicon): compilate con `--build-arg MAVEN_OPTS=-Djdk.lang.Process.launchMechanism=VFORK`.

## 12. Risoluzione dei problemi

| Sintomo | Causa probabile |
|---|---|
| 401 in produzione | nessun token, token scaduto, o **audience** diversa da `OIDC_AUDIENCE`; oppure `OIDC_ISSUER` non corrisponde all'`iss` del token |
| 403 | token valido ma senza il ruolo (`source:read` / `source:write`) |
| L'applicazione non riceve traffico | `/q/health/ready` non risponde 200 (archiviazione irraggiungibile?): `GET /apps/<nome>` mostra le istanze pronte |
| `store=graph`: «source.graph.url est vide» | l'applicazione non è distribuita con `"uses": ["graphdb"]` |
| Gdown: 401 o «Unsupported property value type» | account errato; oppure una **mappa** memorizzata come proprietà (vedere §7) |
| Una nota creata non compare subito (file) | cache dell'elenco di ~3 s tra repliche: aggiornare |
| Il build del repository viene rifiutato | una regola `R-…`: il messaggio la nomina; `mvn test` (`ConformityTest`) la mostra da voi |
| `mvn package` non trova Node | accesso alla rete per scaricarlo, oppure `-Dquarkus.quinoa=false` per compilare senza l'interfaccia |
| Il nativo si blocca all'avvio ma non in JVM | uno stato congelato alla compilazione (`static`) o una reflection mancante (§11) |

---

*Gluonify è una piattaforma minimalista per applicazioni Quarkus native: [gluonify.cloud](https://gluonify.cloud) (presentazione) e [gluonify.io](https://gluonify.io) (tecnico). Questo repository è volutamente piccolo: si legge in un'ora.*
