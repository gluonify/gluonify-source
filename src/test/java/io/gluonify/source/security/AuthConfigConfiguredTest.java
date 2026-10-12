package io.gluonify.source.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** With a client id and the issuer of the platform: the Charm endpoints the browser is sent to (never the internal GLUONIFY_SERVICE_CHARM_URL). */
@QuarkusTest
@TestProfile(AuthConfigConfiguredTest.Configured.class)
class AuthConfigConfiguredTest {
    public static class Configured implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("source.auth.client-id", "source-ui", "quarkus.oidc.token.issuer", "https://gluonify.lan/realms/gluonify", "GLUONIFY_SERVICE_CHARM_URL", "http://127.0.0.1:9999");
        }
    }

    @Test
    void endpointsOfCharmPublicAddress() {
        given().when().get("/api/auth-config").then().statusCode(200)
                .body("clientId", is("source-ui"))
                .body("authorizationUrl", is("https://gluonify.lan/authorize"))
                .body("tokenUrl", is("https://gluonify.lan/token"))
                .body("logoutUrl", is("https://gluonify.lan/logout"));
    }
}
