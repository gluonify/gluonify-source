package io.gluonify.source.notes;

import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * READINESS condition: the platform only sends traffic to this instance if /q/health/ready answers 200, hence if the storage is usable.
 * (/q/health/live depends on nothing external: a Gdown outage must not make the application restart in a loop.)
 */
@Readiness
// @Singleton, not @ApplicationScoped: Quarkus rewrites @ApplicationScoped classes with constructor injection, which makes JaCoCo lose their coverage.
@Singleton
public class StoreHealth implements HealthCheck {
    private final NoteStore store;

    @Inject
    public StoreHealth(final NoteStore store) {
        this.store = store;
    }

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.builder().name("stockage des notes").status(store.ready()).withData("store", store.kind()).build();
    }
}
