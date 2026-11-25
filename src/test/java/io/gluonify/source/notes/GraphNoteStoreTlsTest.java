package io.gluonify.source.notes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import io.gluonify.plasma.services.GluonifyTls;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Gdown storage when Gdown serves its HTTP API over TLS with a certificate of the cluster's internal authority (the platform then gives an {@code https://}
 * address and the root in {@code GLUONIFY_CA_FILE}): the store reaches it with the SDK's client, and stays "not ready" without the root. The authority here is a
 * throwaway self-signed certificate made by the JDK's keytool.
 */
class GraphNoteStoreTlsTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String REPLY = "{\"columns\":[\"1\"],\"rows\":[[1]],\"stats\":{}}";

    @TempDir
    Path dir;
    Path root;
    HttpsServer gdown;

    @BeforeEach
    void start() throws Exception {
        String password = UUID.randomUUID().toString();
        Path keystore = dir.resolve("ks.p12");
        root = dir.resolve("root.pem");
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        run(keytool, "-genkeypair", "-alias", "t", "-keyalg", "EC", "-groupname", "secp256r1", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost", "-validity", "2",
                "-keystore", keystore.toString(), "-storetype", "PKCS12", "-storepass", password);
        run(keytool, "-exportcert", "-rfc", "-alias", "t", "-keystore", keystore.toString(), "-storepass", password, "-file", root.toString());
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(keystore)) {
            ks.load(in, password.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password.toCharArray());
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        gdown = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        gdown.setHttpsConfigurator(new HttpsConfigurator(ctx));
        gdown.createContext("/", ex -> {
            ex.getRequestBody().readAllBytes();
            byte[] b = REPLY.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        gdown.start();
    }

    @AfterEach
    void stop() {
        gdown.stop(0);
    }

    private GraphNoteStore store(final UnaryOperator<String> environment) {
        return new GraphNoteStore("https://localhost:" + gdown.getAddress().getPort(), "source", "notes", "secret",
                () -> GluonifyTls.builder(TIMEOUT, HttpClient.Redirect.NEVER, environment).build());
    }

    @Test
    void readyWithTheClustersRoot() {
        GraphNoteStore store = store(k -> GluonifyTls.CA_FILE_ENV.equals(k) ? root.toString() : null);
        assertTrue(store.ready(), "the SDK's client trusts GLUONIFY_CA_FILE");
        assertEquals(1, store.run("RETURN 1", Map.of()).path("rows").size());
    }

    @Test
    void notReadyWithoutTheRoot() {
        assertFalse(store(k -> null).ready(), "the JDK's authorities do not know the cluster's root (PKIX)");
    }

    @Test
    void theDefaultClientIsTheSdks() {
        // no GLUONIFY_CA_FILE in the test process: the public constructor still builds a working client (system roots), which refuses the unknown authority
        assertFalse(new GraphNoteStore("https://localhost:" + gdown.getAddress().getPort(), "source", "notes", "secret").ready());
    }

    private static void run(final String... command) throws Exception {
        Process p = new ProcessBuilder(List.of(command)).redirectErrorStream(true).start();
        String output;
        try (InputStream in = p.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(0, p.waitFor(), output);
    }
}
