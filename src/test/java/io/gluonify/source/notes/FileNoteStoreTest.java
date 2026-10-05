package io.gluonify.source.notes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Le stockage par fichiers (celui de /distributed/std) : fichiers neufs, ordre chronologique, tolérance aux fichiers illisibles, identifiants sûrs. */
class FileNoteStoreTest {
    @TempDir
    Path dir;

    @Test
    void creerListerLireSupprimer() throws Exception {
        FileNoteStore s = new FileNoteStore(dir.resolve("notes"));
        Note a = s.create(new NewNote("A", "premier"), "ada");
        Thread.sleep(3);
        Note b = s.create(new NewNote("B", null), "bob");
        assertEquals(List.of(b.id(), a.id()), s.list().stream().map(Note::id).toList(), "les plus récentes d'abord");
        assertEquals("premier", s.get(a.id()).orElseThrow().body());
        assertEquals("", s.get(b.id()).orElseThrow().body(), "un corps absent devient vide");
        assertTrue(s.delete(a.id()));
        assertFalse(s.delete(a.id()));
        assertEquals(1, s.list().size());
    }

    @Test
    void uneAutreReplicaVoitLesNotes() {
        // deux instances sur le même dossier = deux répliques de l'application sur /distributed/std
        Path notes = dir.resolve("partage");
        FileNoteStore r1 = new FileNoteStore(notes), r2 = new FileNoteStore(notes);
        Note n = r1.create(new NewNote("vue partout", "x"), "ada");
        assertEquals(n.id(), r2.list().get(0).id());
        assertTrue(r2.delete(n.id()));
        assertTrue(r1.list().isEmpty());
    }

    @Test
    void chaqueNoteEstUnNouveauFichierJamaisRenomme() throws Exception {
        FileNoteStore s = new FileNoteStore(dir);
        s.create(new NewNote("A", "x"), "ada");
        s.create(new NewNote("B", "y"), "ada");
        try (var files = Files.list(dir)) {
            List<String> names = files.map(p -> p.getFileName().toString()).toList();
            assertEquals(2, names.size());
            assertTrue(names.stream().allMatch(n -> n.matches("[0-9]{13}-[0-9a-f-]{36}\\.json")), names.toString());
            assertTrue(names.stream().noneMatch(n -> n.contains("tmp") || n.startsWith(".")), "aucun fichier temporaire à renommer");
        }
    }

    @Test
    void unFichierIllisibleOuPartielEstIgnore() throws Exception {
        FileNoteStore s = new FileNoteStore(dir);
        Note ok = s.create(new NewNote("bonne", "x"), "ada");
        Files.writeString(dir.resolve("9999999999999-00000000-0000-0000-0000-000000000000.json"), "{pas du json");
        Files.writeString(dir.resolve("9999999999998-00000000-0000-0000-0000-000000000001.json"), "{\"id\":\"a\"}");
        assertEquals(List.of(ok.id()), s.list().stream().map(Note::id).toList());
    }

    @Test
    void unIdentifiantNEstJamaisUnChemin() throws Exception {
        FileNoteStore s = new FileNoteStore(dir.resolve("notes"));
        Files.writeString(dir.resolve("secret.json"), "{}");
        for (String evil : new String[] {"../secret", "..%2Fsecret", "/etc/passwd", "", "x", "0000000000000-../../secret"}) {
            assertTrue(s.get(evil).isEmpty(), evil);
            assertFalse(s.delete(evil), evil);
        }
        assertTrue(Files.exists(dir.resolve("secret.json")));
    }

    @Test
    void pretSiLeDossierEstUtilisable() throws Exception {
        assertTrue(new FileNoteStore(dir.resolve("a/b/c")).ready(), "le dossier est créé");
        Path file = dir.resolve("fichier");
        Files.writeString(file, "x");
        assertFalse(new FileNoteStore(file.resolve("sous")).ready(), "sous un fichier : impossible");
    }
}
