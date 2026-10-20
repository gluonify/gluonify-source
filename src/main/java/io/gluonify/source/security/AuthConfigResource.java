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
 *
 * <p>Deployed for an organization, the issuer is the realm of that organization ({@code <charm>/realms/org-<org>}, subscriptions S6): its endpoints are the
 * realm's own ({@code /realms/org-<org>/protocol/openid-connect/auth|token|logout}); the platform realm keeps Charm's root endpoints.
 *
 * @author David CABILLIC
 */
@Path("/api/auth-config")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Authentication")
public class AuthConfigResource {
    private static final String REALMS = "/realms/";
    private static final String PLATFORM_REALM = "gluonify";

    /** The answer: null fields when the client is not configured (the UI then explains which variable is missing). */
    public record AuthConfig(boolean signInRequired, String clientId, String authorizationUrl, String tokenUrl, String logoutUrl, String scope) {}

    private final SourceConfig config;
    private final boolean oidcEnabled;
    private final String issuer;

    @Inject
    public AuthConfigResource(final SourceConfig config, @ConfigProperty(name = "quarkus.oidc.enabled", defaultValue = "true") final boolean oidcEnabled,
            @ConfigProperty(name = "quarkus.oidc.token.issuer", defaultValue = "any") final String issuer) {
        this.config = config;
        this.oidcEnabled = oidcEnabled;
        this.issuer = issuer;
    }

    @GET
    @PermitAll
    @Operation(summary = "Public sign-in configuration of the UI (Charm client id and endpoints)")
    public AuthConfig get() {
        SourceConfig.Auth a = config.auth();
        Optional<String> client = a.clientId().map(String::strip).filter(s -> !s.isEmpty());
        Optional<String> charm = charmUrl(a.charmUrl(), issuer);
        if (client.isEmpty() || charm.isEmpty()) return new AuthConfig(oidcEnabled, null, null, null, null, a.scope());
        String base = endpointBase(charm.get(), issuer);
        String protocol = base.equals(charm.get()) ? "" : "/protocol/openid-connect";
        return new AuthConfig(oidcEnabled, client.get(), base + protocol + (protocol.isEmpty() ? "/authorize" : "/auth"), base + protocol + "/token",
                base + protocol + "/logout", a.scope());
    }

    /**
     * Base of the browser endpoints: Charm's public address for the platform realm, {@code <charm>/realms/<realm>} for any other realm (an organization's).
     *
     * @param charm  Charm's public address
     * @param issuer the token issuer
     * @return the base the endpoint paths are appended to
     */
    static String endpointBase(final String charm, final String issuer) {
        final String realm = realmOf(issuer);
        return realm.isEmpty() || PLATFORM_REALM.equals(realm) ? charm : charm + REALMS + realm;
    }

    /** The realm name in the issuer ({@code <charm>/realms/<realm>}), empty when there is none. */
    private static String realmOf(final String issuer) {
        if (issuer == null) return "";
        final int i = issuer.indexOf(REALMS);
        return i < 0 ? "" : stripTrailingSlashes(issuer.substring(i + REALMS.length()));
    }

    /** Linear-time replacement of {@code replaceAll("/+$", "")}, which backtracks on a long run of slashes. */
    private static String stripTrailingSlashes(final String v) {
        int end = v.length();
        while (end > 0 && v.charAt(end - 1) == '/') end--;
        return v.substring(0, end);
    }

    /** The public base of Charm: the configured one, otherwise the issuer without its /realms/... part (Charm's endpoints are at the root of its address). */
    static Optional<String> charmUrl(final Optional<String> configured, final String issuer) {
        String v = configured.map(String::strip).filter(s -> !s.isEmpty()).orElse(null);
        if (v == null && issuer != null && issuer.startsWith("http")) {
            int i = issuer.indexOf(REALMS);
            v = i > 0 ? issuer.substring(0, i) : issuer;
        }
        if (v == null || !(v.startsWith("https://") || v.startsWith("http://"))) return Optional.empty();
        return Optional.of(stripTrailingSlashes(v));
    }
}
