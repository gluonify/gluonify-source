# gluonify-source

[English](README.md) · [Français](README.fr.md) · [Español](README.es.md) · [Italiano](README.it.md) · **Deutsch**

> **Gradle-Variante.** Derselbe Dienst, gebaut mit **Gradle** (Kotlin DSL) anstelle von Maven, existiert in [`gluonify-source-gradle`](https://github.com/gluonify/gluonify-source-gradle): derselbe Code, dieselben Tests, dieselben Plattformregeln.

**Der Ausgangspunkt für einen Quarkus-4-Dienst auf [Gluonify](https://gluonify.cloud).** Ein kleiner, vollständiger Dienst (»Notizen«), der mit lauffähigem Code und Tests zeigt, wie man:

1. einen **REST-Dienst** baut (Validierung, Rollen, OpenAPI);
2. eine **Weboberfläche** mit [Quinoa](https://quarkus.io/extensions/io.quarkiverse.quinoa/quarkus-quinoa/) baut (Vue 3, ausgeliefert von derselben ausführbaren Datei);
3. sich **an die Dienste von Gluonify anbindet**: Konfiguration und Geheimnisse (Top), Identitäts-Tokens (Charm), Daten (Gdown) oder verteilte Dateien, andere Anwendungen, von Photon empfangene Webhooks, statische Sites (Field), Domains und Richtlinien (Higgs);
4. ihn als **native ausführbare Datei baut** und **deployt**.

Er richtet sich an Entwickler oder an **einen KI-Assistenten**, die einen Dienst von Grund auf neu beginnen: Jede Entscheidung wird hier erklärt, jede Konfigurationszeile ist im Code kommentiert, und [`CLAUDE.md`](CLAUDE.md) fasst die Regeln für eine KI zusammen.

> Gluonify führt **native Quarkus-Anwendungen** (eine ausführbare Datei, ohne JVM) auf Debian-Maschinen aus. Sie geben Zugriff auf Ihren Code in Git; die Plattform baut, deployt, sichert und betreibt ihn.

## Inhaltsverzeichnis
1. [In 5 Minuten starten](#1-in-5-minuten-starten)
2. [Die Projektübersicht](#2-die-projektübersicht)
3. [Das Projekt umbenennen](#3-das-projekt-umbenennen)
4. [Einen REST-Dienst bauen](#4-einen-rest-dienst-bauen)
5. [Eine Oberfläche mit Quinoa bauen](#5-eine-oberfläche-mit-quinoa-bauen)
6. [Authentifizierung: die Tokens von Charm](#6-authentifizierung-die-tokens-von-charm)
7. [Anbindung an die Gluonify-Dienste](#7-anbindung-an-die-gluonify-dienste)
8. [Bauen](#8-bauen)
9. [Deployen](#9-deployen)
10. [Testen](#10-testen)
11. [Bekannte Stolperfallen](#11-bekannte-stolperfallen)
12. [Fehlerbehebung](#12-fehlerbehebung)

---

## 1. In 5 Minuten starten

**Voraussetzungen**: Java 25 (`JAVA_HOME`), Maven 3.9+. Node.js ist nicht nötig: Quinoa lädt seine eigene Version (v24.3.0) beim ersten Build herunter. Docker wird nur für die native ausführbare Datei gebraucht.

```bash
git clone https://github.com/gluonify/gluonify-source.git
cd gluonify-source
mvn quarkus:dev
```

Öffnen Sie <http://localhost:8080>: die Oberfläche (Notizen hinzufügen, auflisten, löschen). Außerdem:

| Adresse | Inhalt |
|---|---|
| `/` | die Vue-Oberfläche |
| `/api/notes` | die REST-API (JSON) |
| `/q/swagger-ui` | die interaktive Dokumentation der API |
| `/q/openapi` | der OpenAPI-Vertrag (JSON oder YAML) |
| `/q/health/ready` · `/q/health/live` | Gesundheit: »bereit« und »lebendig« (siehe [§7](#7-anbindung-an-die-gluonify-dienste)) |
| `/q/metrics` | Prometheus-Metriken |

In der **Entwicklung** wird kein Token verlangt: Eine Identität »dev« (mit allen Rollen) existiert nur in diesem Profil (`@IfBuildProfile("dev")`, in der ausführbaren Produktionsdatei nicht vorhanden). Die Notizen liegen **im Speicher** (`source.store=memory`): Sie verschwinden beim Beenden.

```bash
curl -s localhost:8080/api/notes -H 'Content-Type: application/json' -d '{"title":"Courses","body":"du pain"}'
curl -s localhost:8080/api/notes
```

## 2. Die Projektübersicht

```
pom.xml                         EIGENSTÄNDIGES Maven-Projekt (Quarkus 4.0.0.Beta1, Java 25): kein Gluonify-Parent
Dockerfile.build                baut die native ausführbare Datei in Docker
src/main/resources/
  application.properties        die GESAMTE Konfiguration, Zeile für Zeile kommentiert
src/main/java/io/gluonify/source/
  SourceConfig.java             die Konfiguration der Anwendung (Präfix »source.«)
  notes/
    Note.java, NewNote.java     das Modell (Records) und der (validierte) Body zum Anlegen
    NotesResource.java          ← DAS MODELL EINER REST-RESSOURCE (zum Kopieren)
    NoteStore.java              das Speicher-Interface; drei Implementierungen:
    MemoryNoteStore.java          im Speicher (Entwicklung)
    FileNoteStore.java            Dateien in /distributed/std (dauerhaft, von den Replikaten geteilt)
    GraphNoteStore.java           Gdown, die Graphdatenbank der Plattform
    NoteStores.java             wählt die Implementierung (source.store): hier schließen Sie Ihre eigene an
    StoreHealth.java            die Bedingung »bereit« (/q/health/ready)
  platform/
    PlatformResource.java       von der Plattform bereitgestellte Variablen; Aufruf einer anderen Anwendung
    WebhookResource.java        Zustellungen von Photon empfangen
  security/DevAuthentication.java   Entwicklungsidentität (nur Profil dev)
src/main/webui/                 die Vue-3-Oberfläche (Vite + vitest), von Quinoa gebaut
src/test/java/…                 34 Java-Tests; src/main/webui/src/App.test.js: 15 Oberflächentests
deploy/                         Beispiele: AppSpec, Domain, Photon-Webhook, Gdown-Datenbank
scripts/rename.py               benennt das Projekt um
```

## 3. Das Projekt umbenennen

Ihr Dienst heißt nicht `gluonify-source`. Ein Befehl ändert die Maven-Koordinaten, das Java-Paket (samt Ordnern), den Namen der ausführbaren Datei, den OpenAPI-Titel und die erwartete **Audience** der Tokens:

```bash
python3 scripts/rename.py com.acme shop-api com.acme.shop
mvn test
```

(`groupId` `com.acme`, `artifactId` `shop-api`, Java-Paket `com.acme.shop`. Das Skript kann gefahrlos erneut ausgeführt werden.) Der Name des Artefakts wird auch zum Namen der Anwendung auf Gluonify; wählen Sie ihn wie einen DNS-Namen: Kleinbuchstaben, Ziffern, Bindestriche.

## 4. Einen REST-Dienst bauen

Lesen Sie [`NotesResource.java`](src/main/java/io/gluonify/source/notes/NotesResource.java): Das ist das Modell. Um **Ihre** Ressource hinzuzufügen, zum Beispiel Bestellungen:

1. **Das Modell**: ein Java-`record` (unveränderlich, Jackson liest und schreibt es, OpenAPI beschreibt es).
   ```java
   public record Order(String id, String customer, int quantity) {}
   public record NewOrder(@NotBlank String customer, @Min(1) int quantity) {}   // validiert
   ```
2. **Die Ressource**: eine Klasse mit `@Path`, Methoden `@GET`/`@POST` …, Rollen mit `@RolesAllowed`, Validierung mit `@Valid`.
   ```java
   @Path("/api/orders")
   @Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
   public class OrdersResource {
       @POST @RolesAllowed("shop:write")
       public Response create(@Valid NewOrder in) { … return Response.created(uri).entity(order).build(); }
   }
   ```
3. **Die Dokumentation**: `@Operation(summary = "…")` und `@Tag(name = "…")` (microprofile-openapi): Sie erscheinen in `/q/swagger-ui`. Der Vertrag wird bei jedem Start neu erzeugt.
4. **Die Tests**: Kopieren Sie [`NotesResourceTest`](src/test/java/io/gluonify/source/NotesResourceTest.java). `@TestSecurity(user = "ada", roles = {"shop:write"})` legt die Identität eines Tests fest; ohne Annotation ist es ein Aufruf ohne Token (erwartet: 401).
5. **Die HTTP-Codes**: 201 + `Location` beim Anlegen, 204 ohne Body, 400 bei ungültigem Body (Hibernate Validator), 401 ohne Token, 403 ohne die passende Rolle, 404 bei unbekannter Kennung. Fehler liefern `{"error": "…"}`.

**Der Speicher** liegt hinter dem Interface [`NoteStore`](src/main/java/io/gluonify/source/notes/NoteStore.java): Die Ressource weiß nicht, wo die Daten liegen. Für Ihren eigenen Speicher (eine andere Datenbank, ein anderer Ordner) implementieren Sie das Interface und fügen in [`NoteStores`](src/main/java/io/gluonify/source/notes/NoteStores.java) einen Fall hinzu.

## 5. Eine Oberfläche mit Quinoa bauen

[Quinoa](https://quarkus.io/extensions/io.quarkiverse.quinoa/quarkus-quinoa/) baut ein Webprojekt (hier Vue 3 + Vite, in `src/main/webui`) und liefert es **über dieselbe ausführbare Datei** wie die API aus: eine einzige Datei zu deployen, ein einziger Origin (kein CORS).

- **Entwickeln**: `mvn quarkus:dev` startet auch den Vite-Server (Port 5173, Hot-Reload der Oberfläche); Quarkus liefert die API. Öffnen Sie <http://localhost:8080>.
- **Die API aufrufen**: relative Adressen (`fetch('/api/notes')`), siehe [`src/api.js`](src/main/webui/src/api.js). Das Charm-Token wird vom Benutzer eingefügt und für den Tab aufbewahrt (`sessionStorage`).
- **Die Routen der Oberfläche**: `quarkus.quinoa.enable-spa-routing=true` liefert für eine unbekannte Adresse `index.html` zurück (nützlich mit vue-router). Aber **eine unbekannte API-Adresse darf nicht die Oberfläche zurückgeben**: `quarkus.quinoa.ignored-path-prefixes=/api,/hooks,/q` (bereits gesetzt). Wenn Sie ein API-Präfix hinzufügen, tragen Sie es hier ein.
- **Bauen**: `mvn package` führt `npm install` und dann `npm run build` aus; `dist/` wird in die ausführbare Datei eingebettet. Ohne Oberfläche: `-Dquarkus.quinoa=false`.
- **Sprachen (i18n)**: Die Oberfläche gibt es auf Englisch (Standard), Französisch, Spanisch, Italienisch und Deutsch. In den Komponenten steht kein Text: Jeder Text ist ein Schlüssel in [`src/i18n/`](src/main/webui/src/i18n) (`en.js`, `fr.js`, `es.js`, `it.js`, `de.js`), gelesen mit `t('Schlüssel', { Parameter })` (ein kleines Hilfsmittel in `index.js`, ohne Abhängigkeit). Die Sprachauswahl in der Kopfzeile merkt sich die Wahl (`localStorage`); ohne Wahl wird die Browsersprache verwendet, wenn sie eine der fünf ist, sonst Englisch; `<html lang>` folgt. Um eine Sprache hinzuzufügen: Kopieren Sie `en.js` nach `<Code>.js` und übersetzen Sie die Werte (gleiche Schlüssel), importieren Sie die Datei dann in `index.js` (`MESSAGES` und `LANGUAGES`). Vom API zurückgegebene Fehlermeldungen werden unverändert angezeigt; die zu den Codes 401 und 403 sind übersetzt.
- **Die Oberfläche testen**: `cd src/main/webui && npm install && npm test` (vitest + jsdom, simuliertes `fetch`: siehe `App.test.js`). Mit `quarkus.quinoa.run-tests=true` startet `mvn package` sie ebenfalls.
- **Caching**: Die Dateien in `dist/assets` tragen einen Fingerabdruck im Namen (unveränderlich); `index.html` wird mit `Cache-Control: no-cache` ausgeliefert (Einstellung `ui-entry`).

Eine andere Technologie (React, Svelte …): Ersetzen Sie den Inhalt von `src/main/webui`; Quinoa verlangt nur eine `package.json` mit den Skripten `dev` und `build` sowie einen Ausgabeordner (`quarkus.quinoa.build-dir`).

## 6. Authentifizierung: die Tokens von Charm

Die API ist **ohne Token geschlossen**. Sie erwartet ein Bearer-Token (JWT), ausgestellt von **gluonify-charm**, dem Identitätsanbieter von Gluonify (OpenID Connect, ES256):

```
Authorization: Bearer <token>
```

Der Dienst **erzeugt** nie ein Token: Er **prüft** welche (Modus `quarkus.oidc.application-type=service`). Was er kontrolliert:

| Kontrolle | Einstellung | Wert |
|---|---|---|
| Signatur | Schlüssel von Charm, gelesen unter `${GLUONIFY_SERVICE_CHARM_URL}/realms/gluonify` | `GLUONIFY_SERVICE_CHARM_URL` wird von der Plattform bereitgestellt, wenn die Anwendung mit `"uses": ["charm"]` deployt wird |
| Aussteller (`iss`) | `quarkus.oidc.token.issuer` | `GLUONIFY_ZZZNONE_OIDC_ISSUER` = `https://id.<Ihre Zone>/realms/gluonify` (stabil: nicht die Adresse der Charm-Instanz) |
| Audience (`aud`) | `quarkus.oidc.token.audience` | `GLUONIFY_ZZZNONE_OIDC_AUDIENCE`, standardmäßig der Projektname (`gluonify-source`) |
| Ablauf | automatisch | kurzlebige Tokens |
| Rollen | Claim `roles` | `source:read` (lesen), `source:write` (schreiben): siehe `@RolesAllowed` |

**Ein Test-Token erhalten** (der Plattformadministrator, mit dem Administrations-Token von Charm):

```bash
curl -s -X POST "$CHARM_URL/v1/tokens" -H "Authorization: Bearer $GLUONIFY_CHARM_ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"sub":"ada","audience":"gluonify-source","ttlSeconds":3600,"roles":["source:read","source:write"]}'
# -> {"token": "eyJ…"}
curl -s https://gluonify-source.<zone>/api/notes -H "Authorization: Bearer eyJ…"
```

Ein abweichender Audience-Wert (`"audience": "other"`) ergibt **401**; ein gültiges Token ohne die passende Rolle ergibt **403**.

**Lokal** stellt das Profil `dev` die Identität »dev« bereit (siehe [`DevAuthentication`](src/main/java/io/gluonify/source/security/DevAuthentication.java)); **in den Tests** `@TestSecurity`. Niemals eine solche Identität in der Produktion: Sie wird nur im Profil `dev` kompiliert.

## 7. Anbindung an die Gluonify-Dienste

Eine Anwendung auf Gluonify ist **isoliert**: eigenes Konto, eigenes Netzwerk, ein reduziertes Dateisystem. Sie erreicht nur, was sie **deklariert** (`"uses"`), und erhält ihre Konfiguration **über die Umgebung**. Hier jeder Dienst, mit der Stelle im Code.

| Dienst | Was er bietet | Wie die Anwendung ihn nutzt | In diesem Repository |
|---|---|---|---|
| **Top** (Tresor) | Konfiguration und Geheimnisse pro Anwendung | mit `"top": true` kommen die Schlüssel des eigenen Namensraums `<uuid>.app` Ihrer Anwendung als `APP_<SCHLÜSSEL>` an; `${app.gluonify.gdown.password}` in `application.properties` | `source.graph.password`, `source.webhook.key` |
| **Charm** (Identität) | kurzlebige JWT-Tokens | `"uses": ["charm"]` → `GLUONIFY_SERVICE_CHARM_URL`; `quarkus-oidc` prüft die Tokens | [§6](#6-authentifizierung-die-tokens-von-charm) |
| **Gdown** (Graphdatenbank) | replizierte Datenbank (Raft), Cypher über HTTP | `"uses": ["gdown"]` → `GLUONIFY_SERVICE_GDOWN_URL`; ein lokales Gdown-Konto | [`GraphNoteStore`](src/main/java/io/gluonify/source/notes/GraphNoteStore.java) |
| **Verteilte Dateien** | `/distributed/std`: dauerhaft, von allen Replikaten geteilt, 2 Kopien | `"distributed": ["std"]` beim Deployment | [`FileNoteStore`](src/main/java/io/gluonify/source/notes/FileNoteStore.java) |
| **Andere Anwendungen** | Dienst-zu-Dienst-Aufruf | `"uses": ["other"]` → `SERVICE_OTHER_URL` | [`PlatformResource.ping`](src/main/java/io/gluonify/source/platform/PlatformResource.java) |
| **Photon** (API-Gateway) | signierte Webhooks, Umwandlung XML / SOAP / Formular → JSON, Wiederholung | Photon liefert an `…/hooks/events`; Ihr Dienst bestätigt mit 2XX | [`WebhookResource`](src/main/java/io/gluonify/source/platform/WebhookResource.java) |
| **Field** | statische Sites (ZIP), auf Ihrer Domain ausgeliefert | nichts zu programmieren: Eine Site wird als ZIP hochgeladen | — |
| **Higgs** (Orchestrator) | Domains, automatische HTTPS-Zertifikate, Routen, Richtlinien (Token, Limits, CORS, Adressen) | `PUT /domains/<name>` | `deploy/domain.json` |

### Konfiguration und Geheimnisse (Top)

- Ein **nicht geheimer** Wert: in `"env"` des Deployments (`deploy/appspec.json`): `"SOURCE_STORE": "files"`. Er wird im Zustand des Clusters gespeichert.
- Ein **geheimer** Wert (Passwort, API-Schlüssel): im **Tresor** (Top, Namensraum `<uuid>.app` Ihrer Anwendung). Der Schlüssel `GLUONIFY_GDOWN_PASSWORD` kommt als Variable `APP_GLUONIFY_GDOWN_PASSWORD` an und wird mit `${app.gluonify.gdown.password}` gelesen. Fordern Sie diesen Namensraum mit `"top": true` in der Anwendungsspezifikation an: Nur dieser von der Steuerungsebene zugewiesene Namensraum `<uuid>.app` fügt das Präfix `APP_` hinzu. Mit einem literalen `"topNamespace"` (zum Beispiel `"gluonify-source"`, die einzige Option des Builders) kommen die Schlüssel **ohne Präfix** an, so wie sie im Tresor heißen (`GLUONIFY_GDOWN_PASSWORD` bleibt `GLUONIFY_GDOWN_PASSWORD`): Benennen Sie sie nach den gelesenen Eigenschaften (zum Beispiel `SOURCE_GRAPH_PASSWORD`). **Niemals** im Repository: Der Builder lehnt ein Klartext-Passwort ab (Regel R-SECRET), und `ConformityTest` sagt es Ihnen vorher.
- **Einen Wert zu speichern startet nichts neu**: Lösen Sie nach der Änderung mehrerer Schlüssel **ein** Redeployment aus (`POST /apps/<name>/redeploy`); die Replikate starten nacheinander neu, ohne Unterbrechung, wenn Sie zwei oder mehr haben.
- Variablen, die die Plattform **immer** hinzufügt: `QUARKUS_HTTP_PORT` und `QUARKUS_HTTP_HOST`, `GLUONIFY_ENVIRONMENT` (die Umgebung: SBX, QUA, PRD …), `GLUONIFY_REPLICA` (Rang des Replikats: 1, 2 …), `GLUONIFY_SELF_URL` (Adresse dieser Instanz). Sie werden von [`PlatformResource`](src/main/java/io/gluonify/source/platform/PlatformResource.java) gelesen und angezeigt (`GET /api/platform`, niemals ein Geheimnis).

### Gesundheit: `/q/health/ready` und `/q/health/live`

Die Plattform sendet Verkehr nur an Instanzen, deren **`/q/health/ready`** mit 200 antwortet, und startet jene neu, deren **`/q/health/live`** wiederholt fehlschlägt. `quarkus-smallrye-health` ist **obligatorisch** (der Builder kontrolliert es: Regel R-SANTE). [`StoreHealth`](src/main/java/io/gluonify/source/notes/StoreHealth.java) macht die Instanz nur »bereit«, wenn der Speicher antwortet. Halten Sie »lebendig« unabhängig von externen Diensten: Ein Ausfall von Gdown darf die Anwendung nicht in eine Neustartschleife schicken.

### Daten in Gdown (`source.store=gdown`)

**Am einfachsten: eine dedizierte Datenbank.** Deployen Sie mit `"gdownDatabase": true` und `SOURCE_STORE=gdown`: Die Plattform legt eine eigene Datenbank für die Anwendung an (eigene Raft-Gruppe, eingeschränktes Konto) und liefert `GLUONIFY_GDOWN_DATABASE`, `GLUONIFY_GDOWN_USER`, `GLUONIFY_GDOWN_PASSWORD` und den Netzwerkzugang (`GLUONIFY_SERVICE_GDOWN_URL`); mehr ist nicht zu tun. Die Datenbank wird mit der Anwendung **nie gelöscht**.

Ein einsatzbereites Beispiel steht in [`deploy/appspec-graph.json`](deploy/appspec-graph.json).

**Von Hand** (gemeinsame Datenbank, eigene Namen):

1. Deployen Sie mit `"uses": ["gdown"]` (das **öffnet** auch das Netzwerk zu Gdown) und `SOURCE_STORE=gdown`.
2. Ein Administrator legt die Datenbank und das Konto der Anwendung an ([`deploy/gdown-setup.cypher`](deploy/gdown-setup.cypher): Datenbank `source`, eingeschränkte Rolle, Konto `notes`).
3. Legen Sie `GLUONIFY_GDOWN_USER` (= `notes`) und `GLUONIFY_GDOWN_PASSWORD` im Tresor ab.

Die HTTP-API von Gdown: `POST <url>/db/<datenbank>/query` mit `{"statement": "…", "parameters": {…}}` und Basic-Authentifizierung; Antwort `{"columns": […], "rows": [[…]], "stats": {…}}`. **Immer Parameter** (`$id`, `$title`), niemals einen Wert in die Anweisung einkleben (Injection). Eine **Map** ist nicht als Eigenschaft eines Knotens speicherbar: Speichern Sie Skalare, Listen von Skalaren oder das JSON als Text. Gdown kann auch benannte Cypher-Abfragen als REST-API veröffentlichen (OpenAPI-Verträge, Tokens, Limits): siehe [gluonify.io](https://gluonify.io).

### Verteilte Dateien (`source.store=files`)

Deployen Sie mit `"distributed": ["std"]`: `/distributed/std` erscheint, von allen Replikaten auf allen Knoten geteilt, mit 2 Kopien auf den Speicherknoten. **Regeln** (sie erklären die Form von [`FileNoteStore`](src/main/java/io/gluonify/source/notes/FileNoteStore.java)):

1. **Niemals eine Datei neu schreiben oder einen Ordner umbenennen, der gerade geschrieben wurde**: Schreiben Sie **neue** Dateien unter ihrem endgültigen Namen (deshalb ist eine Notiz unveränderlich); Löschen ist erlaubt.
2. **Die Liste eines Ordners kann ~3 Sekunden Verzögerung** gegenüber einem anderen Replikat haben: Eine auf Replikat A angelegte Notiz kann einen Moment brauchen, bis sie auf B erscheint.
3. Eine Datei wird **beim Schließen** sichtbar; tolerieren Sie trotzdem eine unlesbare Datei.
4. Standardmäßig keine Sperren zwischen Knoten (Option `distributedLocks`).
5. **Ein HTTP-Request-Body wird bis zum Ende gelesen**, sonst blockiert die wiederverwendete Verbindung.

### Webhooks von Photon empfangen

Photon empfängt die Nachrichten Ihrer Partner (prüft deren HMAC-Signatur, wandelt XML, SOAP oder Formular in JSON um) und **liefert** sie dann an Ihren Dienst. Zustellungsvertrag:

- **ein 2XX-Code bestätigt**; jeder andere Code (oder ein Ausfall) führt zur **Wiederholung** gemäß der Webhook-Richtlinie (`retry`) und danach zur Dead-Letter-Queue;
- also **antworten Sie schnell** und seien Sie **idempotent**: »mindestens einmal« bedeutet, dass dieselbe Nachricht zweimal ankommen kann. Der Header `X-Gluonify-Event-Id` ist der Schlüssel zur Deduplizierung; `X-Gluonify-Delivery-Attempt` zählt die Versuche; `X-Gluonify-Webhook-Id` benennt den Webhook. Der Arbeitsspeicher wird **nicht** zwischen Replikaten geteilt: Hier werden die Kennungen im über `source.store` gewählten Speicher abgelegt (Arbeitsspeicher, eine Datei pro Ereignis, mit `CREATE_NEW` in `/distributed/std/events` angelegt, oder ein durch eine Eindeutigkeitsbedingung geschützter Knoten in Gdown), sodass genau ein Replikat ein Ereignis annimmt.
- Photon sendet **kein** Charm-Token: Schützen Sie den Einstiegspunkt mit einem Schlüssel, den Sie in das Ziel des Webhooks (`"headers": {"X-Api-Key": "…"}`, siehe [`deploy/photon-webhook.json`](deploy/photon-webhook.json)) und in den Tresor (`WEBHOOK_KEY`) legen. **Ohne konfigurierten Schlüssel ist der Empfänger geschlossen** (404).

[`WebhookResource`](src/main/java/io/gluonify/source/platform/WebhookResource.java) zeigt das Ganze (Schlüssel in konstanter Zeit verglichen, Deduplizierung, unlesbares JSON → 400); ersetzen Sie den Body von `receive` durch Ihre Verarbeitung.

### Eine andere Anwendung aufrufen

Deklarieren Sie sie in `"uses"`: Die Plattform stellt `GLUONIFY_SERVICE_<APP>_URL` bereit **und öffnet das Netzwerk** zu ihr (ohne `uses` erreicht die Anwendung sie nicht). Schreiben Sie die Adresse niemals fest in den Code: Lesen Sie die Variable (siehe `ping` in [`PlatformResource`](src/main/java/io/gluonify/source/platform/PlatformResource.java)).

### Statische Sites und Domains

- **Field** liefert eine statische Site (ZIP) auf Ihrer Domain aus; in diesem Dienst ist nichts zu programmieren.
- **Ihre Domain**: `PUT /domains/<name>` auf der API von Higgs (siehe [`deploy/domain.json`](deploy/domain.json)) leitet einen Pfad zur Anwendung und wendet **Richtlinien** an: Charm-Token (Audience, Rollen), Ratenbegrenzungen, CORS, Adresslisten. Das HTTPS-Zertifikat wird automatisch bezogen.

## 8. Bauen

```bash
mvn test                       # Java-Tests (34)
mvn package                    # JVM: target/quarkus-app/; baut auch die Oberfläche (Quinoa)
```

**Die native ausführbare Datei** (was Gluonify ausführt: eine Datei, ohne JVM):

```bash
docker build --target out --output type=local,dest=dist -f Dockerfile.build .
./dist/gluonify-source         # läuft auf diesem Linux, Port 8080
```

Die Datei `dist/gluonify-source` ist eine ausführbare **glibc**-Linux-Datei der Architektur Ihrer Docker-Installation (arm64 auf Apple Silicon, amd64 auf x86_64). Für die andere Architektur: `docker build --platform linux/amd64 …` (langsam: Emulation; siehe die Kommentare in `Dockerfile.build`).

Ohne Docker: `mvn package -Dnative` setzt eine installierte GraalVM (NIK 25) voraus.

**Die Normen der Plattform**: Nach dem Klonen kontrolliert der Builder von Gluonify das Projekt (`R-SANTE`, `R-SECRET`, `R-FICHIER-SENSIBLE`, `R-NATIF`, `R-IMAGE`, `R-ECOUTE`) und lehnt ein Repository ab, das sie nicht einhält. [`ConformityTest`](src/test/java/io/gluonify/source/ConformityTest.java) prüft sie **bei Ihnen**; behalten Sie ihn.

## 9. Deployen

Es gibt zwei Wege.

**A. Gluonify baut für Sie** (am einfachsten): Geben Sie dem Builder (**gluonify-bup**) die Git-Adresse:

```bash
curl -X POST "$BUP_URL/v1/builds" -H 'Content-Type: application/json' -H "X-Git-Token: $GIT_TOKEN" \
  -d '{"name":"gluonify-source","gitUrl":"https://github.com/VOUS/VOTRE-DEPOT.git","ref":"main","deploy":true,"replicas":2,"memoryMb":128,"topNamespace":"gluonify-source"}'
```

Der Builder klont, prüft die Konformität, kompiliert nativ, veröffentlicht die ausführbare Datei und deployt sie. Verfolgen Sie ihn: `GET /v1/builds/<id>` und `/logs`. (Ein Git-Push kann den Build auch per Webhook auslösen: `POST /v1/webhooks/git`.) Hinweis: Der Builder kennt nur `topNamespace`, daher kommen die Tresor-Schlüssel damit ohne Präfix an.

**B. Sie veröffentlichen die ausführbare Datei** selbst und deployen dann:

```bash
SHA=$(shasum -a 256 dist/gluonify-source | cut -d' ' -f1)
# veröffentlichen Sie dist/gluonify-source unter einer HTTPS-Adresse, dann:
curl -X PUT "https://api.$ZONE/apps/gluonify-source" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d @deploy/appspec.json     # nachdem Sie Ihre Adresse und Ihren Fingerabdruck in artifacts eingetragen haben
```

Die Anwendung ist dann unter `https://gluonify-source.<zone>` erreichbar (automatisches Zertifikat). Die Felder von [`deploy/appspec.json`](deploy/appspec.json):

| Feld | Aufgabe |
|---|---|
| `artifacts` | die ausführbare Datei pro Architektur: `URL#sha256=…` (der Fingerabdruck ist **Pflicht**, vom Knoten geprüft) |
| `replicas` | Anzahl der Instanzen (2 oder mehr: Updates ohne Unterbrechung) |
| `memoryMb` | Speicherobergrenze pro Instanz (eine native Anwendung kommt mit 64 bis 128 MB aus) |
| `uses` | erreichbare Dienste: `GLUONIFY_SERVICE_<APP>_URL` bereitgestellt, Netzwerk geöffnet (`id` = Charm, `graphdb` = Gdown) |
| `distributed` | `["std"]` für `/distributed/std` |
| `env` | **nicht geheime** Variablen; `${NAME}` und `${NAME:-default}` werden aufgelöst |
| `vault` | `true`: Die Steuerungsebene weist der Anwendung ihren eigenen Tresor-Namensraum `<uuid>.app` zu, dessen Schlüssel als `APP_<SCHLÜSSEL>` ankommen |
| `gdownDatabase` | `true`: eine dedizierte Gdown-Datenbank für die Anwendung (eigene Raft-Gruppe, eingeschränktes Konto); `GLUONIFY_GDOWN_DATABASE`, `GLUONIFY_GDOWN_USER`, `GLUONIFY_GDOWN_PASSWORD` und `GLUONIFY_SERVICE_GDOWN_URL` werden bereitgestellt; nie mit der Anwendung gelöscht (siehe [`deploy/appspec-graph.json`](deploy/appspec-graph.json)) |
| `topNamespace` | Alternative: ein bestehender Tresor-Namensraum per Name; seine Schlüssel kommen **ohne Präfix** an (kein `APP_`) |
| `internal` | `true`: keine öffentliche Route (interner Dienst) |

Aktualisieren: dasselbe `PUT` (oder ein neuer Build); die Replikate werden **nacheinander** ersetzt. Neu starten ohne Änderung: `POST /apps/<name>/redeploy`. Logs: `GET /apps/<name>/logs`. Metriken: `/q/metrics`.

## 10. Testen

```bash
mvn test                                 # 34 Java-Tests
cd src/main/webui && npm install && npm test   # 15 Oberflächentests
```

| Test | Anzahl | Was er prüft |
|---|---|---|
| `NotesResourceTest` | 7 | 401 ohne Token, 403 ohne die passende Rolle, vollständiger Lebenszyklus, Validierung 400, OpenAPI, Gesundheit, Metriken |
| `FileNoteStoreTest` | 6 | neue Dateien nie umbenannt, Reihenfolge, zwei Replikate im selben Ordner, unlesbare Dateien ignoriert, Kennungen nie Pfade |
| `GraphNoteStoreTest` | 4 | gegen ein gefälschtes Gdown: Pfad, Authentifizierung, **parametrisierte** Anweisung, Lesen der Zeilen, Fehlschlag |
| `WebhookResourceTest` / `WebhookClosedTest` | 4 + 1 | Schlüssel, Bestätigung, **Idempotenz**, Ablehnungen; ohne Schlüssel geschlossen |
| `PlatformResourceTest` | 4 | Plattformvariablen gelesen, **kein Geheimnis zurückgegeben**, Dienst-zu-Dienst-Aufruf auf deklarierte Dienste beschränkt |
| `ConformityTest` | 4 | die Normen des Builders, bei Ihnen |
| `EventLedgerTest` | 4 | Idempotenz-Register: genau ein Gewinner pro Ereignis (Speicher, Dateien mit zwei Replikaten, Eindeutigkeitsbedingung des Graphen, Graph-Fehler außer einem Duplikat weitergegeben) |

Ein API-Test wird von `NotesResourceTest` kopiert; ein Speichertest von `FileNoteStoreTest` (ohne Quarkus zu starten: schnell).

## 11. Bekannte Stolperfallen

- **Nativ**: Erzeugen Sie **keinen** `HttpClient`, kein `Random` und kein `SecureRandom` in einem `static`-Feld (der Zustand würde zur Kompilierzeit eingefroren, nicht zur Laufzeit): Erzeugen Sie sie bei der ersten Verwendung (siehe `GraphNoteStore.client()`). Lesen Sie das JSON von Dritten als Baum (`JsonNode`) statt in Klassen; ein von Jackson außerhalb einer REST-Signatur (de)serialisierter Typ muss `@RegisterForReflection` tragen. **Ein Fehler, der nur nativ auftritt, ist in `mvn test` nicht zu sehen**: Starten Sie die native ausführbare Datei vor der Auslieferung.
- **Jackson 3**: Das Paket heißt `tools.jackson.databind`, nicht `com.fasterxml.jackson.databind`.
- **Keine Adresse, kein Port, kein Passwort fest im Code**: Alles kommt aus der Umgebung.
- **Verteilte Dateien**: siehe die Regeln in [§7](#verteilte-dateien-sourcestorefiles) (niemals neu schreiben, ~3 s Verzögerung).
- **Webhooks**: schnell mit 2XX antworten, idempotent sein, mit einem Schlüssel schützen.
- **Eine Entwicklungsidentität hat in der Produktion nichts verloren**: Behalten Sie `@IfBuildProfile("dev")`.
- **Quinoa und die API-Adressen**: Tragen Sie jedes neue API-Präfix in `quarkus.quinoa.ignored-path-prefixes` ein.
- **Unter x86_64-Emulation** (Docker auf Apple Silicon): Bauen Sie mit `--build-arg MAVEN_OPTS=-Djdk.lang.Process.launchMechanism=VFORK`.

## 12. Fehlerbehebung

| Symptom | Wahrscheinliche Ursache |
|---|---|
| 401 in der Produktion | kein Token, abgelaufenes Token oder **Audience** abweichend von `GLUONIFY_ZZZNONE_OIDC_AUDIENCE`; oder `GLUONIFY_ZZZNONE_OIDC_ISSUER` stimmt nicht mit dem `iss` des Tokens überein |
| 403 | gültiges Token, aber ohne die Rolle (`source:read` / `source:write`) |
| Die Anwendung erhält keinen Verkehr | `/q/health/ready` antwortet nicht mit 200 (Speicher nicht erreichbar?): `GET /apps/<name>` zeigt die bereiten Instanzen |
| `store=gdown`: »source.graph.url is empty« | die Anwendung ist nicht mit `"uses": ["gdown"]` deployt |
| Gdown: 401 oder »Unsupported property value type« | falsches Konto; oder eine als Eigenschaft gespeicherte **Map** (siehe §7) |
| Eine angelegte Notiz erscheint nicht sofort (Dateien) | Listen-Cache von ~3 s zwischen Replikaten: aktualisieren |
| Der Build des Repositorys wird abgelehnt | eine Regel `R-…`: die Meldung nennt sie; `mvn test` (`ConformityTest`) zeigt sie bei Ihnen |
| `mvn package` findet Node nicht | Netzwerkzugriff zum Herunterladen, oder `-Dquarkus.quinoa=false`, um ohne Oberfläche zu bauen |
| Die native Anwendung stürzt beim Start ab, in der JVM aber nicht | ein zur Kompilierzeit eingefrorener Zustand (`static`) oder fehlende Reflexion (§11) |

---

*Gluonify ist eine minimalistische Plattform für native Quarkus-Anwendungen: [gluonify.cloud](https://gluonify.cloud) (Vorstellung) und [gluonify.io](https://gluonify.io) (Technik). Dieses Repository ist bewusst klein: Es lässt sich in einer Stunde lesen.*
