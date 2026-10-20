package io.gluonify.source.notes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.core.SecurityContext;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Edge cases of the stores and of the resource: absent body or author, unusable folders, unreadable entries. */
class NoteStoresEdgesTest {
    @TempDir
    Path dir;

    @Test
    void aMissingBodyBecomesEmptyInMemory() {
        assertEquals("", new MemoryNoteStore().create(new NewNote("T", null), "ada").body());
    }

    @Test
    void anonymousCreationHasAnEmptyAuthor() {
        final SecurityContext anonymous = (SecurityContext) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {SecurityContext.class}, (p, m, a) -> null);
        final var resource = new NotesResource(new MemoryNoteStore());
        final var uri = (jakarta.ws.rs.core.UriInfo) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {jakarta.ws.rs.core.UriInfo.class},
                (p, m, a) -> jakarta.ws.rs.core.UriBuilder.fromUri("http://localhost/api/notes"));
        assertEquals("", ((Note) resource.create(new NewNote("T", "b"), anonymous, uri).getEntity()).author());
    }

    @Test
    void theListingIsBoundedToTheLast200Notes() throws Exception {
        for (int i = 0; i < 205; i++) {
            Files.writeString(dir.resolve(String.format("%013d-00000000-0000-0000-0000-%012d.json", i, i)),
                    "{\"id\":\"n" + i + "\",\"title\":\"t\",\"createdAt\":\"2026-10-05T10:00:00Z\"}");
        }
        final var notes = new FileNoteStore(dir).list();
        assertEquals(200, notes.size());
        assertEquals("n204", notes.get(0).id(), "most recent first");
    }

    @Test
    void theFileStoreNamesItself() {
        assertEquals("files", new FileNoteStore(dir).kind());
    }

    @Test
    void creatingUnderARegularFileFails() throws Exception {
        final Path file = Files.writeString(dir.resolve("file"), "x");
        assertThrows(UncheckedIOException.class, () -> new FileNoteStore(file.resolve("notes")).create(new NewNote("T", "b"), "ada"));
    }

    @Test
    void listingAMissingOrNonFolderPathIsEmpty() throws Exception {
        assertTrue(new FileNoteStore(dir.resolve("absent")).list().isEmpty());
        assertTrue(new FileNoteStore(Files.writeString(dir.resolve("file"), "x")).list().isEmpty());
    }

    @Test
    void anUnreadableFolderFailsTheListing() throws Exception {
        final Path locked = Files.createDirectory(dir.resolve("locked"), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("---------")));
        try {
            org.junit.jupiter.api.Assumptions.assumeFalse(Files.isReadable(locked), "running as root: permissions do not apply");
            assertThrows(UncheckedIOException.class, () -> new FileNoteStore(locked).list());
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void aReadOnlyFolderIsNotReady() throws Exception {
        final Path ro = Files.createDirectory(dir.resolve("ro"), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("r-x------")));
        try {
            org.junit.jupiter.api.Assumptions.assumeFalse(Files.isWritable(ro), "running as root: permissions do not apply");
            assertFalse(new FileNoteStore(ro).ready());
        } finally {
            Files.setPosixFilePermissions(ro, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void anEntryThatCannotBeDeletedFails() throws Exception {
        final String id = "1234567890123-00000000-0000-0000-0000-000000000000";
        final Path notEmpty = Files.createDirectories(dir.resolve(id + ".json"));
        Files.writeString(notEmpty.resolve("inside"), "x");
        assertThrows(UncheckedIOException.class, () -> new FileNoteStore(dir).delete(id));
    }

    @Test
    void aNullIdentifierIsNeverSafe() {
        assertFalse(FileNoteStore.safe(null));
    }
}
