package io.gluonify.source.security;

import io.gluonify.source.SourceConfig;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * What the UI needs to sign in with Charm: the client id and Charm's PUBLIC endpoints. The UI never asks for a password: it sends the browser to Charm's own
 * login page (authorization code + PKCE) and gets a token for the audience gluonify-source back. Everything here is public (a PKCE client has no secret).
 *
 * <p>{@code signInRequired} is false when the API does not check tokens (OIDC off: {@code mvn quarkus:dev}, where {@link DevAuthentication} lets every request in):
 * the UI then shows everything without a sign-in button.
 */
@Path("/api/auth-config")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Authentication")
public class AuthConfigResource {
    /** The answer: null fields when the client is not configured (the UI then explains which variable is missing). */
    public record AuthConfig(boolean signInRequired, String clientId, String authorizationUrl, String tokenUrl, String logoutUrl, String scope) {}

    @Inject
    SourceConfig config;

    @ConfigProperty(name = "quarkus.oidc.enabled", defaultValue = "true")
    boolean oidcEnabled;

    @ConfigProperty(name = "quarkus.oidc.token.issuer", defaultValue = "any")
    String issuer;

    @GET
    @PermitAll
    @Operation(summary = "Public sign-in configuration of the UI (Charm client id and endpoints)")
    public AuthConfig get() {
        SourceConfig.Auth a = config.auth();
        Optional<String> client = a.clientId().map(String::strip).filter(s -> !s.isEmpty());
        Optional<String> charm = charmUrl(a.charmUrl(), issuer);
        if (client.isEmpty() || charm.isEmpty()) return new AuthConfig(oidcEnabled, null, null, null, null, a.scope());
        String base = charm.get();
        return new AuthConfig(oidcEnabled, client.get(), base + "/authorize", base + "/token", base + "/logout", a.scope());
    }

    /** The public base of Charm: the configured one, otherwise the issuer without its /realms/... part (Charm's endpoints are at the root of its address). */
    static Optional<String> charmUrl(Optional<String> configured, String issuer) {
        String v = configured.map(String::strip).filter(s -> !s.isEmpty()).orElse(null);
        if (v == null && issuer != null && issuer.startsWith("http")) {
            int i = issuer.indexOf("/realms/");
            v = i > 0 ? issuer.substring(0, i) : issuer;
        }
        if (v == null || !(v.startsWith("https://") || v.startsWith("http://"))) return Optional.empty();
        return Optional.of(v.replaceAll("/+$", ""));
    }
}
