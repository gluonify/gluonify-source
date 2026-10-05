package io.gluonify.source.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.gluonify.source.notes.GraphNoteStore;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The idempotence ledgers: exactly one winner per event, whatever the replica (simulated by several instances on the same storage). */
class EventLedgerTest {
    @TempDir
    Path dir;

    @Test
    void memoireUnSeulGagnant() {
        MemoryEventLedger l = new MemoryEventLedger();
        assertTrue(l.firstSeen("e1"));
        assertFalse(l.firstSeen("e1"));
        assertTrue(l.firstSeen("e2"));
    }

    @Test
    void fichiersDeuxReplicasSurLeMemeDossierUnSeulGagnant() throws Exception {
        Path events = dir.resolve("events");
        List<FileEventLedger> replicas = List.of(new FileEventLedger(events), new FileEventLedger(events));
        AtomicInteger wins = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> ts = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            FileEventLedger r = replicas.get(i % 2);
            Thread t = Thread.ofPlatform().start(() -> {
                try {
                    go.await();
                    if (r.firstSeen("evt-../x")) wins.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            ts.add(t);
        }
        go.countDown();
        for (Thread t : ts) t.join();
        assertEquals(1, wins.get(), "exactly one winner for the same event id");
        assertTrue(replicas.get(0).firstSeen("autre"), "another event is independent");
        try (var s = Files.list(events)) {
            assertTrue(s.allMatch(p -> p.getFileName().toString().matches("[0-9a-f]{64}\\.evt")), "the identifier never becomes a file name");
        }
    }

    // --- Gdown, against a fake: the first CREATE succeeds, the next ones are refused by the uniqueness constraint ---
    @Test
    void graphContrainteCreeeUneFoisEtDoublonRefuse() throws Exception {
        ConcurrentLinkedQueue<String> statements = new ConcurrentLinkedQueue<>();
        AtomicInteger creates = new AtomicInteger();
        HttpServer gdown = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        gdown.createContext("/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            statements.add(body);
            int status = 200;
            String reply = "{\"columns\":[],\"rows\":[],\"stats\":{}}";
            if (body.contains("CREATE (:Event") && creates.incrementAndGet() > 1) {
                status = 400;
                reply = "{\"message\":\"Node(3) would break the uniqueness constraint `event_id`\",\"code\":\"QueryError\"}";
            } else if (body.contains("CREATE (:Event") && body.contains("boom")) {
                status = 500;
            }
            byte[] b = reply.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        gdown.start();
        try {
            GraphEventLedger l = new GraphEventLedger(new GraphNoteStore("http://127.0.0.1:" + gdown.getAddress().getPort(), "source", "notes", "pw"));
            assertTrue(l.firstSeen("e1"));
            assertFalse(l.firstSeen("e1"), "refused by the uniqueness constraint = duplicate");
            assertEquals(1, statements.stream().filter(s -> s.contains("CREATE CONSTRAINT") && s.contains("IS UNIQUE")).count(), "constraint created once");
            assertTrue(statements.stream().noneMatch(s -> s.contains("\"statement\":\"CREATE (:Event {id: 'e1'")), "parameters only");
        } finally {
            gdown.stop(0);
        }
    }

    @Test
    void graphUneErreurAutreQuUnDoublonEstPropagee() throws Exception {
        HttpServer gdown = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        gdown.createContext("/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            int status = body.contains("CREATE (:Event") ? 503 : 200;
            byte[] b = "{\"message\":\"cluster unavailable\"}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        gdown.start();
        try {
            GraphEventLedger l = new GraphEventLedger(new GraphNoteStore("http://127.0.0.1:" + gdown.getAddress().getPort(), "source", "notes", "pw"));
            assertThrows(IllegalStateException.class, () -> l.firstSeen("e1"), "an outage is not a duplicate: the webhook answers 5XX so Photon replays");
        } finally {
            gdown.stop(0);
        }
    }
}
