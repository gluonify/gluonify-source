package io.gluonify.source.documents;

import io.gluonify.plasma.crystallon.client.CrystallonClient;
import io.gluonify.plasma.services.GluonifyServices;
import jakarta.inject.Singleton;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.Optional;

/**
 * Crystallon is OPTIONAL. There is nothing to configure: {@code gluonify-plasma-quarkus} produces the {@link CrystallonClient} (address and token found by the SDK:
 * the platform when the application is deployed with {@code "uses": ["crystallon"]}, your cluster after {@code gluonify login}, the fake services in tests) and this class only
 * asks whether the service is available. Not available = the documents feature is off ({@code POST /api/documents/invoice} answers 404) and nothing else changes.
 */
@Singleton
public class Documents {
    /** Name of the service in {@code "uses"}. */
    static final String SERVICE = "crystallon";

    private final Instance<CrystallonClient> crystallon; // always resolvable (default bean); it fails when the service cannot be reached, hence the check below

    @Inject
    public Documents(final Instance<CrystallonClient> crystallon) {
        this.crystallon = crystallon;
    }

    /** @return true when Crystallon can be reached from here (the platform provides it, or a cluster is known, or fake services run in a test) */
    public boolean enabled() {
        return GluonifyServices.optional(SERVICE).isPresent();
    }

    /**
     * The client of the SDK, which uses the application's own token on the platform (the one the node agent renews).
     *
     * @return the client, or empty when Crystallon is not available
     * @throws io.gluonify.plasma.services.GluonifyServicesException when it is available but cannot be used (expired developer session, discovery unreachable); the message says what to do
     */
    public Optional<CrystallonClient> client() {
        return enabled() ? Optional.of(crystallon.get()) : Optional.empty();
    }
}
