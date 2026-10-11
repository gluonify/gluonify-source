package io.gluonify.source.documents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.gluonify.plasma.crystallon.client.CrystallonClient;
import io.gluonify.plasma.crystallon.client.RenderedPdf;
import io.gluonify.source.documents.generated.GluonifyInvoiceDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

/**
 * The client is built from the settings only; absent or blank address = no client = feature off; a bad address, an unknown token mode or {@code platform} without its file
 * fail with a message that says what to do; the two token modes (the caller's token, the application's own token) present the right token at every call.
 */
class CrystallonClientsTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final LocalDate DAY = LocalDate.of(2026, 10, 11);

    /** The settings of a deployment with only the address: forwarding, no token file. */
    private static Optional<CrystallonClient> forwarding(Optional<String> url, AtomicReference<String> caller) {
        return CrystallonClients.fromSettings(url, null, Optional.empty(), MAPPER, caller::get);
    }

    private static RenderedPdf render(CrystallonClient client) {
        return new GluonifyInvoiceDocument(client).render(InvoiceMapping.payload(InvoiceMappingTest.request(), DAY));
    }

    @Test
    void noAddressMeansNoClient() {
        AtomicReference<String> none = new AtomicReference<>();
        assertTrue(forwarding(Optional.empty(), none).isEmpty());
        assertTrue(forwarding(Optional.of(""), none).isEmpty());
        assertTrue(forwarding(Optional.of("   "), none).isEmpty());
    }

    @Test
    void anAddressThatIsNotHttpFailsWithTheNameOfTheSetting() {
        AtomicReference<String> none = new AtomicReference<>();
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> forwarding(Optional.of("crystallon.internal"), none));
        assertTrue(e.getMessage().contains("source.crystallon.url") && e.getMessage().contains("GLUONIFY_SERVICE_CRYSTALLON_URL"), e.getMessage());
        assertThrows(IllegalStateException.class, () -> forwarding(Optional.of("ftp://10.0.0.1"), none));
    }

    @Test
    void theTokenOfTheCallerIsAskedAtEveryCallAndTheGeneratedFacadeUsesTheClient() {
        try (FakeCrystallon fake = new FakeCrystallon()) {
            AtomicReference<String> token = new AtomicReference<>("first");
            var client = forwarding(Optional.of(" " + fake.url() + "/ "), token).orElseThrow();
            RenderedPdf pdf = render(client);
            assertEquals(2, pdf.pages());
            token.set(null);
            render(client);
            assertEquals("Bearer first", fake.calls().get(0).authorization());
            assertNull(fake.calls().get(1).authorization(), "no token for the second call: no header");
            assertEquals("/v1/templates/by-name/Gluonify%20invoice/render?pdfa=true", fake.calls().get(1).target(), "trailing slash and spaces of the address are ignored");
        }
    }

    // --- the token mode ---

    @Test
    void theModeIsForwardingByDefaultAndIsReadWhateverTheCase() {
        assertEquals(TokenMode.FORWARDING, TokenMode.parse(null));
        assertEquals(TokenMode.FORWARDING, TokenMode.parse(""));
        assertEquals(TokenMode.FORWARDING, TokenMode.parse("  "));
        assertEquals(TokenMode.FORWARDING, TokenMode.parse("forwarding"));
        assertEquals(TokenMode.PLATFORM, TokenMode.parse("platform"));
        assertEquals(TokenMode.PLATFORM, TokenMode.parse(" Platform "));
        assertEquals("platform", TokenMode.PLATFORM.id());
        assertEquals("forwarding", TokenMode.FORWARDING.id());
    }

    @Test
    void anUnknownModeFailsAndNeverRepeatsTheValue() {
        // somebody who took the setting for the token itself must not see the token in the logs
        String token = "eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJhZGEifQ.c2lnbmF0dXJl";
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> TokenMode.parse(token));
        assertTrue(e.getMessage().contains("source.crystallon.token") && e.getMessage().contains("forwarding") && e.getMessage().contains("platform"), e.getMessage());
        assertFalse(e.getMessage().contains("eyJ"), "the value is not part of the message");
        IllegalStateException fromClients = assertThrows(IllegalStateException.class,
                () -> CrystallonClients.fromSettings(Optional.of("http://10.200.0.5:8502"), token, Optional.empty(), MAPPER, () -> null));
        assertFalse(fromClients.getMessage().contains("eyJ"), fromClients.getMessage());
    }

    @Test
    void theModeIsNotLookedAtWhileTheFeatureIsOff() {
        // no address: nothing is built, so neither a mode that does not exist nor a missing file can stop the application
        assertTrue(CrystallonClients.fromSettings(Optional.empty(), "platform", Optional.empty(), MAPPER, () -> null).isEmpty());
        assertTrue(CrystallonClients.fromSettings(Optional.of(" "), "nonsense", Optional.empty(), MAPPER, () -> null).isEmpty());
    }

    @Test
    void platformWithoutItsTokenFileFailsAndSaysWhatToDo() {
        for (Optional<String> file : List.of(Optional.<String>empty(), Optional.of(""), Optional.of("   "))) {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> CrystallonClients.fromSettings(Optional.of("http://10.200.0.5:8502"), "platform", file, MAPPER, () -> null));
            String m = e.getMessage();
            assertTrue(m.contains("source.crystallon.token=platform") && m.contains("GLUONIFY_CRYSTALLON_TOKEN_FILE") && m.contains("\"uses\": [\"crystallon\"]") && m.contains("forwarding"), m);
        }
    }

    @Test
    void platformModeSendsTheTokenOfTheFileAndIgnoresTheCaller(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("crystallon.token");
        Files.writeString(file, "app-token-1\n");
        try (FakeCrystallon fake = new FakeCrystallon()) {
            // the caller is signed in (a token is available) but Crystallon must see the application
            var client = CrystallonClients.fromSettings(Optional.of(fake.url()), "platform", Optional.of(file.toString()), MAPPER, () -> "caller-token").orElseThrow();
            assertEquals(2, render(client).pages());
            assertEquals("Bearer app-token-1", fake.only().authorization(), "the token of the file, without its trailing newline; not the one of the caller");
        }
    }

    @Test
    void platformModeWorksOutsideARequestWhereThereIsNoCallerToken(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("crystallon.token");
        Files.writeString(file, "app-token-1");
        try (FakeCrystallon fake = new FakeCrystallon()) {
            var client = CrystallonClients.fromSettings(Optional.of(fake.url()), "platform", Optional.of(file.toString()), MAPPER, () -> null).orElseThrow();
            render(client);
            assertEquals("Bearer app-token-1", fake.only().authorization());
        }
    }

    @Test
    void platformModeReadsTheFileAgainAtEveryCallSoARenewalIsTakenIntoAccount(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("crystallon.token");
        Files.writeString(file, "app-token-1\n");
        try (FakeCrystallon fake = new FakeCrystallon()) {
            var client = CrystallonClients.fromSettings(Optional.of(fake.url()), "platform", Optional.of(file.toString()), MAPPER, () -> null).orElseThrow();
            render(client);
            // the node agent renews the token: the client was built before, and is the same one
            Files.writeString(file, "app-token-2\n");
            render(client);
            Files.delete(file);
            render(client); // the file is gone (an instance without the token): no header, and Crystallon decides
            assertEquals("Bearer app-token-1", fake.calls().get(0).authorization());
            assertEquals("Bearer app-token-2", fake.calls().get(1).authorization(), "the renewed token is used by the next call");
            assertNull(fake.calls().get(2).authorization());
        }
    }

    @Test
    void forwardingModeIgnoresTheTokenFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("crystallon.token");
        Files.writeString(file, "app-token-1");
        try (FakeCrystallon fake = new FakeCrystallon()) {
            var client = CrystallonClients.fromSettings(Optional.of(fake.url()), "forwarding", Optional.of(file.toString()), MAPPER, () -> "caller-token").orElseThrow();
            render(client);
            assertEquals("Bearer caller-token", fake.only().authorization());
        }
    }
}
