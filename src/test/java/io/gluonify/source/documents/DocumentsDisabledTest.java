package io.gluonify.source.documents;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.gluonify.source.SourceConfig;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/** Crystallon NOT configured (the default: no "uses": ["crystallon"]): the application starts and works, the documents endpoint is off (404 with the reason). */
@QuarkusTest
class DocumentsDisabledTest {
    @Inject
    SourceConfig config;

    @Test
    void theTokenSettingsHaveSafeDefaults() {
        // application.properties writes both as ${ENVIRONMENT_VARIABLE:} (empty): no value, and the mode then is the caller's token
        assertTrue(config.crystallon().token().isEmpty());
        assertTrue(config.crystallon().tokenFile().isEmpty());
        assertEquals(TokenMode.FORWARDING, TokenMode.of(config.crystallon().token()));
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void theEndpointIsOffWithAClearReason() {
        given().contentType(ContentType.JSON).body(DocumentsResourceTest.REQUEST).when().post("/api/documents/invoice")
                .then().statusCode(404).contentType(ContentType.JSON).body("error", containsString("crystallon")).body("error", containsString("uses"));
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void platformInfoSaysDocumentsAreOff() {
        given().when().get("/api/platform").then().statusCode(200).body("documents", is(false)).body("documentsToken", is(""));
    }

    @Test
    void theRestOfTheApplicationIsNotAffected() {
        given().when().get("/q/health/ready").then().statusCode(200);
        given().contentType(ContentType.JSON).body(DocumentsResourceTest.REQUEST).when().post("/api/documents/invoice").then().statusCode(401); // off or on, the API stays behind the token
    }
}
