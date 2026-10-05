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
 * Notes in {@code /distributed/std}: durable files, SHARED by all replicas of the application on all nodes (data on the storage nodes,
 * in two copies). The folder exists because the application was deployed with {@code "distributed": ["std"]}.
 *
 * <p><b>The rules of the distributed file system</b> (the ones that were costly to discover; they explain the shape of this class):
 * <ol>
 *   <li><b>Never rewrite a file nor rename a folder that was just written.</b> So we only write NEW files, directly under their final name
 *       (this is why a note is immutable); deleting a file is allowed.</li>
 *   <li><b>A folder listing can lag by about 3 seconds</b> on another replica: a note created on replica A can take a few seconds to
 *       show up on replica B. Do not build logic that assumes otherwise (the client refreshes its list).</li>
 *   <li>A file becomes visible <b>when it is closed</b>: a reader never sees a half-written file, but it still tolerates an unreadable file (ignored here).</li>
 *   <li>No locks between nodes without {@code distributedLocks}: none is needed here, each note has its own unique file (UUID).</li>
 * </ol>
 * File names start with the creation time (milliseconds, 13 digits): alphabetical order is chronological order, and the last 200 are read.
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
            // directly under the final name, never "write a .tmp then rename" (see rule 1)
            Files.writeString(file(id), MAPPER.writeValueAsString(n), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write the note in " + dir, e);
        }
        return n;
    }

    @Override
    public List<Note> list() {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> names = s.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
            List<Note> out = new ArrayList<>();
            for (int i = names.size() - 1; i >= 0 && out.size() < LIMIT; i--) read(names.get(i)).ifPresent(out::add); // most recent first
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + dir, e);
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
            throw new UncheckedIOException("cannot delete", e);
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

    /** An identifier is never a path: no "..", no "/" (protects against access to another file). */
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
            return Optional.empty(); // missing, partial or corrupt: ignored (rule 3)
        }
    }
}
