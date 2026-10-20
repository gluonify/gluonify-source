package io.gluonify.source.documents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.gluonify.plasma.crystallon.client.CrystallonClient;
import io.gluonify.plasma.crystallon.client.CrystallonException;
import io.gluonify.plasma.services.GluonifyServicesException;
import io.gluonify.source.documents.generated.GluonifyInvoiceDocument;
import io.gluonify.source.documents.generated.GluonifyInvoicePayload;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Edge cases of the documents package: mapping details, error mapping without a message, the SDK failure mapper, the generated client's validation calls. */
@QuarkusTest
@QuarkusTestResource(value = GluonifyTestServicesResource.class, restrictToAnnotatedClass = true)
class DocumentsEdgesTest {
    @Inject
    Instance<CrystallonClient> crystallon;

    private static InvoiceRequest request(final String number, final String unit) {
        final var customer = new InvoiceRequest.Customer("Example SA", "Rue du Lac", "7", "1003", "Lausanne", "CH");
        final var line = new InvoiceRequest.Line("Hosting", BigDecimal.ONE, unit, BigDecimal.TEN);
        return new InvoiceRequest(number, null, null, "Subject", customer, List.of(line), null, null);
    }

    @Test
    void aBlankUnitFallsBackToPieces() {
        assertEquals("pce", InvoiceMapping.payload(request("1", " "), LocalDate.of(2026, 10, 9)).items().get(0).unit());
        assertEquals("h", InvoiceMapping.payload(request("1", "h"), LocalDate.of(2026, 10, 9)).items().get(0).unit());
    }

    @Test
    void aLongInvoiceNumberGivesABoundedFileName() {
        assertEquals("invoice-" + "A".repeat(40) + ".pdf", InvoiceMapping.fileName(request("A".repeat(60), "h")));
    }

    @Test
    void aTokenFailureWithoutMessageIsAnUnreachableCrystallon() {
        assertEquals(502, DocumentErrors.toResponse(new CrystallonException(0, null, null, null)).getStatus());
    }

    @Test
    void aSdkFailureBecomes502WithItsMessage() {
        final var response = new DocumentsResource(new Documents(crystallon)).servicesFailed(new GluonifyServicesException("developer session expired"));
        assertEquals(502, response.getStatus());
        assertTrue(response.getEntity().toString().contains("developer session expired"));
    }

    @Test
    void theGeneratedClientForwardsItsValidationCalls() {
        final var document = new GluonifyInvoiceDocument(crystallon.get());
        final var payload = InvoiceMapping.payload(request("1", "h"), LocalDate.of(2026, 10, 9));
        assertNotNull(document.validate(payload));
        assertNotNull(document.validate(payload, io.gluonify.plasma.crystallon.client.RenderOptions.DEFAULT));
        assertEquals(null, new GluonifyInvoicePayload.Payment(null, null, null, null).iban());
    }
}
