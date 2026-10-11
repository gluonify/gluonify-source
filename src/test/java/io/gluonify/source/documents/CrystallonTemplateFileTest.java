package io.gluonify.source.documents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.gluonify.source.documents.generated.GluonifyInvoiceDocument;
import io.gluonify.source.documents.generated.GluonifyInvoicePayload;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Crystallon template of the example is in the repository ({@code crystallon/templates/gluonify-invoice.json}, installed by the builder at deployment) and matches
 * the generated code: same name, and a sample payload with exactly the top-level fields of the generated payload.
 * <p>
 * The fingerprint {@code SCHEMA_SHA256} itself is NOT recomputed here: Crystallon computes it by reading every expression of the template (and of the preset it
 * extends) with its own analyser, which is server code and not part of the SDK. The builder reports the fingerprint of the installed template ({@code templates[].schemaSha256}
 * of the build) and Crystallon refuses to replace a template whose payload would change ({@code differs}); compare that value with
 * {@link GluonifyInvoiceDocument#SCHEMA_SHA256} after a deployment.
 */
class CrystallonTemplateFileTest {
    static final Path FILE = Path.of("crystallon/templates/gluonify-invoice.json");

    @Test
    void theTemplateFileIsTheOneTheGeneratedCodeCalls() throws Exception {
        assertTrue(Files.isRegularFile(FILE), FILE + " is missing: the builder would have no template to install");
        JsonNode t = JsonMapper.builder().build().readTree(Files.readString(FILE));
        assertTrue(t.isObject(), "a template is a JSON object");
        assertEquals(GluonifyInvoiceDocument.TEMPLATE_NAME, t.path("name").asString(), "the exact name at the root is the one the generated code calls");
        assertEquals("Gluonify invoice", t.path("name").asString());
        assertEquals(1, t.path("schema").asInt());
        assertFalse(t.has("extends"), "self-contained: the template does not depend on a preset of the server");
        assertEquals("CHF", t.path("params").path("currency").asString(), "the defaults the facade documents (QR-bill: CHF)");

        List<String> sample = new ArrayList<>(t.path("samplePayload").propertyNames());
        List<String> record = Arrays.stream(GluonifyInvoicePayload.class.getRecordComponents()).map(c -> c.getName()).toList();
        assertEquals(record, sample, "the sample payload of the template and the generated payload have the same fields: regenerate the code after changing the template");
    }
}
