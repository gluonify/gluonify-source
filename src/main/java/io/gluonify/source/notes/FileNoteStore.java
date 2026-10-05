package io.gluonify.source.notes;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Notes dans {@code /distributed/std} : des fichiers durables, PARTAGÉS par toutes les répliques de l'application sur tous les nœuds (données sur les nœuds de stockage,
 * en deux copies). Le dossier existe parce que l'application a été déployée avec {@code "distributed": ["std"]}.
 *
 * <p><b>Les règles du système de fichiers distribué</b> (celles qui ont coûté cher à découvrir ; elles expliquent la forme de cette classe) :
 * <ol>
 *   <li><b>Ne jamais réécrire un fichier ni renommer un dossier qui vient d'être écrit.</b> On n'écrit donc que de NOUVEAUX fichiers, directement sous leur nom définitif
 *       (c'est pourquoi une note est immuable) ; supprimer un fichier est permis.</li>
 *   <li><b>La liste d'un dossier peut être en retard d'environ 3 secondes</b> sur une autre réplique : une note créée sur la réplique A peut mettre quelques secondes à
 *       apparaître sur la réplique B. Ne bâtissez pas de logique qui suppose le contraire (le client rafraîchit sa liste).</li>
 *   <li>Un fichier devient visible <b>à sa fermeture</b> : un lecteur ne voit jamais un fichier à moitié écrit, mais il tolère quand même un fichier illisible (ignoré ici).</li>
 *   <li>Pas de verrous entre nœuds sans {@code distributedLocks} : ici aucun n'est nécessaire, chaque note a son fichier unique (UUID).</li>
 * </ol>
 * Les noms de fichiers commencent par l'heure de création (millisecondes, 13 chiffres) : l'ordre alphabétique est l'ordre chronologique, on lit les 200 derniers.
 */
public class FileNoteStore implements NoteStore {
    private static final int LIMIT = 200;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path dir;

    public FileNoteStore(Path dir) {
        this.dir = dir;
    }

    @Override
    public String kind() {
        return "files";
    }

    @Override
    public Note create(NewNote in, String author) {
        Instant now = Instant.now();
        String id = String.format("%013d-%s", now.toEpochMilli(), UUID.randomUUID());
        Note n = new Note(id, in.title(), in.body() == null ? "" : in.body(), now, author);
        try {
            Files.createDirectories(dir);
            // directement sous le nom définitif, jamais « écrire un .tmp puis renommer » (voir règle 1)
            Files.writeString(file(id), MAPPER.writeValueAsString(n), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("écriture de la note impossible dans " + dir, e);
        }
        return n;
    }

    @Override
    public List<Note> list() {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> names = s.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
            List<Note> out = new ArrayList<>();
            for (int i = names.size() - 1; i >= 0 && out.size() < LIMIT; i--) read(names.get(i)).ifPresent(out::add); // les plus récentes d'abord
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException("lecture de " + dir + " impossible", e);
        }
    }

    @Override
    public Optional<Note> get(String id) {
        return safe(id) ? read(file(id)) : Optional.empty();
    }

    @Override
    public boolean delete(String id) {
        if (!safe(id)) return false;
        try {
            return Files.deleteIfExists(file(id));
        } catch (IOException e) {
            throw new UncheckedIOException("suppression impossible", e);
        }
    }

    @Override
    public boolean ready() {
        try {
            Files.createDirectories(dir);
            return Files.isDirectory(dir) && Files.isWritable(dir);
        } catch (IOException e) {
            return false;
        }
    }

    /** Un identifiant n'est jamais un chemin : ni « .. », ni « / » (protège contre l'accès à un autre fichier). */
    static boolean safe(String id) {
        return id != null && id.matches("[0-9]{13}-[0-9a-fA-F-]{36}");
    }

    private Path file(String id) {
        return dir.resolve(id + ".json");
    }

    private Optional<Note> read(Path p) {
        try {
            JsonNode j = MAPPER.readTree(Files.readString(p, StandardCharsets.UTF_8));
            return Optional.of(new Note(j.get("id").asString(), j.get("title").asString(), j.path("body").asString(""), Instant.parse(j.get("createdAt").asString()), j.path("author").asString("")));
        } catch (IOException | RuntimeException e) {
            return Optional.empty(); // absent, partiel ou corrompu : ignoré (règle 3)
        }
    }
}
