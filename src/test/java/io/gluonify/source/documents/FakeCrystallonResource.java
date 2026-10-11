package io.gluonify.source.documents;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;

/** Starts a fake Crystallon for the test run and points the application at it (source.crystallon.url), as the platform would with {@code "uses": ["crystallon"]}. */
public class FakeCrystallonResource implements QuarkusTestResourceLifecycleManager {
    /** The running fake: read by the tests of this package (one Quarkus application per test class using this resource). */
    static volatile FakeCrystallon fake;

    @Override
    public Map<String, String> start() {
        fake = new FakeCrystallon();
        return Map.of("source.crystallon.url", fake.url());
    }

    @Override
    public void stop() {
        if (fake != null) fake.close();
        fake = null;
    }
}
