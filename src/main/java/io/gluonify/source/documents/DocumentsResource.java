package io.gluonify.source.documents;

import io.gluonify.plasma.crystallon.client.CrystallonClient;
import io.gluonify.plasma.crystallon.client.CrystallonException;
import io.gluonify.plasma.crystallon.client.RenderedPdf;
import io.gluonify.plasma.services.GluonifyServicesException;
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
import java.time.ZoneId;
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
 *   <li>no configuration: the client comes from {@code gluonify-plasma-quarkus}; when Crystallon is not available (application not deployed with {@code "uses": ["crystallon"]}) the endpoint answers 404
 *       and nothing else is affected ({@link Documents});</li>
 *   <li>Crystallon is called with the application's own token, which the platform provides and renews; a {@link CrystallonException} becomes a clean 4XX/5XX ({@link DocumentErrors}).
 *       To call it as the person who calls this application, see the README (Documents, "Forwarding the caller's token").</li>
 * </ul>
 */
@Path("/api/documents")
@Tag(name = "Documents")
public class DocumentsResource {
    /** The time zone of the invoice template (Europe/Zurich): the date printed when the request has none. */
    private static final ZoneId INVOICE_ZONE = ZoneId.of("Europe/Zurich");
    private static final Logger LOG = Logger.getLogger(DocumentsResource.class);

    private final Documents documents;

    @Inject
    public DocumentsResource(final Documents documents) {
        this.documents = documents;
    }

    @POST
    @Path("invoice")
    @Consumes(MediaType.APPLICATION_JSON)
    // JSON first: with the default "Accept: */*" an error (validation, 404...) must be JSON, not typed as a PDF. The PDF answer sets its own type.
    @Produces({MediaType.APPLICATION_JSON, "application/pdf"})
    @RolesAllowed("source:read")
    @Operation(summary = "Renders an invoice as a PDF (PDF/A-1B) with Crystallon; Crystallon is called with the application's own token "
            + "(role gluonify:crystallon:render), provided by the platform")
    @APIResponse(responseCode = "200", description = "The invoice, PDF/A-1B", content = @Content(mediaType = "application/pdf", schema = @Schema(type = SchemaType.STRING, format = "binary")))
    @APIResponse(responseCode = "404", description = "Documents are off: the application is not deployed with \"uses\": [\"crystallon\"]")
    @APIResponse(responseCode = "422", description = "The template cannot render these data (Crystallon's message, code and path)")
    @APIResponse(responseCode = "502", description = "Crystallon is unreachable or failed, or did not accept the application's own token")
    public Response invoice(@Valid final InvoiceRequest in) {
        Optional<CrystallonClient> client = documents.client();
        if (client.isEmpty()) {
            return Response.status(404).type(MediaType.APPLICATION_JSON).entity(Map.of("error", "documents are off: Crystallon is not available; deploy the application with \"uses\": [\"crystallon\"] (or run gluonify login <zone> on your machine)")).build();
        }
        RenderedPdf pdf = new GluonifyInvoiceDocument(client.get()).render(InvoiceMapping.payload(in, LocalDate.now(INVOICE_ZONE)), InvoiceMapping.options(in));
        return Response.ok(pdf.bytes(), "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"" + InvoiceMapping.fileName(in) + "\"")
                .header("X-Crystallon-Pages", pdf.pages())
                .header("X-Crystallon-Warnings", pdf.warnings())
                .build();
    }

    /** Crystallon failures of this resource only: the other resources keep their own errors. */
    @ServerExceptionMapper
    public Response crystallonFailed(final CrystallonException e) {
        LOG.warnf("Crystallon call failed: status=%d code=%s path=%s: %s", e.status(), e.code(), e.path(), e.getMessage());
        return DocumentErrors.toResponse(e);
    }

    /** Crystallon is available but the SDK cannot get a token or an address (expired developer session...): the message of the SDK says what to do and never holds a token. */
    @ServerExceptionMapper
    public Response servicesFailed(final GluonifyServicesException e) {
        LOG.warnf("Gluonify services: %s", e.getMessage());
        return Response.status(502).type(MediaType.APPLICATION_JSON).entity(Map.of("error", e.getMessage())).build();
    }
}
