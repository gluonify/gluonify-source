package io.gluonify.source.platform;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The Photon webhook receiver, key configured: authentication, idempotence on X-Gluonify-Event-Id, rejections. */
@QuarkusTest
@TestProfile(WebhookResourceTest.AvecCle.class)
class WebhookResourceTest {
    public static class AvecCle implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("source.webhook.key", "cle-de-test-photon");
        }
    }

    private io.restassured.specification.RequestSpecification hook(String key, String eventId) {
        var r = given().contentType(ContentType.JSON);
        if (key != null) r = r.header("X-Api-Key", key);
        if (eventId != null) r = r.header("X-Gluonify-Event-Id", eventId).header("X-Gluonify-Webhook-Id", "orders").header("X-Gluonify-Delivery-Attempt", "1");
        return r;
    }

    @Test
    void cleAbsenteOuFausse() {
        hook(null, "e1").body("{}").when().post("/hooks/events").then().statusCode(401);
        hook("mauvaise", "e1").body("{}").when().post("/hooks/events").then().statusCode(401);
    }

    @Test
    void evenementAcquitteUneFoisPuisDoublonToujoursAcquitte() {
        hook("cle-de-test-photon", "evt-A").body("{\"order\":{\"id\":42},\"x\":1}").when().post("/hooks/events").then().statusCode(200)
                .body("status", is("accepted")).body("eventId", is("evt-A")).body("webhook", is("orders")).body("attempt", is("1")).body("fields", is(2));
        // "at least once": the same event comes back (lost acknowledgement) -> 200 too (otherwise Photon would replay endlessly), without redoing the work
        hook("cle-de-test-photon", "evt-A").body("{\"order\":{\"id\":42},\"x\":1}").when().post("/hooks/events").then().statusCode(200).body("status", is("duplicate"));
        hook("cle-de-test-photon", "evt-B").body("{}").when().post("/hooks/events").then().statusCode(200).body("status", is("accepted"));
    }

    @Test
    void rejetsClairs() {
        hook("cle-de-test-photon", null).body("{}").when().post("/hooks/events").then().statusCode(400);
        hook("cle-de-test-photon", "evt-C").body("{pas du json").when().post("/hooks/events").then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void lesCompteursSontVisiblesSurLaPageDePlateforme() {
        hook("cle-de-test-photon", "evt-D").body("{}").when().post("/hooks/events").then().statusCode(200);
        given().when().get("/api/platform").then().statusCode(200).body("webhookOpen", is(true)).body("webhooks.accepted", org.hamcrest.Matchers.greaterThanOrEqualTo(1));
    }
}
