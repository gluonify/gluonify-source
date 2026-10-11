package io.gluonify.source.documents;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.gluonify.plasma.crystallon.client.CrystallonClient;
import io.gluonify.source.documents.generated.GluonifyInvoiceDocument;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code source.crystallon.token=platform}: Crystallon is called with the APPLICATION's token (the file the platform maintains), not with the token of the person calling.
 * Same fake Crystallon as {@link DocumentsResourceTest}; what changes is the Authorization header it receives and how a refusal is reported.
 */
@QuarkusTest
@QuarkusTestResource(value = FakePlatformCrystallonResource.class, restrictToAnnotatedClass = true)
class DocumentsPlatformTokenTest {
    private static final String CALLER_TOKEN = "eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJhZGEifQ.c2ln";

    @Inject
    Optional<CrystallonClient> crystallon;

    @BeforeEach
    void reset() throws IOException {
        Files.writeString(FakePlatformCrystallonResource.tokenFile, "app-token-1\n");
        FakePlatformCrystallonResource.fake.answer(FakeCrystallon.Reply.pdf());
    }

    private static FakeCrystallon fake() {
        return FakePlatformCrystallonResource.fake;
    }

    private static Response post(String token) {
        var req = given().contentType(ContentType.JSON).accept("application/pdf, application/json");
        if (token != null) req = req.header("Authorization", "Bearer " + token);
        return req.body(DocumentsResourceTest.REQUEST).when().post("/api/documents/invoice");
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void crystallonReceivesTheTokenOfTheApplicationNotTheOneOfTheCaller() {
        post(CALLER_TOKEN).then().statusCode(200).contentType("application/pdf");
        assertEquals("Bearer app-token-1", fake().only().authorization());
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void aCallerWithoutAnyCrystallonTokenIsServedAllTheSame() {
        post(null).then().statusCode(200); // in forwarding mode Crystallon would have received no token at all
        assertEquals("Bearer app-token-1", fake().only().authorization());
    }

    @Test
    void outsideARequestTheApplicationStillHasItsToken() {
        // a timer, a webhook, a queue: no HTTP request, hence nothing to forward; the application's token is what makes the call possible
        new GluonifyInvoiceDocument(crystallon.orElseThrow()).render(InvoiceMapping.payload(InvoiceMappingTest.request(), java.time.LocalDate.of(2026, 10, 11)));
        assertEquals("Bearer app-token-1", fake().only().authorization());
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void aRenewalOfTheTokenFileIsTakenIntoAccountWithoutARestart() throws IOException {
        post(null).then().statusCode(200);
        Files.writeString(FakePlatformCrystallonResource.tokenFile, "app-token-2\n"); // what the node agent does before the token expires
        post(null).then().statusCode(200);
        assertEquals("Bearer app-token-1", fake().calls().get(0).authorization());
        assertEquals("Bearer app-token-2", fake().calls().get(1).authorization());
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void aMissingTokenFileMakesCrystallonRefuseAndThatIsReportedAsAnApplicationProblem() throws IOException {
        Files.delete(FakePlatformCrystallonResource.tokenFile);
        fake().answer(FakeCrystallon.Reply.empty(401)); // what the real Crystallon says to a call without a token
        post(CALLER_TOKEN).then().statusCode(502).contentType(ContentType.JSON).body("error", containsString("token of the application")).body("error", containsString("\"uses\": [\"crystallon\"]"))
                .body("error", containsString("HTTP 401")).body("error", not(containsString("your token"))).body("error", not(containsString(CALLER_TOKEN)));
        assertNull(fake().only().authorization(), "no file, no header: the token of the caller is NOT used as a fallback");
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void aRefusalByCrystallonOfTheApplicationTokenIs502AndNeverShowsTheToken() {
        fake().answer(FakeCrystallon.Reply.empty(403));
        post(null).then().statusCode(502).body("error", containsString("gluonify:crystallon:render")).body("error", not(containsString("app-token-1")));
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void platformInfoSaysWhichTokenCrystallonReceivesAndNothingElse() {
        given().when().get("/api/platform").then().statusCode(200).body("documents", is(true)).body("documentsToken", is("platform"))
                .body(not(containsString("app-token-1"))).body(not(containsString("crystallon.token")));
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void theTemplateIsStillCalledByNameAndTheValidationOfTheRequestStillApplies() {
        post(null).then().statusCode(200);
        assertEquals("/v1/templates/by-name/Gluonify%20invoice/render?pdfa=true", fake().only().target());
        given().contentType(ContentType.JSON).body("{\"number\":\"\",\"lines\":[]}").when().post("/api/documents/invoice").then().statusCode(400);
        assertEquals(1, fake().calls().size(), "a request this application refuses never reaches Crystallon");
    }
}
