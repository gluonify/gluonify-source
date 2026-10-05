package io.gluonify.source;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/** L'API REST des notes : droits (rôles du jeton), validation, cycle de vie complet. Stockage en mémoire (défaut). */
@QuarkusTest
class NotesResourceTest {
    @Test
    void sansJetonL_apiEstFermee() {
        given().when().get("/api/notes").then().statusCode(401);
        given().contentType(ContentType.JSON).body("{\"title\":\"x\"}").when().post("/api/notes").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read", "source:write"})
    void cycleDeVieComplet() {
        String location = given().contentType(ContentType.JSON).body("{\"title\":\"Courses\",\"body\":\"du pain\"}")
                .when().post("/api/notes").then().statusCode(201)
                .body("title", is("Courses")).body("author", is("ada")).body("id", notNullValue()).body("createdAt", notNullValue())
                .extract().header("Location");
        String id = location.substring(location.lastIndexOf('/') + 1);
        given().when().get("/api/notes").then().statusCode(200).body("id", hasItem(id));
        given().when().get(location).then().statusCode(200).body("body", is("du pain"));
        given().when().delete(location).then().statusCode(204);
        given().when().get(location).then().statusCode(404);
        given().when().delete("/api/notes/" + id).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read", "source:write"})
    void laValidationRefuseUnCorpsInvalide() {
        given().contentType(ContentType.JSON).body("{\"title\":\"\"}").when().post("/api/notes").then().statusCode(400);
        given().contentType(ContentType.JSON).body("{\"body\":\"sans titre\"}").when().post("/api/notes").then().statusCode(400);
        given().contentType(ContentType.JSON).body("{\"title\":\"" + "x".repeat(121) + "\"}").when().post("/api/notes").then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "lecteur", roles = {"source:read"})
    void unLecteurNePeutPasEcrire() {
        given().when().get("/api/notes").then().statusCode(200);
        given().contentType(ContentType.JSON).body("{\"title\":\"x\"}").when().post("/api/notes").then().statusCode(403);
        given().when().delete("/api/notes/quelconque").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "autre", roles = {"quelque:chose"})
    void unJetonSansLeBonRoleEstRefuse() {
        given().when().get("/api/notes").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "ada", roles = {"source:read", "source:write"})
    void leContratOpenApiDecritLApi() {
        given().when().get("/q/openapi?format=json").then().statusCode(200).body(containsString("/api/notes")).body(containsString("/hooks/events")).body(containsString("Notes"));
        given().when().get("/q/swagger-ui/").then().statusCode(200);
    }

    @Test
    void santeEtMesuresSontOuvertes() {
        given().when().get("/q/health/ready").then().statusCode(200).body(containsString("stockage des notes")).body(containsString("memory"));
        given().when().get("/q/health/live").then().statusCode(200);
        given().when().get("/q/metrics").then().statusCode(200).body(containsString("jvm_memory"));
        given().when().get("/q/health").then().statusCode(200).header("Content-Type", containsString("json")).body(endsWith("}"));
    }
}
