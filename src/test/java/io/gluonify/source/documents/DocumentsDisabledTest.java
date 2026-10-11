package io.gluonify.source.documents;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/** Crystallon NOT available (no fake services, no cluster known: the pom points GLUONIFY_HOME at an empty folder; on the platform: no "uses": ["crystallon"]): the application starts and works, the documents endpoint is off (404 with the reason). */
@QuarkusTest
class DocumentsDisabledTest {
    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void theEndpointIsOffWithAClearReason() {
        given().contentType(ContentType.JSON).body(DocumentsResourceTest.REQUEST).when().post("/api/documents/invoice")
                .then().statusCode(404).contentType(ContentType.JSON).body("error", containsString("crystallon")).body("error", containsString("uses"));
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void platformInfoSaysDocumentsAreOff() {
        given().when().get("/api/platform").then().statusCode(200).body("documents", is(false));
    }

    @Test
    void theRestOfTheApplicationIsNotAffected() {
        given().when().get("/q/health/ready").then().statusCode(200);
        given().contentType(ContentType.JSON).body(DocumentsResourceTest.REQUEST).when().post("/api/documents/invoice").then().statusCode(401); // off or on, the API stays behind the token
    }
}
