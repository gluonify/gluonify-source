package io.gluonify.source.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

/** GLUONIFY_SOURCE_OIDC_* (and GLUONIFY_SOURCE_CHARM_PUBLIC_URL) override the values injected by the platform. */
@QuarkusTest
@TestProfile(AuthConfigOverrideTest.Override.class)
class AuthConfigOverrideTest {
    public static class Override implements QuarkusTestProfile {
        @java.lang.Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("GLUONIFY_OIDC_CLIENT_ID", "app-source", "GLUONIFY_OIDC_ISSUER", "https://gluonify.lan/realms/gluonify", "GLUONIFY_OIDC_PUBLIC_URL", "https://gluonify.lan",
                    "GLUONIFY_SOURCE_OIDC_CLIENT_ID", "my-client", "GLUONIFY_SOURCE_OIDC_ISSUER", "https://id.example.com/realms/gluonify",
                    "GLUONIFY_SOURCE_CHARM_PUBLIC_URL", "https://id.example.com", "GLUONIFY_SERVICE_CHARM_URL", "http://127.0.0.1:9999");
        }
    }

    @Test
    void ownVariablesWin() {
        given().when().get("/api/auth-config").then().statusCode(200)
                .body("clientId", is("my-client"))
                .body("authorizationUrl", is("https://id.example.com/authorize"));
        org.junit.jupiter.api.Assertions.assertEquals("https://id.example.com/realms/gluonify", ConfigProvider.getConfig().getValue("quarkus.oidc.token.issuer", String.class));
        org.junit.jupiter.api.Assertions.assertEquals("gluonify-source", ConfigProvider.getConfig().getValue("quarkus.oidc.token.audience", String.class));
    }
}
