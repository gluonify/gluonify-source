package io.gluonify.source.documents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.gluonify.plasma.crystallon.client.RenderedPdf;
import io.gluonify.source.documents.generated.GluonifyInvoiceDocument;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** The client is built from the address only; absent or blank = no client = feature off; a bad address fails with a message that says what to do. */
class CrystallonClientsTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void noAddressMeansNoClient() {
        assertTrue(CrystallonClients.fromUrl(Optional.empty(), MAPPER, () -> null).isEmpty());
        assertTrue(CrystallonClients.fromUrl(Optional.of(""), MAPPER, () -> null).isEmpty());
        assertTrue(CrystallonClients.fromUrl(Optional.of("   "), MAPPER, () -> null).isEmpty());
    }

    @Test
    void anAddressThatIsNotHttpFailsWithTheNameOfTheSetting() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> CrystallonClients.fromUrl(Optional.of("crystallon.internal"), MAPPER, () -> null));
        assertTrue(e.getMessage().contains("source.crystallon.url") && e.getMessage().contains("GLUONIFY_SERVICE_CRYSTALLON_URL"), e.getMessage());
        assertThrows(IllegalStateException.class, () -> CrystallonClients.fromUrl(Optional.of("ftp://10.0.0.1"), MAPPER, () -> null));
    }

    @Test
    void theTokenIsAskedAtEveryCallAndTheGeneratedFacadeUsesTheClient() {
        try (FakeCrystallon fake = new FakeCrystallon()) {
            AtomicReference<String> token = new AtomicReference<>("first");
            var client = CrystallonClients.fromUrl(Optional.of(" " + fake.url() + "/ "), MAPPER, token::get).orElseThrow();
            GluonifyInvoiceDocument invoice = new GluonifyInvoiceDocument(client);
            RenderedPdf pdf = invoice.render(InvoiceMapping.payload(InvoiceMappingTest.request(), java.time.LocalDate.of(2026, 10, 11)));
            assertEquals(2, pdf.pages());
            token.set(null);
            invoice.render(InvoiceMapping.payload(InvoiceMappingTest.request(), java.time.LocalDate.of(2026, 10, 11)));
            assertEquals("Bearer first", fake.calls().get(0).authorization());
            assertNull(fake.calls().get(1).authorization(), "no token for the second call: no header");
            assertEquals("/v1/templates/by-name/Gluonify%20invoice/render?pdfa=true", fake.calls().get(1).target(), "trailing slash and spaces of the address are ignored");
        }
    }
}
