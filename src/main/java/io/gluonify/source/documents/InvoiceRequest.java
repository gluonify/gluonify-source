package io.gluonify.source.documents;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The invoice of THIS application's domain (what the caller of {@code POST /api/documents/invoice} sends). It is deliberately not the payload of the Crystallon template:
 * {@link InvoiceMapping} turns it into the generated {@code GluonifyInvoicePayload} (adds the issuer, computes the amounts), so the rest of the application never depends on
 * the shape of a template. Validated like any body: 400 with the violations if invalid.
 *
 * @param number   invoice number, as printed
 * @param date     invoice date (ISO 8601); today if absent
 * @param dueDate  payment due date; left out of the document if absent
 * @param subject  subject line; optional
 * @param customer who is invoiced
 * @param lines    the invoiced lines (1 to 100)
 * @param vatRate  VAT in percent (0 to 100); 8.1 (Swiss standard rate) if absent
 * @param lang     language of the document ({@code en}, {@code fr}, {@code es}, {@code it} or {@code de}); the template's language if absent
 */
public record InvoiceRequest(
        @NotBlank @Size(max = 40) String number,
        LocalDate date,
        LocalDate dueDate,
        @Size(max = 120) String subject,
        @NotNull @Valid Customer customer,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid Line> lines,
        @DecimalMin("0") @DecimalMax("100") BigDecimal vatRate,
        @Pattern(regexp = "en|fr|es|it|de") String lang) {

    /** The invoiced party: a postal address (country: ISO 3166 alpha-2 code, for example {@code CH}). */
    public record Customer(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 120) String street,
            @Size(max = 20) String houseNumber,
            @Size(max = 20) String postalCode,
            @Size(max = 120) String city,
            @Pattern(regexp = "[A-Z]{2}") String country) {}

    /** One invoiced line; the amount is computed by the application (quantity x unit price), not sent by the caller. */
    public record Line(
            @NotBlank @Size(max = 200) String description,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @Size(max = 10) String unit,
            @NotNull @DecimalMin("0") BigDecimal unitPrice) {}
}
