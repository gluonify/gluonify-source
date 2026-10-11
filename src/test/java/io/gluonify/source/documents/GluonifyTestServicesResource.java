package io.gluonify.source.documents;

import io.gluonify.plasma.testing.GluonifyTestServices;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;

/**
 * Starts the fake Gluonify services of the SDK ({@code GluonifyTestServices.start()}) for the test run and stops them ({@code close()}). Nothing to configure: the SDK finds the fake
 * Crystallon (address and token) by itself, as it finds the real one on the platform.
 */
public class GluonifyTestServicesResource implements QuarkusTestResourceLifecycleManager {
    /** The running fake services: read by the tests of this package (one Quarkus application per test class using this resource). */
    static volatile GluonifyTestServices services;

    @Override
    public Map<String, String> start() {
        services = GluonifyTestServices.start();
        return Map.of();
    }

    @Override
    public void stop() {
        if (services != null) services.close();
        services = null;
    }
}
