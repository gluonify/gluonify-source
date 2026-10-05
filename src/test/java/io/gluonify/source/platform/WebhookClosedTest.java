package io.gluonify.source.platform;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/** Without a configured key (the default case), the webhook receiver is closed: 404, even with a key. */
@QuarkusTest
class WebhookClosedTest {
    @Test
    void fermeTantQuAucuneCleNEstFournie() {
        given().contentType(ContentType.JSON).header("X-Api-Key", "n-importe-quoi").header("X-Gluonify-Event-Id", "e").body("{}").when().post("/hooks/events").then().statusCode(404);
    }
}
