package io.gluonify.source.notes;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * Condition de PRÊT : la plateforme n'envoie du trafic à cette instance que si /q/health/ready répond 200, donc si le stockage est utilisable.
 * (/q/health/live, lui, ne dépend de rien d'extérieur : une panne de Gdown ne doit pas faire redémarrer l'application en boucle.)
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
