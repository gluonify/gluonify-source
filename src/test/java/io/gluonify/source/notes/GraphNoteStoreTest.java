package io.gluonify.source.notes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Gdown storage, against a fake Gdown (HTTP): path, basic authentication, Cypher statement and parameters, row reading, failure. */
class GraphNoteStoreTest {
    private static final ObjectMapper M = new ObjectMapper();
    HttpServer gdown;
    final ConcurrentLinkedQueue<String[]> seen = new ConcurrentLinkedQueue<>(); // {path, Authorization, body}
    volatile int status = 200;
    volatile String reply = "{\"columns\":[],\"rows\":[],\"stats\":{}}";

    @BeforeEach
    void start() throws Exception {
        gdown = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        gdown.createContext("/", ex -> {
            seen.add(new String[] {ex.getRequestURI().getPath(), ex.getRequestHeaders().getFirst("Authorization"), new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)});
            byte[] b = reply.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        gdown.start();
    }

    @AfterEach
    void stop() {
        gdown.stop(0);
    }

    GraphNoteStore store() {
        return new GraphNoteStore("http://127.0.0.1:" + gdown.getAddress().getPort() + "/", "source", "notes", "mot-de-passe");
    }

    @Test
    void creerEnvoieUneInstructionParametree() throws Exception {
        Note n = store().create(new NewNote("T", "corps"), "ada");
        String[] req = seen.poll();
        assertEquals("/db/source/query", req[0]);
        assertEquals("Basic " + Base64.getEncoder().encodeToString("notes:mot-de-passe".getBytes(StandardCharsets.UTF_8)), req[1]);
        JsonNode j = M.readTree(req[2]);
        assertTrue(j.get("statement").asString().startsWith("CREATE (:Note"));
        assertFalse(j.get("statement").asString().contains("corps"), "never a value in the statement text (injection): everything goes through parameters");
        assertEquals("corps", j.at("/parameters/body").asString());
        assertEquals(n.id(), j.at("/parameters/id").asString());
        assertEquals("ada", j.at("/parameters/author").asString());
    }

    @Test
    void listerLitLesLignes() {
        reply = "{\"columns\":[\"n.id\",\"n.title\",\"n.body\",\"n.createdAt\",\"n.author\"],\"rows\":[[\"1\",\"A\",\"x\",\"2026-10-05T10:00:00Z\",\"ada\"],[\"2\",\"B\",null,\"2026-10-05T09:00:00Z\",null]]}";
        List<Note> l = store().list();
        assertEquals(2, l.size());
        assertEquals("A", l.get(0).title());
        assertEquals("", l.get(1).body(), "null becomes an empty string");
        assertEquals("", l.get(1).author());
        assertEquals("ORDER BY n.createdAt DESC LIMIT 200", seen.poll()[2].replaceAll(".*(ORDER BY n.createdAt DESC LIMIT 200).*", "$1"));
    }

    @Test
    void lireUneNoteEtLaSupprimer() {
        reply = "{\"columns\":[\"n.id\",\"n.title\",\"n.body\",\"n.createdAt\",\"n.author\"],\"rows\":[[\"1\",\"A\",\"x\",\"2026-10-05T10:00:00Z\",\"ada\"]]}";
        assertEquals("A", store().get("1").orElseThrow().title());
        reply = "{\"columns\":[],\"rows\":[]}";
        assertTrue(store().get("zzz").isEmpty());
        reply = "{\"columns\":[],\"rows\":[],\"stats\":{\"nodesDeleted\":1}}";
        assertTrue(store().delete("1"));
        reply = "{\"columns\":[],\"rows\":[],\"stats\":{\"nodesDeleted\":0}}";
        assertFalse(store().delete("1"));
    }

    @Test
    void pretSeulementSiGdownRepond() {
        assertTrue(store().ready());
        status = 500;
        reply = "{\"error\":\"boum\"}";
        assertFalse(store().ready());
        gdown.stop(0);
        assertFalse(store().ready(), "unreachable: not ready, without throwing");
    }
}
