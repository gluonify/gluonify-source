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
 * Les normes de Gluonify, vérifiées chez vous AVANT de pousser (le builder de Gluonify les contrôle après le clone et refuse le dépôt sinon : règles R-*).
 * Si vous partez de ce projet, gardez ce test : il vous évite un aller-retour.
 */
class ConformityTest {
    private static String read(String p) throws IOException {
        return Files.readString(Path.of(p));
    }

    @Test
    void rSante_smallryeHealthPresent() throws IOException {
        assertTrue(read("pom.xml").contains("quarkus-smallrye-health"), "R-SANTE : Gluonify n'envoie du trafic qu'aux instances dont /q/health/ready répond 200");
    }

    @Test
    void rSecret_aucuneValeurSensibleEnClairDansLaConfiguration() throws IOException {
        Pattern sensitive = Pattern.compile("(?i)^[^#\\s][^=]*(password|secret|token|api-key|apikey|private-key|access-key|\\.key)[^=]*=(.*)$");
        for (String line : read("src/main/resources/application.properties").split("\n")) {
            if (line.startsWith("%dev") || line.startsWith("%test")) continue;
            Matcher m = sensitive.matcher(line.trim());
            if (!m.matches()) continue;
            String value = m.group(2).trim();
            assertTrue(value.isEmpty() || value.startsWith("${"), "R-SECRET : « " + line + " » : écrivez ${VARIABLE} ou ${app.clé}, jamais la valeur");
        }
    }

    @Test
    void rFichierSensible_aucunSecretDansLeDepot() throws IOException {
        // la racine et src/ (hors tests) : pas target/ ni node_modules/ ni .git/, que les outils modifient pendant le test
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
        assertFalse(props.matches("(?s).*quarkus\\.native\\.enabled\\s*=\\s*false.*"), "R-NATIF : la compilation native ne doit pas être désactivée");
        assertFalse(pom.contains("quarkus-container-image"), "R-IMAGE : Gluonify fabrique l'image ; pas d'extension quarkus-container-image");
        assertFalse(props.matches("(?s).*quarkus\\.http\\.host\\s*=\\s*(localhost|127\\.0\\.0\\.1|\\[?::1\\]?)\\s*(\\n.*)?"), "R-ECOUTE : l'application doit écouter sur 0.0.0.0, pas sur la boucle locale");
    }
}
