package io.gluonify.source.documents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.gluonify.plasma.crystallon.client.CrystallonException;
import jakarta.ws.rs.core.Response;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Every kind of Crystallon failure and what the caller of this application gets. */
class DocumentErrorsTest {
    private static Response map(int status, String code, String message, String path, Throwable cause) {
        return DocumentErrors.toResponse(new CrystallonException(status, code, message, path, cause));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(Response r) {
        return (Map<String, Object>) r.getEntity();
    }

    @Test
    void tokenProblemsAre403() {
        for (int status : new int[] {401, 403}) {
            Response r = map(status, null, "Crystallon refused the call", null, null);
            assertEquals(403, r.getStatus());
            assertTrue(body(r).get("error").toString().contains("gluonify:crystallon:render"));
            assertTrue(body(r).get("error").toString().contains("HTTP " + status));
        }
    }

    @Test
    void dataProblemsAre422WithCodeAndPath() {
        for (int status : new int[] {400, 422}) {
            Response r = map(status, "CRYSTALLON-0015", "Invalid template", "body[1].text", null);
            assertEquals(422, r.getStatus());
            assertEquals(Map.of("error", "Invalid template", "code", "CRYSTALLON-0015", "path", "body[1].text"), body(r));
        }
        assertFalse(body(map(422, null, "m", null, null)).containsKey("path"), "a missing code or path is left out, not null");
    }

    @Test
    void theOtherFailuresAreOnTheCrystallonSide() {
        assertEquals(502, map(404, "CRYSTALLON-0028", "No such template", null, null).getStatus());
        assertTrue(body(map(404, null, "m", null, null)).get("error").toString().contains("Gluonify invoice"));
        assertEquals(502, map(500, null, "boom", null, null).getStatus());
        assertEquals(502, map(503, null, "unavailable", null, null).getStatus());
        assertEquals(502, map(429, null, "slow down", null, null).getStatus());
        assertEquals(502, map(200, null, "Crystallon did not return a PDF", null, null).getStatus());
    }

    @Test
    void noAnswerIsUnreachableOrTooSlow() {
        assertEquals(502, map(0, null, "Crystallon is unreachable (ConnectException)", null, new ConnectException("refused")).getStatus());
        assertEquals(502, map(0, null, "The call to Crystallon was interrupted", null, null).getStatus());
        Response slow = map(0, null, "Crystallon did not answer within PT30S", null, new HttpTimeoutException("request timed out"));
        assertEquals(504, slow.getStatus());
        assertNull(body(slow).get("code"));
    }

    @Test
    void withTheApplicationsOwnTokenARefusalIsNotTheCallersFault() {
        for (int status : new int[] {401, 403}) {
            Response r = DocumentErrors.toResponse(new CrystallonException(status, null, "Crystallon refused the call", null, null), TokenMode.PLATFORM);
            assertEquals(502, r.getStatus(), "the caller cannot fix the token of the application: not a 403");
            String message = body(r).get("error").toString();
            assertTrue(message.contains("token of the application") && message.contains("\"uses\": [\"crystallon\"]") && message.contains("gluonify:crystallon:render")
                    && message.contains("GLUONIFY_CRYSTALLON_TOKEN_FILE") && message.contains("HTTP " + status), message);
            assertFalse(message.contains("your token"), message);
        }
        // the same status with the caller's token is still the caller's business
        assertEquals(403, DocumentErrors.toResponse(new CrystallonException(401, null, "m", null, null), TokenMode.FORWARDING).getStatus());
    }

    @Test
    void withTheApplicationsOwnTokenTheTemplateIsLookedUpInTheOrganizationOfThatToken() {
        Response r = DocumentErrors.toResponse(new CrystallonException(404, "CRYSTALLON-0028", "No such template", null, null), TokenMode.PLATFORM);
        assertEquals(502, r.getStatus());
        assertTrue(body(r).get("error").toString().contains("organization of the application's token (default)"));
        assertTrue(body(map(404, null, "m", null, null)).get("error").toString().contains("your organization"), "the caller's own organization otherwise");
    }

    @Test
    void anUnreadableTokenFileIsNotReportedAsAnUnreachableCrystallon() {
        CrystallonException unreadable = new CrystallonException(0, null, "cannot read the Crystallon token file /run/gluonify-tokens/crystallon.token (AccessDeniedException)", null, new java.io.IOException("denied"));
        Response r = DocumentErrors.toResponse(unreadable, TokenMode.PLATFORM);
        assertEquals(502, r.getStatus());
        assertTrue(body(r).get("error").toString().contains("GLUONIFY_CRYSTALLON_TOKEN_FILE"));
        assertFalse(body(r).get("error").toString().contains("unreachable"));
    }

    @Test
    void theOtherFailuresDoNotDependOnTheTokenMode() {
        Response forwarding = DocumentErrors.toResponse(new CrystallonException(422, "CRYSTALLON-0015", "Invalid template", "body[1]", null), TokenMode.FORWARDING);
        Response platform = DocumentErrors.toResponse(new CrystallonException(422, "CRYSTALLON-0015", "Invalid template", "body[1]", null), TokenMode.PLATFORM);
        assertEquals(forwarding.getStatus(), platform.getStatus());
        assertEquals(body(forwarding), body(platform));
        assertEquals(502, DocumentErrors.toResponse(new CrystallonException(0, null, "Crystallon is unreachable (ConnectException)", null, new ConnectException("refused")), TokenMode.PLATFORM).getStatus());
        assertEquals(504, DocumentErrors.toResponse(new CrystallonException(0, null, "m", null, new HttpTimeoutException("t")), TokenMode.PLATFORM).getStatus());
    }

    @Test
    void theBodyIsJson() {
        assertEquals("application/json", map(500, null, "x", null, null).getMediaType().toString());
    }
}
