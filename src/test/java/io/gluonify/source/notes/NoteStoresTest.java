package io.gluonify.source.notes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Store selection: auto follows the dedicated database provided by the platform, an explicit value always wins. */
class NoteStoresTest {
    @Test void autoUsesGdownWhenTheDedicatedDatabaseIsProvided() {
        assertEquals("gdown", NoteStores.effectiveStore("auto", "app-7f3c"));
    }

    @Test void autoFallsBackToMemoryWithoutDedicatedDatabase() {
        assertEquals("memory", NoteStores.effectiveStore("auto", null));
        assertEquals("memory", NoteStores.effectiveStore("auto", " "));
    }

    @Test void explicitStoreWins() {
        assertEquals("files", NoteStores.effectiveStore("files", "app-7f3c"));
        assertEquals("memory", NoteStores.effectiveStore("memory", "app-7f3c"));
        assertEquals("gdown", NoteStores.effectiveStore("gdown", null));
    }
}
