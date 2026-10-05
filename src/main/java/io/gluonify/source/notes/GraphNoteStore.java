package io.gluonify.source.notes;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Notes dans <b>Gdown</b>, la base graphe de la plateforme, par son API HTTP : {@code POST <url>/db/<base>/query} avec {@code {"statement": "...", "parameters": {...}}}
 * et une authentification de base (un compte local de Gdown, voir le README : création de la base et du compte). Réponse : {@code {"columns": [...], "rows": [[...], ...]}}.
 *
 * <p>L'adresse de Gdown n'est pas écrite en dur : la plateforme la fournit par {@code SERVICE_GRAPHDB_URL} si l'application est déployée avec {@code "uses": ["graphdb"]}.
 * Le mot de passe vient du coffre ({@code APP_GRAPH_PASSWORD}), jamais du dépôt Git.
 *
 * <p>Deux pièges du natif, déjà évités ici : le {@link HttpClient} est créé à la première utilisation (pas dans un champ statique : l'état serait figé à la compilation),
 * et le JSON est lu comme un arbre ({@link JsonNode}), sans classe à enregistrer pour la réflexion.
 */
public class GraphNoteStore implements NoteStore {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String url, database, auth;
    private volatile HttpClient http;

    public GraphNoteStore(String url, String database, String user, String password) {
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.database = database;
        this.auth = "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String kind() {
        return "graph";
    }

    @Override
    public Note create(NewNote in, String author) {
        Note n = new Note(UUID.randomUUID().toString(), in.title(), in.body() == null ? "" : in.body(), Instant.now(), author);
        query("CREATE (:Note {id: $id, title: $title, body: $body, createdAt: $createdAt, author: $author})",
                Map.of("id", n.id(), "title", n.title(), "body", n.body(), "createdAt", n.createdAt().toString(), "author", author == null ? "" : author));
        return n;
    }

    @Override
    public List<Note> list() {
        return notes(query("MATCH (n:Note) RETURN n.id, n.title, n.body, n.createdAt, n.author ORDER BY n.createdAt DESC LIMIT 200", Map.of()));
    }

    @Override
    public Optional<Note> get(String id) {
        return notes(query("MATCH (n:Note {id: $id}) RETURN n.id, n.title, n.body, n.createdAt, n.author", Map.of("id", id))).stream().findFirst();
    }

    @Override
    public boolean delete(String id) {
        JsonNode r = query("MATCH (n:Note {id: $id}) DELETE n", Map.of("id", id));
        return r.path("stats").path("nodesDeleted").asInt(0) > 0;
    }

    @Override
    public boolean ready() {
        try {
            query("RETURN 1", Map.of());
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static List<Note> notes(JsonNode result) {
        List<Note> out = new ArrayList<>();
        for (JsonNode row : result.path("rows")) {
            out.add(new Note(row.get(0).asString(), row.get(1).asString(), row.get(2).asString(""), Instant.parse(row.get(3).asString()), row.get(4).asString("")));
        }
        return out;
    }

    private JsonNode query(String statement, Map<String, Object> parameters) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("statement", statement);
            body.put("parameters", parameters);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url + "/db/" + database + "/query")).timeout(Duration.ofSeconds(10))
                    .header("Authorization", auth).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8)).build();
            HttpResponse<String> r = client().send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 != 2) throw new IllegalStateException("Gdown : HTTP " + r.statusCode() + " " + r.body());
            return MAPPER.readTree(r.body());
        } catch (IOException e) {
            throw new IllegalStateException("Gdown injoignable : " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrompu", e);
        }
    }

    private HttpClient client() {
        HttpClient h = http;
        if (h == null) http = h = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(); // créé à la première utilisation (natif)
        return h;
    }
}
