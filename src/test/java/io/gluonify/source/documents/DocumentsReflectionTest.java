package io.gluonify.source.documents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.gluonify.source.documents.generated.GluonifyInvoicePayload;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Native image guard (a native-only bug never shows in the JVM tests): the root record and EVERY nested record of the generated payload are registered for reflection.
 * After generating another template, add its records to {@link DocumentsReflection} and to this test.
 */
class DocumentsReflectionTest {
    @Test
    void everyRecordOfTheGeneratedPayloadIsRegistered() {
        Set<Class<?>> registered = new HashSet<>(Arrays.asList(DocumentsReflection.class.getAnnotation(RegisterForReflection.class).targets()));
        assertTrue(registered.contains(GluonifyInvoicePayload.class));
        Set<Class<?>> nested = new HashSet<>(Arrays.asList(GluonifyInvoicePayload.class.getDeclaredClasses()));
        assertFalse(nested.isEmpty());
        for (Class<?> record : nested) {
            assertTrue(registered.contains(record), record.getName() + " is not registered for reflection in DocumentsReflection: the native executable would fail at the first call");
        }
        assertEquals(nested.size() + 1, registered.size(), "nothing registered that the payload does not have");
    }
}
