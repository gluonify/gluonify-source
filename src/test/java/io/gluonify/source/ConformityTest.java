package io.gluonify.source;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Gluonify's standards, checked on your side BEFORE pushing (the Gluonify builder checks them after cloning and rejects the repository otherwise: R-* rules).
 * If you start from this project, keep this test: it saves you a round trip.
 */
class ConformityTest {
    private static String read(String p) throws IOException {
        return Files.readString(Path.of(p));
    }

    @Test
    void rSante_smallryeHealthPresent() throws IOException {
        assertTrue(read("pom.xml").contains("quarkus-smallrye-health"), "R-SANTE : Gluonify only sends traffic to instances whose /q/health/ready answers 200");
    }

    @Test
    void rSecret_aucuneValeurSensibleEnClairDansLaConfiguration() throws IOException {
        Pattern sensitive = Pattern.compile("(?i)^[^#\\s][^=]*(password|secret|token|api-key|apikey|private-key|access-key|\\.key)[^=]*=(.*)$");
        for (String line : read("src/main/resources/application.properties").split("\n")) {
            if (line.startsWith("%dev") || line.startsWith("%test")) continue;
            Matcher m = sensitive.matcher(line.trim());
            if (!m.matches()) continue;
            String value = m.group(2).trim();
            assertTrue(value.isEmpty() || value.startsWith("${"), "R-SECRET : \"" + line + "\": write ${VARIABLE} or ${app.key}, never the value");
        }
    }

    @Test
    void rFichierSensible_aucunSecretDansLeDepot() throws IOException {
        // the root and src/ (excluding tests): not target/ nor node_modules/ nor .git/, which tools modify during the test
        java.util.List<Path> files = new java.util.ArrayList<>();
        try (Stream<Path> root = Files.list(Path.of("."))) {
            root.filter(Files::isRegularFile).forEach(files::add);
        }
        try (Stream<Path> src = Files.walk(Path.of("src/main"))) {
            src.filter(Files::isRegularFile).filter(p -> !p.toString().contains("node_modules") && !p.toString().contains("/dist/")).forEach(files::add);
        }
        for (Path p : files) {
            String n = p.getFileName().toString();
            assertFalse(n.equals(".env") || n.endsWith(".pem") || n.endsWith(".p12") || n.endsWith(".jks") || n.equals("id_rsa") || n.equals("id_ed25519"), "R-FICHIER-SENSIBLE : " + p);
        }
    }

    @Test
    void rNatif_etAutresNormes() throws IOException {
        String props = read("src/main/resources/application.properties");
        String pom = read("pom.xml");
        assertFalse(props.matches("(?s).*quarkus\\.native\\.enabled\\s*=\\s*false.*"), "R-NATIF : native compilation must not be disabled");
        assertFalse(pom.contains("quarkus-container-image"), "R-IMAGE : Gluonify builds the image; no quarkus-container-image extension");
        assertFalse(props.matches("(?s).*quarkus\\.http\\.host\\s*=\\s*(localhost|127\\.0\\.0\\.1|\\[?::1\\]?)\\s*(\\n.*)?"), "R-ECOUTE : the application must listen on 0.0.0.0, not on the loopback");
    }
}
