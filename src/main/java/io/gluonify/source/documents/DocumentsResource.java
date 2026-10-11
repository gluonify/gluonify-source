package io.gluonify.source.documents;

import io.gluonify.plasma.crystallon.client.CrystallonClient;
import io.gluonify.plasma.crystallon.client.CrystallonException;
import io.gluonify.plasma.crystallon.client.RenderedPdf;
import io.gluonify.source.documents.generated.GluonifyInvoiceDocument;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * PDF documents with <b>Crystallon</b>: an example of an OPTIONAL integration with a Gluonify service, through the SDK client.
 * <ul>
 *   <li>the request is the application's own {@link InvoiceRequest}; {@link InvoiceMapping} turns it into the generated payload and the generated
 *       {@link GluonifyInvoiceDocument} calls the template by name: no JSON and no HTTP here;</li>
 *   <li>without {@code source.crystallon.url} (application not deployed with {@code "uses": ["crystallon"]}) the endpoint answers 404 and nothing else is affected;</li>
 *   <li>Crystallon is called with the caller's own token ({@link ForwardedToken}); a {@link CrystallonException} becomes a clean 4XX/5XX ({@link DocumentErrors}).</li>
 * </ul>
 */
@Path("/api/documents")
@Tag(name = "Documents")
public class DocumentsResource {
    private static final Logger LOG = Logger.getLogger(DocumentsResource.class);

    @Inject
    Optional<CrystallonClient> crystallon; // empty when Crystallon is not configured

    @POST
    @Path("invoice")
    @Consumes(MediaType.APPLICATION_JSON)
    // JSON first: with the default "Accept: */*" an error (validation, 404...) must be JSON, not typed as a PDF. The PDF answer sets its own type.
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    @RolesAllowed("source:read")
    @Operation(summary = "Renders an invoice as a PDF (PDF/A-1B) with Crystallon; your token is forwarded to Crystallon (audience gluonify-crystallon, role gluonify:crystallon:render)")
    @APIResponse(responseCode = "200", description = "The invoice, PDF/A-1B", content = @Content(mediaType = "application/pdf", schema = @Schema(type = SchemaType.STRING, format = "binary")))
    @APIResponse(responseCode = "403", description = "Crystallon did not accept the token")
    @APIResponse(responseCode = "404", description = "Documents are off: the application is not deployed with \"uses\": [\"crystallon\"]")
    @APIResponse(responseCode = "422", description = "The template cannot render these data (Crystallon's message, code and path)")
    @APIResponse(responseCode = "502", description = "Crystallon is unreachable or failed")
    public Response invoice(@Valid InvoiceRequest in) {
        if (crystallon.isEmpty()) {
            return Response.status(404).type(MediaType.APPLICATION_JSON).entity(Map.of("error", "documents are off: deploy the application with \"uses\": [\"crystallon\"] (source.crystallon.url is empty)")).build();
        }
        RenderedPdf pdf = new GluonifyInvoiceDocument(crystallon.get()).render(InvoiceMapping.payload(in, LocalDate.now()), InvoiceMapping.options(in));
        return Response.ok(pdf.bytes(), "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"" + InvoiceMapping.fileName(in) + "\"")
                .header("X-Crystallon-Pages", pdf.pages())
                .header("X-Crystallon-Warnings", pdf.warnings())
                .build();
    }

    /** Crystallon failures of this resource only: the other resources keep their own errors. */
    @ServerExceptionMapper
    public Response crystallonFailed(CrystallonException e) {
        LOG.warnf("Crystallon call failed: status=%d code=%s path=%s: %s", e.status(), e.code(), e.path(), e.getMessage());
        return DocumentErrors.toResponse(e);
    }
}
