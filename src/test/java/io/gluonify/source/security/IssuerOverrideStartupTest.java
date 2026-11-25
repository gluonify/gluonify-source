package io.gluonify.source.security;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

/**
 * An organization's deployment that keeps the platform issuer as {@code GLUONIFY_SOURCE_OIDC_ISSUER}: the application starts, the override still wins, the
 * started configuration is reported by the startup check (its single log line is tested in {@code IssuerOverrideCheckTest}), and nothing of it reaches a
 * client.
 *
 * @author David CABILLIC
 */
@QuarkusTest
@TestProfile(IssuerOverrideStartupTest.StaleOverride.class)
class IssuerOverrideStartupTest {

    private static final String OVERRIDE = "https://gluonify.lan/realms/gluonify";

    /**
     * The variables Higgs injects for an application of the organization acme, plus an issuer override left over from a deployment without an organization.
     *
     * @author David CABILLIC
     */
    public static class StaleOverride implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {

            return Map.of("GLUONIFY_OIDC_CLIENT_ID", "app-source", "GLUONIFY_OIDC_ISSUER", "https://gluonify.lan/realms/org-acme", "GLUONIFY_OIDC_PUBLIC_URL",
                    "https://gluonify.lan", "GLUONIFY_OIDC_REALM", "org-acme", "GLUONIFY_SERVICE_CHARM_URL", "http://127.0.0.1:9999",
                    IssuerOverrideCheck.SOURCE_ISSUER, OVERRIDE);
        }
    }

    @Test
    void theOverrideStillWinsAndTheStartedConfigurationIsReported() {

        assertEquals(OVERRIDE, ConfigProvider.getConfig().getValue("quarkus.oidc.token.issuer", String.class), "warned, not refused");
        assertEquals("http://127.0.0.1:9999/realms/org-acme", ConfigProvider.getConfig().getValue("quarkus.oidc.auth-server-url", String.class));
        final String message = IssuerOverrideCheck.warning(name -> ConfigProvider.getConfig().getOptionalValue(name, String.class)).orElseThrow();
        assertTrue(message.startsWith("GLUONIFY_SOURCE_OIDC_ISSUER names realm 'gluonify', but GLUONIFY_OIDC_REALM is 'org-acme'"), message);
        assertFalse(message.contains("gluonify.lan"), message);
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void theMismatchIsNotExposedToClients() {

        for (final String path : List.of("/api/auth-config", "/q/health/ready", "/q/openapi", "/api/platform")) {
            final String body = given().when().get(path).then().extract().asString();
            assertFalse(body.contains(IssuerOverrideCheck.OIDC_REALM), path);
            assertFalse(body.contains(IssuerOverrideCheck.SOURCE_ISSUER), path);
        }
    }
}
