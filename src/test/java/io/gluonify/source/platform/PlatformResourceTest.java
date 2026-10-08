package io.gluonify.source.platform;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** What the platform provides: GLUONIFY_ENVIRONMENT, GLUONIFY_REPLICA, declared services (SERVICE_*_URL), never a secret; service-to-service call. */
@QuarkusTest
class PlatformResourceTest {
    @Inject
    PlatformResource platform;

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void forme() {
        given().when().get("/api/platform").then().statusCode(200).body("store", is("memory")).body("storeReady", is(true)).body("webhookOpen", is(false))
                .body("webhooks.accepted", is(0)).body("webhooks.duplicates", is(0));
    }

    @AfterEach
    void restaurerLEnvironnement() {
        platform.env = System::getenv;
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"}) // info() is protected by @RolesAllowed even when called directly
    void lesVariablesDeLaPlateformeSontLues() {
        platform.env = () -> Map.of("GLUONIFY_ENVIRONMENT", "SBX", "GLUONIFY_REPLICA", "2", "GLUONIFY_SELF_URL", "http://10.0.0.5:20003",
                "GLUONIFY_SERVICE_GDOWN_URL", "http://10.200.0.2:7474", "GLUONIFY_SERVICE_PHOTON_URL", "http://10.200.0.4:8080", "APP_GLUONIFY_GDOWN_PASSWORD", "secret-qui-ne-doit-jamais-sortir", "PATH", "/usr/bin");
        Map<String, Object> i = platform.info();
        assertEquals("SBX", i.get("envName"));
        assertEquals("2", i.get("envNode"));
        assertEquals("http://10.0.0.5:20003", i.get("selfUrl"));
        assertEquals(Map.of("gdown", "http://10.200.0.2:7474", "photon", "http://10.200.0.4:8080"), i.get("services"));
        assertEquals(false, i.toString().contains("secret-qui-ne-doit-jamais-sortir"), "no secret value is returned");
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void appelDeServiceASeulementPourLesApplicationsDeclarees() {
        platform.env = () -> Map.of("GLUONIFY_SERVICE_PHOTON_URL", "http://127.0.0.1:1");
        given().when().get("/api/platform/ping/inconnue").then().statusCode(404);
        given().when().get("/api/platform/ping/Pas.Valide").then().statusCode(400);
        given().when().get("/api/platform/ping/photon").then().statusCode(502); // declared but unreachable (port 1)
    }

    @Test
    void sansJetonFerme() {
        given().when().get("/api/platform").then().statusCode(401);
        given().when().get("/api/platform/ping/photon").then().statusCode(401);
    }
}
