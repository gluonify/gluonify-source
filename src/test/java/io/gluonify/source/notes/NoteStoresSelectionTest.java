package io.gluonify.source.notes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.gluonify.source.TestConfig;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Which store the producer builds for each {@code source.store} value, and the messages when the Gdown settings are incomplete. */
class NoteStoresSelectionTest {
    @TempDir
    Path dir;

    private final NoteStores producer = new NoteStores();

    @Test
    void eachModeBuildsItsStore() {
        assertInstanceOf(MemoryNoteStore.class, producer.noteStore(TestConfig.of(Map.of("store", "memory"))));
        assertInstanceOf(FileNoteStore.class, producer.noteStore(TestConfig.of(Map.of("store", "files", "files.dir", dir.toString()))));
        assertInstanceOf(GraphNoteStore.class, producer.noteStore(TestConfig.of(Map.of("store", "gdown", "graph.url", "http://127.0.0.1:1", "graph.user", "u", "graph.password", "p"))));
    }

    @Test
    void anUnknownModeIsRefusedWithTheListOfTheValidOnes() {
        final var e = assertThrows(IllegalStateException.class, () -> producer.noteStore(TestConfig.of(Map.of("store", "cloud"))));
        assertTrue(e.getMessage().contains("\"cloud\"") && e.getMessage().contains("memory, files or gdown"), e.getMessage());
    }

    @Test
    void theGdownSettingsAreChecked() {
        assertTrue(message(Map.of("graph.url", "  ", "graph.user", "u", "graph.password", "p")).contains("source.graph.url is empty"));
        assertTrue(message(Map.of("graph.user", "u", "graph.password", "p")).contains("source.graph.url is empty"));
        assertTrue(message(Map.of("graph.url", "http://g", "graph.password", "p")).contains("source.graph.user is missing"));
        assertTrue(message(Map.of("graph.url", "http://g", "graph.user", "u")).contains("password is missing"));
        assertEquals("gdown", NoteStores.graph(TestConfig.of(Map.of("graph.url", "http://g", "graph.user", "u", "graph.password", "p"))).kind());
    }

    private static String message(final Map<String, String> graph) {
        return assertThrows(IllegalStateException.class, () -> NoteStores.graph(TestConfig.of(graph))).getMessage();
    }

    @Test
    void theEffectiveStoreReadsTheEnvironmentOnlyThroughTheConfiguration() {
        assertEquals("files", NoteStores.effectiveStore(TestConfig.of(Map.of("store", "files"))));
    }
}
