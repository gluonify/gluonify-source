package io.gluonify.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** Swagger UI is read-only documentation in production builds (no Authorize, no Try it out); dev and test keep the full page. */
class SwaggerReadOnlyConfigTest {

    private static Properties load() throws Exception {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            p.load(in);
        }
        return p;
    }

    @Test
    void productionSwaggerUiIsReadOnly() throws Exception {
        Properties p = load();
        assertEquals("true", p.getProperty("quarkus.swagger-ui.always-include"), "the documentation stays readable");
        assertEquals("false", p.getProperty("%prod.quarkus.swagger-ui.try-it-out-enabled"));
        assertEquals("false", p.getProperty("%prod.quarkus.swagger-ui.persist-authorization"));
        String interceptor = p.getProperty("%prod.quarkus.swagger-ui.request-interceptor");
        assertNotNull(interceptor);
        assertTrue(interceptor.contains("req.loadSpec") && interceptor.contains("throw"), "only the document itself is fetched");
        String onComplete = p.getProperty("%prod.quarkus.swagger-ui.on-complete");
        assertNotNull(onComplete);
        assertTrue(onComplete.contains(".auth-wrapper") && onComplete.contains(".try-out"), "Authorize and Try it out are hidden");
    }

    @Test
    void devAndTestKeepTheFullSwaggerUi() throws Exception {
        Properties p = load();
        for (String key : p.stringPropertyNames()) {
            if (key.startsWith("quarkus.swagger-ui.") || key.startsWith("%dev.quarkus.swagger-ui.") || key.startsWith("%test.quarkus.swagger-ui.")) {
                assertFalse(key.endsWith("request-interceptor") || key.endsWith("try-it-out-enabled") || key.endsWith("on-complete"), key);
            }
        }
    }
}
