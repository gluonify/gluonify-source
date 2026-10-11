package io.gluonify.source.documents;

import io.gluonify.plasma.crystallon.client.CrystallonClient;
import io.gluonify.plasma.crystallon.client.CrystallonTokens;
import io.gluonify.plasma.crystallon.client.Jackson3Codec;
import io.gluonify.source.SourceConfig;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;
import tools.jackson.databind.ObjectMapper;

/**
 * Builds the Crystallon client from the configuration, like {@code NoteStores} picks the storage. Crystallon is OPTIONAL: without {@code source.crystallon.url}
 * (the platform provides it as {@code GLUONIFY_SERVICE_CRYSTALLON_URL} when the application is deployed with {@code "uses": ["crystallon"]}) there is no client,
 * the documents feature is off, and the rest of the application starts and works as before.
 *
 * <p>The token Crystallon receives follows {@code source.crystallon.token} ({@link TokenMode}): the token of the person calling this application ({@code forwarding}, the default)
 * or the application's own token ({@code platform}). A wrong setting stops the application at startup ({@link Startup}) with a message that says what to do, instead of
 * failing at the first request.
 */
public class CrystallonClients {
    /** One client for the whole application (immutable, safe between threads; its HTTP client is created on first use, as native images require). */
    @Produces
    @Singleton
    @Startup
    Optional<CrystallonClient> crystallon(SourceConfig cfg, ObjectMapper mapper, ForwardedToken caller) {
        SourceConfig.Crystallon c = cfg.crystallon();
        return fromSettings(c.url(), c.token().orElse(null), c.tokenFile(), mapper, caller);
    }

    /**
     * @param url       {@code source.crystallon.url}; absent or blank = no client (feature off, and the other settings are not looked at)
     * @param mode      {@code source.crystallon.token}: {@code forwarding} or {@code platform}; absent or blank = {@code forwarding}
     * @param tokenFile {@code source.crystallon.token-file}: needed by {@code platform} only
     * @param mapper    the application's Jackson 3 mapper: it writes the generated records (ISO 8601 dates, {@code NON_NULL})
     * @param caller    the bearer token of the request being served, for {@code forwarding}
     * @return the client, or empty when the address is absent or blank (feature off)
     * @throws IllegalStateException when the address is set but is not an http(s) address, when the mode is unknown, or when {@code platform} has no token file
     */
    static Optional<CrystallonClient> fromSettings(Optional<String> url, String mode, Optional<String> tokenFile, ObjectMapper mapper, Supplier<String> caller) {
        return url.map(String::strip).filter(u -> !u.isEmpty()).map(u -> create(u, tokens(TokenMode.parse(mode), tokenFile, caller), mapper));
    }

    /** Where each call gets its token from. */
    static CrystallonTokens tokens(TokenMode mode, Optional<String> tokenFile, Supplier<String> caller) {
        return switch (mode) {
            case FORWARDING -> CrystallonTokens.forwarding(caller); // Crystallon sees the person who called us
            case PLATFORM -> platform(tokenFile); // Crystallon sees this application
        };
    }

    /**
     * The application's own token: the file the platform maintains, read again at every call (the node agent renews the token before it expires).
     * This is what {@code CrystallonTokens.platform()} does in the SDK sources, which the published SDK 0.6.0 does not have yet: with a newer SDK, replace this method by that one.
     */
    static CrystallonTokens platform(Optional<String> tokenFile) {
        String file = tokenFile.map(String::strip).orElse("");
        if (file.isEmpty()) {
            throw new IllegalStateException("source.crystallon.token=platform needs the file of the application token (source.crystallon.token-file, GLUONIFY_CRYSTALLON_TOKEN_FILE), which the platform provides when the application "
                    + "is deployed with \"uses\": [\"crystallon\"] on a platform that issues application tokens; otherwise use source.crystallon.token=forwarding");
        }
        try {
            return CrystallonTokens.file(Path.of(file));
        } catch (InvalidPathException e) {
            throw new IllegalStateException("source.crystallon.token-file (GLUONIFY_CRYSTALLON_TOKEN_FILE) is not a valid file path", e);
        }
    }

    /**
     * @param url    address of Crystallon (http or https)
     * @param tokens where the token of each call comes from
     * @param mapper the application's Jackson 3 mapper
     */
    static CrystallonClient create(String url, CrystallonTokens tokens, ObjectMapper mapper) {
        try {
            return CrystallonClient.builder().baseUrl(url).tokens(tokens).json(Jackson3Codec.of(mapper)).build();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("source.crystallon.url must be an http(s) address such as http://10.200.0.5:8502 (GLUONIFY_SERVICE_CRYSTALLON_URL): " + e.getMessage(), e);
        }
    }
}
