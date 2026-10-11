package io.gluonify.source.documents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.gluonify.plasma.crystallon.client.RenderOptions;
import io.gluonify.source.documents.generated.GluonifyInvoicePayload;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The application's invoice becomes the payload of the template: issuer added, amounts computed, defaults applied. */
class InvoiceMappingTest {
    static InvoiceRequest request() {
        return new InvoiceRequest("2026-0042", null, null, "Website relaunch", new InvoiceRequest.Customer("Example Customer SA", "Rue du Lac", "7", "1003", "Lausanne", "CH"),
                List.of(new InvoiceRequest.Line("Consulting", new BigDecimal("12"), "h", new BigDecimal("160.00")), new InvoiceRequest.Line("Hosting", new BigDecimal("0.5"), null, new BigDecimal("33.33"))),
                null, null);
    }

    @Test
    void amountsAreComputedAndDefaultsApplied() {
        GluonifyInvoicePayload p = InvoiceMapping.payload(request(), LocalDate.of(2026, 10, 11));
        assertEquals("Gluonify Source Example Ltd", p.sender().name());
        assertEquals("Example Customer SA", p.recipient().name());
        assertEquals("Lausanne", p.recipient().city());
        assertEquals(LocalDate.of(2026, 10, 11), p.invoice().date(), "today when the request has no date");
        assertNull(p.invoice().dueDate());
        assertEquals("2026-0042", p.invoice().number());
        assertEquals(new BigDecimal("1920.00"), p.items().get(0).amount());
        assertEquals(new BigDecimal("16.67"), p.items().get(1).amount(), "16.665 rounded half up, two decimals");
        assertEquals("pce", p.items().get(1).unit(), "default unit");
        assertEquals(new BigDecimal("8.1"), p.vatRate());
        assertNull(p.payment(), "no payment: no QR-bill");
    }

    @Test
    void theRequestWins() {
        InvoiceRequest r = new InvoiceRequest("N1", LocalDate.of(2026, 1, 2), LocalDate.of(2026, 2, 1), null, request().customer(), request().lines(), new BigDecimal("2.6"), "fr");
        GluonifyInvoicePayload p = InvoiceMapping.payload(r, LocalDate.of(2026, 10, 11));
        assertEquals(LocalDate.of(2026, 1, 2), p.invoice().date());
        assertEquals(LocalDate.of(2026, 2, 1), p.invoice().dueDate());
        assertEquals(new BigDecimal("2.6"), p.vatRate());
        assertEquals("fr", InvoiceMapping.options(r).params().lang());
        assertSame(RenderOptions.DEFAULT, InvoiceMapping.options(request()), "no language asked: the SDK defaults (PDF/A, the template's parameters)");
    }

    @Test
    void theFileNameIsMadeOfSafeCharactersOnly() {
        InvoiceRequest r = new InvoiceRequest("../../etc/passwd \"x\"\r\nSet-Cookie: a", null, null, null, request().customer(), request().lines(), null, null);
        String name = InvoiceMapping.fileName(r);
        assertEquals("invoice-.._.._etc_passwd__x___Set-Cookie__a.pdf", name);
        assertEquals("invoice-2026-0042.pdf", InvoiceMapping.fileName(request()));
        assertEquals("invoice-" + "a".repeat(40) + ".pdf", InvoiceMapping.fileName(new InvoiceRequest("a".repeat(40), null, null, null, request().customer(), request().lines(), null, null)));
    }
}
