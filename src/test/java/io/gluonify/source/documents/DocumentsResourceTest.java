package io.gluonify.source.documents;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.gluonify.plasma.crystallon.client.CrystallonClient;
import io.gluonify.plasma.testing.FakeCrystallon;
import io.gluonify.source.documents.generated.GluonifyInvoiceDocument;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code POST /api/documents/invoice} against the FAKE Crystallon of the SDK ({@code GluonifyTestServices}, no configuration): success (a PDF), what Crystallon receives (path, payload; the fake
 * accepts only the application's token), the mapping of Crystallon's failures, our own validation, and the roles of this application.
 */
@QuarkusTest
@QuarkusTestResource(value = GluonifyTestServicesResource.class, restrictToAnnotatedClass = true)
class DocumentsResourceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TOKEN = "eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJhZGEifQ.c2ln";

    static final String REQUEST = """
            {"number":"2026-0042","date":"2026-10-09","dueDate":"2026-11-08","subject":"Website relaunch",
             "customer":{"name":"Example Customer SA","street":"Rue du Lac","houseNumber":"7","postalCode":"1003","city":"Lausanne","country":"CH"},
             "lines":[{"description":"Website redesign (fixed price)","quantity":1,"unit":"pce","unitPrice":4800},
                      {"description":"Consulting, 12 hours","quantity":12,"unit":"h","unitPrice":160.00},
                      {"description":"Hosting","quantity":1,"unitPrice":480}]}
            """;

    @Inject
    Instance<CrystallonClient> crystallon;

    @BeforeEach
    void reset() {
        fake().reset();
    }

    private static FakeCrystallon fake() {
        return GluonifyTestServicesResource.services.crystallon();
    }

    /** The caller's token is only for THIS API (source:read); Crystallon never sees it. */
    private static Response post(String body, String token) {
        var req = given().contentType(ContentType.JSON).accept("application/pdf, application/json");
        if (token != null) req = req.header("Authorization", "Bearer " + token);
        return req.body(body).when().post("/api/documents/invoice");
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void rendersAPdfThroughCrystallonWithTheTokenOfTheApplication() throws Exception {
        Response r = post(REQUEST, TOKEN);
        r.then().statusCode(200).contentType("application/pdf").header("Content-Disposition", is("attachment; filename=\"invoice-2026-0042.pdf\""))
                .header("X-Crystallon-Pages", is("1")).header("X-Crystallon-Warnings", is("0"));
        byte[] pdf = r.asByteArray();
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1), "the body is the PDF Crystallon returned");

        assertEquals(1, fake().renders().size());
        FakeCrystallon.Render call = fake().lastRender();
        assertEquals("render", call.kind());
        assertEquals("Gluonify invoice", call.template(), "the generated facade calls the template by NAME");
        assertTrue(call.byName());
        assertEquals("pdfa=true", call.query(), "PDF/A asked");
        // the fake answers 401 to any token but the one of the application (GluonifyTestServices.TOKEN): getting here means Crystallon was called with the application's own token,
        // not with the one of the caller (TOKEN above), which is not even forwarded

        JsonNode sent = JSON.readTree(call.body());
        assertEquals("2026-0042", sent.at("/data/invoice/number").asString());
        assertEquals("2026-10-09", sent.at("/data/invoice/date").asString(), "a LocalDate is written as ISO 8601 text");
        assertEquals("Example Customer SA", sent.at("/data/recipient/name").asString());
        assertEquals("Gluonify Source Example Ltd", sent.at("/data/sender/name").asString(), "the issuer is added by the application");
        assertEquals(3, sent.at("/data/items").size());
        assertEquals(0, new java.math.BigDecimal("1920.00").compareTo(sent.at("/data/items/1/amount").decimalValue()), "amount = quantity x unit price, computed by the application");
        assertEquals("pce", sent.at("/data/items/2/unit").asString(), "default unit");
        assertEquals(0, new java.math.BigDecimal("8.1").compareTo(sent.at("/data/vatRate").decimalValue()), "default VAT rate");
        assertTrue(sent.at("/data").path("payment").isMissingNode(), "an absent optional value is not sent (NON_NULL): no payment, hence no QR-bill");
        assertTrue(sent.at("/params/lang").isMissingNode(), "no language asked: the language of the template applies");
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void theLanguageAndTheVatRateOfTheRequestAreSent() throws Exception {
        post(REQUEST.replace("\"number\"", "\"lang\":\"fr\",\"vatRate\":2.6,\"number\""), TOKEN).then().statusCode(200);
        JsonNode sent = JSON.readTree(fake().lastRender().body());
        assertEquals("fr", sent.at("/params/lang").asString());
        assertEquals(0, new java.math.BigDecimal("2.6").compareTo(sent.at("/data/vatRate").decimalValue()));
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void aCallerWithoutAnyCrystallonTokenIsServedAllTheSame() {
        post(REQUEST, null).then().statusCode(200); // Crystallon sees the application, not the person
        assertEquals(1, fake().renders().size());
    }

    @Test
    void outsideARequestTheApplicationStillHasItsToken() {
        // a timer, a webhook, a queue: no HTTP request, hence no caller token to forward; the application's own token makes the call possible
        new GluonifyInvoiceDocument(crystallon.get()).render(InvoiceMapping.payload(InvoiceMappingTest.request(), java.time.LocalDate.of(2026, 10, 11)));
        assertEquals(1, fake().renders().size());
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void validationErrorsOfTheTemplateAreMappedTo422WithCodeAndPath() {
        fake().failNext(422, "CRYSTALLON-0015", "Invalid template: unknown block type");
        post(REQUEST, TOKEN).then().statusCode(422).contentType(ContentType.JSON).body("error", is("Invalid template: unknown block type")).body("code", is("CRYSTALLON-0015"));
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void crystallonRefusingTheTokenOfTheApplicationIs502AndNotTheCallersFault() {
        fake().failNext(403, "CRYSTALLON-0003", "Missing role");
        post(REQUEST, TOKEN).then().statusCode(502).contentType(ContentType.JSON).body("error", containsString("token of the application")).body("error", containsString("gluonify:crystallon:render"))
                .body("error", containsString("HTTP 403")).body("error", org.hamcrest.Matchers.not(containsString("your token"))).body("error", org.hamcrest.Matchers.not(containsString(TOKEN)));
        fake().failNext(401, "CRYSTALLON-0001", "Missing or invalid token");
        post(REQUEST, TOKEN).then().statusCode(502).body("error", containsString("HTTP 401"));
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void anUnknownTemplateOrAFailingCrystallonIs502() {
        fake().failNext(404, "CRYSTALLON-0028", "No such template");
        post(REQUEST, TOKEN).then().statusCode(502).body("error", containsString("Gluonify invoice")).body("code", is("CRYSTALLON-0028"));
        fake().failNext(500, "CRYSTALLON-0001", "boom");
        post(REQUEST, TOKEN).then().statusCode(502).body("error", containsString("HTTP 500"));
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void ourOwnValidationRefusesABadRequestBeforeCallingCrystallon() {
        post("{\"number\":\"\",\"customer\":{\"name\":\"X\"},\"lines\":[{\"description\":\"d\",\"quantity\":1,\"unitPrice\":1}]}", TOKEN).then().statusCode(400);
        post("{\"number\":\"1\",\"customer\":{\"name\":\"X\"},\"lines\":[]}", TOKEN).then().statusCode(400);
        post("{\"number\":\"1\",\"lines\":[{\"description\":\"d\",\"quantity\":1,\"unitPrice\":1}]}", TOKEN).then().statusCode(400);
        post("{\"number\":\"1\",\"lang\":\"xx\",\"customer\":{\"name\":\"X\"},\"lines\":[{\"description\":\"d\",\"quantity\":1,\"unitPrice\":1}]}", TOKEN).then().statusCode(400);
        post("{\"number\":\"1\",\"customer\":{\"name\":\"X\"},\"lines\":[{\"description\":\"d\",\"quantity\":0,\"unitPrice\":1}]}", TOKEN).then().statusCode(400);
        post("{\"number\":\"1\",\"vatRate\":120,\"customer\":{\"name\":\"X\"},\"lines\":[{\"description\":\"d\",\"quantity\":1,\"unitPrice\":1}]}", TOKEN).then().statusCode(400);
        assertTrue(fake().renders().isEmpty(), "Crystallon is never called with a request this application refuses");
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void errorsAreJsonWhateverTheAcceptHeaderSaysAndTheSuccessIsAPdf() {
        // curl sends "Accept: */*": an error must not be typed as a PDF just because the PDF is the other thing this endpoint produces
        for (String accept : new String[] {"*/*", "application/pdf", "application/json", "application/pdf, application/json"}) {
            given().accept(accept).contentType(ContentType.JSON).body("{\"number\":\"\",\"lines\":[]}").when().post("/api/documents/invoice")
                    .then().statusCode(400).contentType(containsString("application/json")).body("violations", notNullValue());
        }
        fake().failNext(422, "CRYSTALLON-0015", "Invalid template");
        given().accept("*/*").contentType(ContentType.JSON).body(REQUEST).when().post("/api/documents/invoice").then().statusCode(422).contentType(containsString("application/json")).body("error", is("Invalid template"));
        given().accept("*/*").contentType(ContentType.JSON).body(REQUEST).when().post("/api/documents/invoice").then().statusCode(200).contentType("application/pdf");
    }

    @Test
    void theApiStaysClosedWithoutATokenOrWithoutTheRole() {
        post(REQUEST, null).then().statusCode(401);
        assertTrue(fake().renders().isEmpty());
    }

    @Test
    @TestSecurity(user = "x", roles = {"quelque:chose"})
    void aTokenWithoutTheRoleOfThisApplicationIsRefused() {
        post(REQUEST, TOKEN).then().statusCode(403);
        assertTrue(fake().renders().isEmpty());
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void platformInfoSaysDocumentsAreOn() {
        given().when().get("/api/platform").then().statusCode(200).body("documents", is(true));
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void theOpenApiContractDescribesTheEndpoint() {
        given().when().get("/q/openapi?format=json").then().statusCode(200).body(containsString("/api/documents/invoice")).body(containsString("application/pdf")).body(containsString("Documents"));
    }
}
