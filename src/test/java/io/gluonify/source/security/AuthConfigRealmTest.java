package io.gluonify.source.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

/**
 * Deployed for an organization (subscriptions S6): the platform injects the issuer and the name of the organization's realm; the UI gets the realm's own
 * endpoints and the API reads the realm's discovery and keys.
 *
 * @author David CABILLIC
 */
@QuarkusTest
@TestProfile(AuthConfigRealmTest.Realm.class)
class AuthConfigRealmTest {

    private static final String CHARM = "https://login.gluonify.lan";
    private static final String PROTOCOL = CHARM + "/realms/org-acme/protocol/openid-connect";

    /** The variables Higgs injects for an application of the organization acme. */
    public static class Realm implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {

            return Map.of("GLUONIFY_OIDC_CLIENT_ID", "app-source", "GLUONIFY_OIDC_ISSUER", "https://gluonify.lan/realms/org-acme",
                    "GLUONIFY_OIDC_PUBLIC_URL", CHARM + "/", "GLUONIFY_OIDC_AUDIENCE", "gluonify-source", "GLUONIFY_OIDC_REALM", "org-acme",
                    "GLUONIFY_SERVICE_CHARM_URL", "http://127.0.0.1:9999");
        }
    }

    @Test
    void theRealmsEndpointsAndKeys() {

        given().when().get("/api/auth-config").then().statusCode(200)
                .body("clientId", is("app-source"))
                .body("authorizationUrl", is(PROTOCOL + "/auth"))
                .body("tokenUrl", is(PROTOCOL + "/token"))
                .body("logoutUrl", is(PROTOCOL + "/logout"));
        assertEquals("http://127.0.0.1:9999/realms/org-acme", ConfigProvider.getConfig().getValue("quarkus.oidc.auth-server-url", String.class));
        assertEquals("ERROR", ConfigProvider.getConfig().getValue("quarkus.log.category.\"io.quarkus.oidc.runtime.OidcProvider\".level", String.class),
                "a refused token is never logged (the OIDC warning carries the whole token)");
    }

    @Test
    void endpointBaseFollowsTheIssuersRealm() {

        assertEquals(CHARM, AuthConfigResource.endpointBase(CHARM, "https://gluonify.lan/realms/gluonify"));
        assertEquals(CHARM, AuthConfigResource.endpointBase(CHARM, "any"));
        assertEquals(CHARM, AuthConfigResource.endpointBase(CHARM, null));
        assertEquals(CHARM, AuthConfigResource.endpointBase(CHARM, "https://gluonify.lan/realms/"));
        assertEquals(CHARM + "/realms/org-acme", AuthConfigResource.endpointBase(CHARM, "https://gluonify.lan/realms/org-acme/"));
    }
}
