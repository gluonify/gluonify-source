package io.gluonify.source.documents;

import io.gluonify.plasma.crystallon.client.CrystallonClient;
import io.gluonify.plasma.crystallon.client.CrystallonTokens;
import io.gluonify.plasma.crystallon.client.Jackson3Codec;
import io.gluonify.source.SourceConfig;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.function.Supplier;
import tools.jackson.databind.ObjectMapper;

/**
 * Builds the Crystallon client from the configuration, like {@code NoteStores} picks the storage. Crystallon is OPTIONAL: without {@code source.crystallon.url}
 * (the platform provides it as {@code GLUONIFY_SERVICE_CRYSTALLON_URL} when the application is deployed with {@code "uses": ["crystallon"]}) there is no client,
 * the documents feature is off, and the rest of the application starts and works as before.
 */
public class CrystallonClients {
    /** One client for the whole application (immutable, safe between threads; its HTTP client is created on first use, as native images require). */
    @Produces
    @Singleton
    Optional<CrystallonClient> crystallon(SourceConfig cfg, ObjectMapper mapper, ForwardedToken token) {
        return fromUrl(cfg.crystallon().url(), mapper, token);
    }

    /** @return the client, or empty when the address is absent or blank (feature off); @throws IllegalStateException when it is set but is not an http(s) address */
    static Optional<CrystallonClient> fromUrl(Optional<String> url, ObjectMapper mapper, Supplier<String> bearer) {
        return url.map(String::strip).filter(u -> !u.isEmpty()).map(u -> create(u, mapper, bearer));
    }

    /**
     * @param url    address of Crystallon (http or https)
     * @param mapper the application's Jackson 3 mapper: it writes the generated records (ISO 8601 dates, {@code NON_NULL})
     * @param bearer where the token of each call comes from (the token of the person calling this application)
     */
    static CrystallonClient create(String url, ObjectMapper mapper, Supplier<String> bearer) {
        try {
            return CrystallonClient.builder().baseUrl(url).tokens(CrystallonTokens.forwarding(bearer)).json(Jackson3Codec.of(mapper)).build();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("source.crystallon.url must be an http(s) address such as http://10.200.0.5:8502 (GLUONIFY_SERVICE_CRYSTALLON_URL): " + e.getMessage(), e);
        }
    }
}
