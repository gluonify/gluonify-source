package io.gluonify.source.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** GET /api/auth-config: public, no client by default (the UI then says which variable is missing), Charm's public base derived from the issuer. */
@QuarkusTest
class AuthConfigResourceTest {
    @Test
    void publicWithoutTokenAndEmptyByDefault() {
        // tests run with OIDC off (like mvn quarkus:dev): the API does not ask for a token, so the UI must not ask for a sign-in
        given().when().get("/api/auth-config").then().statusCode(200)
                .body("signInRequired", is(false)).body("clientId", nullValue()).body("authorizationUrl", nullValue()).body("scope", is("openid profile"));
    }

    @Test
    void charmBaseFromTheIssuerOrTheConfiguredAddress() {
        assertEquals(Optional.of("https://gluonify.lan"), AuthConfigResource.charmUrl(Optional.empty(), "https://gluonify.lan/realms/gluonify"));
        assertEquals(Optional.of("https://id.example.com"), AuthConfigResource.charmUrl(Optional.of("https://id.example.com/"), "https://gluonify.lan/realms/gluonify"));
        assertEquals(Optional.empty(), AuthConfigResource.charmUrl(Optional.empty(), "any"));
        assertEquals(Optional.empty(), AuthConfigResource.charmUrl(Optional.of("id.example.com"), "any"));
        assertEquals(Optional.empty(), AuthConfigResource.charmUrl(Optional.of(" "), null));
    }
}
