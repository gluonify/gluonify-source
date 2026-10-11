package io.gluonify.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
