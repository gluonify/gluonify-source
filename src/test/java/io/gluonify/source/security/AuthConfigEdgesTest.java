package io.gluonify.source.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.gluonify.source.TestConfig;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The sign-in configuration without the container: blank values, the issuer forms, trailing slashes. */
class AuthConfigEdgesTest {
    private static AuthConfigResource.AuthConfig get(final Map<String, String> values, final String issuer) {
        return new AuthConfigResource(TestConfig.of(values), true, issuer).get();
    }

    @Test
    void aBlankClientIdOrAnUnknownCharmLeavesTheEndpointsOut() {
        assertEquals(null, get(Map.of("auth.client-id", "  ", "auth.charm-url", "https://charm.example"), "any").clientId());
        assertEquals(null, get(Map.of("auth.client-id", "source-ui"), "any").authorizationUrl(), "no Charm address and an issuer that is not a URL");
    }

    @Test
    void theClientIdAndTheCharmAddressGiveTheRootEndpointsOfThePlatformRealm() {
        final var c = get(Map.of("auth.client-id", " source-ui ", "auth.charm-url", "https://charm.example//"), "https://charm.example/realms/gluonify/");
        assertEquals("source-ui", c.clientId());
        assertEquals("https://charm.example/authorize", c.authorizationUrl());
        assertEquals("https://charm.example/token", c.tokenUrl());
        assertEquals("https://charm.example/logout", c.logoutUrl());
        assertEquals("openid profile", c.scope());
    }

    @Test
    void anOrganizationRealmGetsItsOwnEndpoints() {
        final var c = get(Map.of("auth.client-id", "source-ui"), "https://charm.example/realms/org-acme");
        assertEquals("https://charm.example/realms/org-acme/protocol/openid-connect/auth", c.authorizationUrl());
        assertEquals("https://charm.example/realms/org-acme/protocol/openid-connect/token", c.tokenUrl());
    }

    @Test
    void endpointBaseHandlesAMissingIssuerAndAnEmptyRealm() {
        assertEquals("https://c", AuthConfigResource.endpointBase("https://c", null));
        assertEquals("https://c", AuthConfigResource.endpointBase("https://c", "https://c"));
        assertEquals("https://c", AuthConfigResource.endpointBase("https://c", "https://c/realms/"));
        assertEquals("https://c", AuthConfigResource.endpointBase("https://c", "https://c/realms///"));
        assertEquals("https://c/realms/org-a", AuthConfigResource.endpointBase("https://c", "https://c/realms/org-a///"));
    }

    @Test
    void charmUrlComesFromTheConfigurationThenFromTheIssuer() {
        assertEquals(Optional.of("https://c"), AuthConfigResource.charmUrl(Optional.of(" https://c/// "), "any"));
        assertEquals(Optional.of("https://c"), AuthConfigResource.charmUrl(Optional.of("  "), "https://c/realms/x"));
        assertEquals(Optional.of("https://c"), AuthConfigResource.charmUrl(Optional.empty(), "https://c"));
        assertEquals(Optional.of("http://c"), AuthConfigResource.charmUrl(Optional.empty(), "http://c/"));
        assertFalse(AuthConfigResource.charmUrl(Optional.of("ftp://c"), null).isPresent(), "only http(s)");
        assertFalse(AuthConfigResource.charmUrl(Optional.empty(), null).isPresent());
        assertFalse(AuthConfigResource.charmUrl(Optional.empty(), "any").isPresent());
    }
}
