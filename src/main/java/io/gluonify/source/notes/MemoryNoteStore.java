package io.gluonify.source.notes;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory notes: the default choice for development. Each replica has its own notes, and they disappear on shutdown. */
public class MemoryNoteStore implements NoteStore {
    private final ConcurrentHashMap<String, Note> notes = new ConcurrentHashMap<>();

    @Override
    public String kind() {
        return "memory";
    }

    @Override
    public Note create(NewNote in, String author) {
        Note n = new Note(UUID.randomUUID().toString(), in.title(), in.body() == null ? "" : in.body(), Instant.now(), author);
        notes.put(n.id(), n);
        return n;
    }

    @Override
    public List<Note> list() {
        return notes.values().stream().sorted(Comparator.comparing(Note::createdAt).reversed()).limit(200).toList();
    }

    @Override
    public Optional<Note> get(String id) {
        return Optional.ofNullable(notes.get(id));
    }

    @Override
    public boolean delete(String id) {
        return notes.remove(id) != null;
    }

    @Override
    public boolean ready() {
        return true;
    }
}
