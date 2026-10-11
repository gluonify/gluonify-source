package io.gluonify.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The application specs of {@code deploy/} (what you send to {@code PUT /apps/<name>}) are copied by readers: they must use the current names. {@code uses} lists the
 * applications of the platform the service calls ({@code charm}, {@code gdown}, {@code crystallon}...), {@code SOURCE_STORE} one of the storages this code knows.
 */
class DeploySpecsTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> STORES = Set.of("memory", "files", "gdown");
    private static final Set<String> OLD_NAMES = Set.of("id", "graphdb", "vault", "graph", "paas");
    /** The fields of gluonify.json that Bup (gluonify-bup) reads. */
    /** The services the SDK modules of this application carry a marker for: Bup infers them at build time, gluonify.json does not repeat them. */
    private static final Set<String> INFERRED = Set.of("crystallon");
    private static final Set<String> BUP_FIELDS = Set.of("framework", "port", "health", "healthPath", "portEnv", "sleep", "wakeTest", "archive", "uses", "gdownDatabase");

    private static List<Path> specs() throws IOException {
        try (var files = Files.list(Path.of("deploy"))) {
            return files.filter(p -> p.getFileName().toString().startsWith("appspec") && p.toString().endsWith(".json")).sorted().toList();
        }
    }

    @Test
    void everyApplicationSpecUsesCurrentNamesAndAKnownStore() throws IOException {
        List<Path> specs = specs();
        assertTrue(specs.size() >= 2, "the examples: " + specs);
        for (Path p : specs) {
            JsonNode spec = JSON.readTree(Files.readString(p));
            List<String> uses = new ArrayList<>();
            spec.path("uses").forEach(n -> uses.add(n.asString()));
            for (String use : uses) {
                assertTrue(use.matches("[a-z][a-z0-9-]*"), p + ": uses \"" + use + "\" is not an application name");
                assertFalse(OLD_NAMES.contains(use), p + ": uses \"" + use + "\" is an old name (the stack applications are charm, gdown, top...)");
            }
            assertTrue(uses.contains("charm"), p + ": the API verifies Charm tokens");
            assertTrue(uses.contains("crystallon"), p + ": crystallon is declared (GLUONIFY_SERVICE_CRYSTALLON_URL and the network to it)");
            assertEquals(uses.size(), Set.copyOf(uses).size(), p + ": uses has duplicates");
            String store = spec.path("env").path("SOURCE_STORE").asString("memory");
            assertTrue(STORES.contains(store), p + ": SOURCE_STORE=" + store + " is not one of " + STORES);
        }
    }

    /**
     * {@code gluonify.json} is what Gluonify's builder (Bup) reads at the root of the repository when it deploys after a build: it forwards {@code uses} and {@code gdownDatabase} to
     * {@code PUT /apps/<name>}, which REPLACES the whole spec. Since SDK 0.7.1, Bup also infers {@code uses} from the markers of the SDK modules on the runtime classpath
     * ({@code META-INF/gluonify/services/<service>}): {@code crystallon} comes from {@code gluonify-plasma-crystallon-client} and is added as {@code ?crystallon}. So
     * {@code gluonify.json} only declares what the SDK cannot detect ({@code charm}: tokens verified by {@code quarkus-oidc}; {@code gdown}: Cypher over HTTP), and the declared plus the
     * inferred services say the same thing as {@code deploy/appspec.json}, the example of the other deployment path (no build, hence no inference: everything is written there).
     * ({@code gdownDatabase} is the choice of whoever switches to the dedicated database: {@code deploy/appspec-graph.json}.)
     */
    @Test
    void gluonifyJsonDeclaresWhatTheSdkCannotDetectAndBupInfersTheRest() throws IOException {
        Path file = Path.of("gluonify.json");
        assertTrue(Files.isRegularFile(file), "gluonify.json at the root: without it, a deployment by the builder declares neither charm nor gdown (only the inferred ?crystallon)");
        assertTrue(Files.size(file) <= 16384, "the builder refuses a gluonify.json above 16 KB");
        JsonNode descriptor = JSON.readTree(Files.readString(file));
        assertTrue(descriptor.isObject(), "a JSON object");
        for (String key : descriptor.propertyNames()) {
            assertTrue(BUP_FIELDS.contains(key), "gluonify.json: \"" + key + "\" is not a field the builder reads " + BUP_FIELDS);
        }
        JsonNode spec = JSON.readTree(Files.readString(Path.of("deploy/appspec.json")));
        assertTrue(descriptor.path("uses").isArray(), "gluonify.json: uses is an array of service names");
        List<String> uses = new ArrayList<>();
        descriptor.path("uses").forEach(n -> {
            assertTrue(n.isString(), "uses holds strings only");
            uses.add(n.asString());
        });
        assertTrue(uses.size() <= 16, "the builder accepts 16 services at most");
        for (String use : uses) {
            assertTrue(use.matches("\\??[a-z0-9]([-a-z0-9]{0,40}[a-z0-9])?"), "uses \"" + use + "\": a service name, with a leading ? when optional");
        }
        List<String> names = uses.stream().map(u -> u.startsWith("?") ? u.substring(1) : u).toList();
        assertEquals(names.size(), Set.copyOf(names).size(), "uses has duplicates (gdown and ?gdown are the same service)");
        List<String> specUses = new ArrayList<>();
        spec.path("uses").forEach(n -> specUses.add(n.asString()));
        for (String inferred : INFERRED) {
            assertFalse(names.contains(inferred), "gluonify.json: \"" + inferred + "\" is inferred by Bup from the SDK marker, do not declare it");
            assertNotNull(Thread.currentThread().getContextClassLoader().getResource("META-INF/gluonify/services/" + inferred),
                    "the SDK on the runtime classpath carries the marker META-INF/gluonify/services/" + inferred + " (SDK 0.7.1 or later)");
        }
        Set<String> effective = new java.util.HashSet<>(names);
        effective.addAll(INFERRED);
        assertEquals(Set.copyOf(specUses), effective, "gluonify.json (builder) plus the inferred services and deploy/appspec.json (PUT /apps) must give the same services");
        assertTrue(!descriptor.has("gdownDatabase") || descriptor.path("gdownDatabase").isBoolean(), "gdownDatabase, when present, is true or false (the builder refuses anything else)");
    }
}
