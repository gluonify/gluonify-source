package io.gluonify.source.documents;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** A fake Crystallon: a JDK {@link HttpServer} that records the calls it receives and answers what the test programmed (a PDF by default). */
public final class FakeCrystallon implements AutoCloseable {
    public static final byte[] PDF = "%PDF-1.4\n%fake invoice\n%%EOF\n".getBytes(StandardCharsets.ISO_8859_1);

    /** A call received: target = raw path and query as sent. */
    public record Call(String method, String target, String authorization, String accept, String contentType, String body) {}

    /** An answer. */
    public record Reply(int status, String contentType, byte[] body, Map<String, String> headers) {
        public static Reply pdf() {
            return new Reply(200, "application/pdf", PDF, Map.of("X-Crystallon-Pages", "2", "X-Crystallon-Warnings", "1", "X-Crystallon-PDFA", "valid"));
        }

        public static Reply json(int status, String json) {
            return new Reply(status, "application/json", json.getBytes(StandardCharsets.UTF_8), Map.of());
        }

        public static Reply empty(int status) {
            return new Reply(status, null, new byte[0], Map.of());
        }
    }

    private final HttpServer server;
    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private final AtomicReference<Reply> reply = new AtomicReference<>(Reply.pdf());

    public FakeCrystallon() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", ex -> {
            String query = ex.getRequestURI().getRawQuery();
            calls.add(new Call(ex.getRequestMethod(), ex.getRequestURI().getRawPath() + (query == null ? "" : "?" + query), ex.getRequestHeaders().getFirst("Authorization"),
                    ex.getRequestHeaders().getFirst("Accept"), ex.getRequestHeaders().getFirst("Content-Type"), new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            Reply r = reply.get();
            if (r.contentType() != null) ex.getResponseHeaders().add("Content-Type", r.contentType());
            r.headers().forEach((k, v) -> ex.getResponseHeaders().add(k, v));
            ex.sendResponseHeaders(r.status(), r.body().length == 0 ? -1 : r.body().length);
            if (r.body().length > 0) ex.getResponseBody().write(r.body());
            ex.close();
        });
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public List<Call> calls() {
        return calls;
    }

    /** The only call received so far (fails if there is not exactly one). */
    public Call only() {
        if (calls.size() != 1) throw new AssertionError("expected one call to Crystallon, got " + calls);
        return calls.get(0);
    }

    /** Programs the next answers and forgets the calls received. */
    public FakeCrystallon answer(Reply r) {
        reply.set(r);
        calls.clear();
        return this;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
