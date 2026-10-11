package io.gluonify.source.documents;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Starts a fake Crystallon AND the token file the platform would maintain, and points the application at both: address, {@code source.crystallon.token=platform} and
 * {@code source.crystallon.token-file}, as a deployment with {@code "uses": ["crystallon"]} and {@code SOURCE_CRYSTALLON_TOKEN=platform} would.
 */
public class FakePlatformCrystallonResource implements QuarkusTestResourceLifecycleManager {
    static volatile FakeCrystallon fake;
    /** The file of the application token: a test rewrites it to simulate the renewal done by the node agent. */
    static volatile Path tokenFile;

    @Override
    public Map<String, String> start() {
        fake = new FakeCrystallon();
        try {
            tokenFile = Files.createTempDirectory("gluonify-tokens").resolve("crystallon.token");
            Files.writeString(tokenFile, "app-token-1\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Map.of("source.crystallon.url", fake.url(), "source.crystallon.token", "platform", "source.crystallon.token-file", tokenFile.toString());
    }

    @Override
    public void stop() {
        if (fake != null) fake.close();
        fake = null;
        try {
            if (tokenFile != null) {
                Files.deleteIfExists(tokenFile);
                Files.deleteIfExists(tokenFile.getParent());
            }
        } catch (IOException e) {
            // a leftover temporary file is harmless
        }
        tokenFile = null;
    }
}
