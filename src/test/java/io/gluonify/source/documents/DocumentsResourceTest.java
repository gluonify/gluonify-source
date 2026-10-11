package io.gluonify.source.documents;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code POST /api/documents/invoice} against a FAKE Crystallon (JDK HttpServer): success (a PDF), what Crystallon receives (path, forwarded bearer token, payload), the
 * mapping of Crystallon's failures, our own validation, and the roles of this application.
 */
@QuarkusTest
@QuarkusTestResource(value = FakeCrystallonResource.class, restrictToAnnotatedClass = true)
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
    ForwardedToken forwardedToken;

    @BeforeEach
    void reset() {
        FakeCrystallonResource.fake.answer(FakeCrystallon.Reply.pdf());
    }

    private static FakeCrystallon fake() {
        return FakeCrystallonResource.fake;
    }

    private static Response post(String body, String token) {
        var req = given().contentType(ContentType.JSON).accept("application/pdf, application/json");
        if (token != null) req = req.header("Authorization", "Bearer " + token);
        return req.body(body).when().post("/api/documents/invoice");
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void rendersAPdfThroughCrystallonWithTheForwardedToken() throws Exception {
        Response r = post(REQUEST, TOKEN);
        r.then().statusCode(200).contentType("application/pdf").header("Content-Disposition", is("attachment; filename=\"invoice-2026-0042.pdf\""))
                .header("X-Crystallon-Pages", is("2")).header("X-Crystallon-Warnings", is("1"));
        byte[] pdf = r.asByteArray();
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1), "the body is the PDF Crystallon returned");

        FakeCrystallon.Call call = fake().only();
        assertEquals("POST", call.method());
        assertEquals("/v1/templates/by-name/Gluonify%20invoice/render?pdfa=true", call.target(), "the generated facade calls the template by NAME (a path segment: %20), PDF/A asked");
        assertEquals("Bearer " + TOKEN, call.authorization(), "the caller's own token is forwarded");
        assertEquals("application/pdf", call.accept());
        assertEquals("application/json", call.contentType());

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
        JsonNode sent = JSON.readTree(fake().only().body());
        assertEquals("fr", sent.at("/params/lang").asString());
        assertEquals(0, new java.math.BigDecimal("2.6").compareTo(sent.at("/data/vatRate").decimalValue()));
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void withoutABearerHeaderNoTokenIsForwarded() {
        post(REQUEST, null).then().statusCode(200);
        assertNull(fake().only().authorization(), "no Authorization header is invented");
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void validationErrorsOfTheTemplateAreMappedTo422WithCodeAndPath() {
        fake().answer(FakeCrystallon.Reply.json(422, "{\"code\":\"CRYSTALLON-0015\",\"message\":\"Invalid template: unknown block type\",\"path\":\"body[2].columns[1].value\"}"));
        post(REQUEST, TOKEN).then().statusCode(422).contentType(ContentType.JSON).body("error", is("Invalid template: unknown block type")).body("code", is("CRYSTALLON-0015"))
                .body("path", is("body[2].columns[1].value"));
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void crystallonRefusingTheTokenIs403WithAnActionableMessage() {
        fake().answer(FakeCrystallon.Reply.empty(403));
        post(REQUEST, TOKEN).then().statusCode(403).contentType(ContentType.JSON).body("error", containsString("gluonify:crystallon:render")).body("error", containsString("gluonify-crystallon"))
                .body("error", org.hamcrest.Matchers.not(containsString(TOKEN)));
        fake().answer(FakeCrystallon.Reply.empty(401));
        post(REQUEST, TOKEN).then().statusCode(403).body("error", containsString("HTTP 401"));
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void anUnknownTemplateOrAFailingCrystallonIs502() {
        fake().answer(FakeCrystallon.Reply.json(404, "{\"code\":\"CRYSTALLON-0028\",\"message\":\"No such template\"}"));
        post(REQUEST, TOKEN).then().statusCode(502).body("error", containsString("Gluonify invoice")).body("code", is("CRYSTALLON-0028"));
        fake().answer(FakeCrystallon.Reply.json(500, "{\"code\":\"CRYSTALLON-0001\",\"message\":\"boom\"}"));
        post(REQUEST, TOKEN).then().statusCode(502).body("error", containsString("HTTP 500"));
        fake().answer(new FakeCrystallon.Reply(200, "text/html", "<html>a proxy page</html>".getBytes(), java.util.Map.of()));
        post(REQUEST, TOKEN).then().statusCode(502).body("error", containsString("not a PDF")); // a 2XX that is not a PDF
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
        assertTrue(fake().calls().isEmpty(), "Crystallon is never called with a request this application refuses");
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void errorsAreJsonWhateverTheAcceptHeaderSaysAndTheSuccessIsAPdf() {
        // curl sends "Accept: */*": an error must not be typed as a PDF just because the PDF is the other thing this endpoint produces
        for (String accept : new String[] {"*/*", "application/pdf", "application/json", "application/pdf, application/json"}) {
            given().accept(accept).contentType(ContentType.JSON).body("{\"number\":\"\",\"lines\":[]}").when().post("/api/documents/invoice")
                    .then().statusCode(400).contentType(containsString("application/json")).body("violations", notNullValue());
        }
        fake().answer(FakeCrystallon.Reply.json(422, "{\"code\":\"CRYSTALLON-0015\",\"message\":\"Invalid template\"}"));
        given().accept("*/*").contentType(ContentType.JSON).body(REQUEST).when().post("/api/documents/invoice").then().statusCode(422).contentType(containsString("application/json")).body("error", is("Invalid template"));
        fake().answer(FakeCrystallon.Reply.pdf());
        given().accept("*/*").contentType(ContentType.JSON).body(REQUEST).when().post("/api/documents/invoice").then().statusCode(200).contentType("application/pdf");
    }

    @Test
    void theApiStaysClosedWithoutATokenOrWithoutTheRole() {
        post(REQUEST, null).then().statusCode(401);
        assertTrue(fake().calls().isEmpty());
    }

    @Test
    @TestSecurity(user = "x", roles = {"quelque:chose"})
    void aTokenWithoutTheRoleOfThisApplicationIsRefused() {
        post(REQUEST, TOKEN).then().statusCode(403);
        assertTrue(fake().calls().isEmpty());
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void platformInfoSaysDocumentsAreOn() {
        given().when().get("/api/platform").then().statusCode(200).body("documents", is(true)).body("documentsToken", is("forwarding"));
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read"})
    void theOpenApiContractDescribesTheEndpoint() {
        given().when().get("/q/openapi?format=json").then().statusCode(200).body(containsString("/api/documents/invoice")).body(containsString("application/pdf")).body(containsString("Documents"));
    }

    @Test
    void outsideARequestThereIsNoTokenToForward() {
        assertNull(forwardedToken.get(), "a call made outside an HTTP request (no Authorization header) forwards nothing");
    }
}
