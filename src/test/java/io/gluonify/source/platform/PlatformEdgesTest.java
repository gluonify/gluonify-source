package io.gluonify.source.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.gluonify.plasma.services.GluonifyServicesException;
import io.gluonify.source.TestConfig;
import io.gluonify.source.documents.Documents;
import io.gluonify.source.notes.GraphNoteStore;
import io.gluonify.source.notes.MemoryNoteStore;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The platform page and the service-to-service call without the container; the ledgers' selection and limits. */
class PlatformEdgesTest {
    @TempDir
    Path dir;

    private static PlatformResource resource(final Map<String, String> config, final Documents documents, final Map<String, String> env) {
        final var cfg = TestConfig.of(config);
        return new PlatformResource(cfg, new MemoryNoteStore(), new WebhookResource(cfg, new MemoryEventLedger()), documents, () -> env);
    }

    private static Documents documents(final boolean fails) {
        return new Documents(null) {
            @Override
            public boolean enabled() {
                if (fails) throw new GluonifyServicesException("no cluster");
                return true;
            }
        };
    }

    @Test
    void theInfoPageAnswersWhenTheDocumentsCheckFails() {
        final var info = resource(Map.of("webhook.key", "  "), documents(true), Map.of("GLUONIFY_SERVICE_X_Y", "ignored", "GLUONIFY_SERVICE_PHOTON_URL", "http://p", "OTHER_URL", "o")).info();
        assertEquals(false, info.get("documents"));
        assertEquals(false, info.get("webhookOpen"), "a blank key keeps the receiver closed");
        assertEquals(Map.of("photon", "http://p"), info.get("services"), "only GLUONIFY_SERVICE_*_URL variables are services");
        assertEquals(true, resource(Map.of("webhook.key", "k"), documents(false), Map.of()).info().get("documents"));
    }

    @Test
    void pingReturnsTheStatusOfTheOtherApplication() throws Exception {
        final HttpServer other = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        other.createContext("/q/health/ready", ex -> {
            ex.sendResponseHeaders(204, -1);
            ex.close();
        });
        other.start();
        try {
            final var r = resource(Map.of(), documents(false), Map.of("GLUONIFY_SERVICE_MY_APP_URL", "http://127.0.0.1:" + other.getAddress().getPort()));
            assertEquals(Map.of("app", "my-app", "status", 204), r.ping("my-app").getEntity());
            assertEquals(200, r.ping("my-app").getStatus(), "the HTTP client is created once and reused");
        } finally {
            other.stop(0);
        }
    }

    @Test
    void anInterruptedPingIs504AndKeepsTheInterruptFlag() {
        Thread.currentThread().interrupt();
        try {
            assertEquals(504, resource(Map.of(), documents(false), Map.of("GLUONIFY_SERVICE_APP_URL", "http://127.0.0.1:1")).ping("app").getStatus());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void theLedgerFollowsTheStore() {
        final var producer = new EventLedgers();
        assertInstanceOf(MemoryEventLedger.class, producer.eventLedger(TestConfig.of(Map.of("store", "memory"))));
        assertInstanceOf(FileEventLedger.class, producer.eventLedger(TestConfig.of(Map.of("store", "files", "files.dir", dir.resolve("notes").toString()))));
        assertInstanceOf(GraphEventLedger.class, producer.eventLedger(TestConfig.of(Map.of("store", "gdown", "graph.url", "http://g", "graph.user", "u", "graph.password", "p"))));
    }

    @Test
    void theFileLedgerFailsWhenItsFolderCannotBeCreated() throws Exception {
        final Path file = java.nio.file.Files.writeString(dir.resolve("file"), "x");
        assertThrows(java.io.UncheckedIOException.class, () -> new FileEventLedger(file.resolve("events")).firstSeen("e"));
    }

    @Test
    void anUnknownDigestAlgorithmIsAnError() {
        assertEquals(64, FileEventLedger.digest("SHA-256", "x").length());
        assertThrows(IllegalStateException.class, () -> FileEventLedger.digest("NOPE-1", "x"));
    }

    @Test
    void theMemoryLedgerForgetsEverythingBeyondItsBound() {
        final var ledger = new MemoryEventLedger();
        for (int i = 0; i <= 10_001; i++) assertTrue(ledger.firstSeen("e" + i));
        assertTrue(ledger.firstSeen("e0"), "the bounded memory was cleared");
        assertFalse(ledger.firstSeen("e0"));
    }

    @Test
    void aGraphFailureWithoutMessageIsNotADuplicate() {
        final var graph = new GraphNoteStore("http://g", "source", "u", "p") {
            @Override
            public tools.jackson.databind.JsonNode run(final String statement, final Map<String, Object> parameters) {
                if (statement.startsWith("CREATE (:Event")) throw new IllegalStateException();
                return null;
            }
        };
        assertThrows(IllegalStateException.class, () -> new GraphEventLedger(graph).firstSeen("e"));
    }

    @Test
    void aBlankEventIdIsRefusedWithoutTouchingTheLedger() {
        final var hook = new WebhookResource(TestConfig.of(Map.of("webhook.key", "k")), id -> {
            throw new AssertionError("the ledger must not be called");
        });
        assertEquals(400, hook.receive("k", "  ", "w", "1", "{}").getStatus());
    }
}
