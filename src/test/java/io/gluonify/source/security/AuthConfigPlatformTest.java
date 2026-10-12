package io.gluonify.source.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

/** Deployed on the platform ("uses": ["charm"]): only the GLUONIFY_OIDC_* variables that Higgs injects, nothing configured, and sign-in works. */
@QuarkusTest
@TestProfile(AuthConfigPlatformTest.Platform.class)
class AuthConfigPlatformTest {
    public static class Platform implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("GLUONIFY_OIDC_CLIENT_ID", "app-source", "GLUONIFY_OIDC_ISSUER", "https://gluonify.lan/realms/gluonify",
                    "GLUONIFY_OIDC_PUBLIC_URL", "https://login.gluonify.lan/", "GLUONIFY_OIDC_AUDIENCE", "gluonify-notes", "GLUONIFY_SERVICE_CHARM_URL", "http://127.0.0.1:9999");
        }
    }

    @Test
    void platformVariablesAreTheDefaults() {
        given().when().get("/api/auth-config").then().statusCode(200)
                .body("clientId", is("app-source"))
                .body("authorizationUrl", is("https://login.gluonify.lan/authorize"))
                .body("tokenUrl", is("https://login.gluonify.lan/token"))
                .body("logoutUrl", is("https://login.gluonify.lan/logout"));
        var c = ConfigProvider.getConfig();
        org.junit.jupiter.api.Assertions.assertEquals("https://gluonify.lan/realms/gluonify", c.getValue("quarkus.oidc.token.issuer", String.class));
        org.junit.jupiter.api.Assertions.assertEquals("gluonify-notes", c.getValue("quarkus.oidc.token.audience", String.class), "the audience of the application's own client");
    }
}
