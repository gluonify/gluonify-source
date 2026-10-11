package io.gluonify.source.documents;

import io.gluonify.plasma.crystallon.client.CrystallonException;
import io.gluonify.source.documents.generated.GluonifyInvoiceDocument;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a failed Crystallon call becomes for the caller of this application. Same body as every other error of the API ({@code {"error": "..."}}), plus the {@code code}
 * and {@code path} Crystallon gives when it has them (the place of the problem in the template). The Crystallon token never appears in a message.
 *
 * <table>
 *   <caption>Mapping</caption>
 *   <tr><td>Crystallon 401 or 403 (token missing, expired, wrong audience, no {@code render} role)</td><td>403: the caller's token is not enough for Crystallon</td></tr>
 *   <tr><td>400 or 422 (the data cannot be rendered by the template)</td><td>422 with Crystallon's message, {@code code} and {@code path}</td></tr>
 *   <tr><td>404 (no such template in the organization of the token), other 4XX, 5XX, a 2XX that is not a PDF</td><td>502: the problem is on the Crystallon side</td></tr>
 *   <tr><td>no answer: unreachable</td><td>502</td></tr>
 *   <tr><td>no answer: too slow</td><td>504</td></tr>
 * </table>
 */
public final class DocumentErrors {
    private DocumentErrors() {}

    public static Response toResponse(CrystallonException e) {
        int status = e.status();
        if (status == 401 || status == 403) {
            return error(403, "Crystallon did not accept your token (HTTP " + status + "): it must carry the audience gluonify-crystallon and the role gluonify:crystallon:render", null, null);
        }
        if (status == 400 || status == 422) {
            return error(422, e.getMessage(), e.code(), e.path());
        }
        if (status == 404) {
            return error(502, "Crystallon does not know the template \"" + GluonifyInvoiceDocument.TEMPLATE_NAME
                    + "\" for your organization: create it in Crystallon's console (README, Documents)", e.code(), null);
        }
        if (status / 100 == 2) {
            return error(502, "Crystallon answered something that is not a PDF", null, null); // an HTTP proxy page, for instance
        }
        if (status == 0) {
            return e.getCause() instanceof HttpTimeoutException ? error(504, "Crystallon did not answer in time", null, null) : error(502, "Crystallon is unreachable", null, null);
        }
        return error(502, "Crystallon failed (HTTP " + status + ")", e.code(), null);
    }

    private static Response error(int status, String message, String code, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        if (code != null) body.put("code", code);
        if (path != null) body.put("path", path);
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(body).build();
    }
}
