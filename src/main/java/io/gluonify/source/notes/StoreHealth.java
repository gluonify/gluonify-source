package io.gluonify.source.notes;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * READINESS condition: the platform only sends traffic to this instance if /q/health/ready answers 200, hence if the storage is usable.
 * (/q/health/live depends on nothing external: a Gdown outage must not make the application restart in a loop.)
 */
@Readiness
@ApplicationScoped
public class StoreHealth implements HealthCheck {
    @Inject
    NoteStore store;

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.builder().name("stockage des notes").status(store.ready()).withData("store", store.kind()).build();
    }
}
