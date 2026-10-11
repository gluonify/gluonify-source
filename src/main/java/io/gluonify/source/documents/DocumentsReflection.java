package io.gluonify.source.documents;

import io.gluonify.source.documents.generated.GluonifyInvoicePayload;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Native image: Jackson writes the generated payload records through reflection, and the SDK only declares ITS OWN records. The root record AND EVERY nested record of
 * each generated payload must be listed here, otherwise the native executable fails at the first call ("Record components not available for record class ...") while the JVM
 * tests pass. When you generate another template, add its records here ({@code DocumentsReflectionTest} fails if one is missing).
 */
@RegisterForReflection(targets = {
        GluonifyInvoicePayload.class,
        GluonifyInvoicePayload.Sender.class,
        GluonifyInvoicePayload.Recipient.class,
        GluonifyInvoicePayload.Invoice.class,
        GluonifyInvoicePayload.Item.class,
        GluonifyInvoicePayload.Payment.class})
public final class DocumentsReflection {
    private DocumentsReflection() {}
}
