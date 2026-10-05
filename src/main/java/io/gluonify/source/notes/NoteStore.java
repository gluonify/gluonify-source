package io.gluonify.source.notes;

import java.util.List;
import java.util.Optional;

/**
 * Where notes live. Three implementations, chosen by {@code source.store} (see application.properties):
 * <ul>
 *   <li>{@code memory} : in memory, for local development and tests (lost on restart);</li>
 *   <li>{@code files} : files in {@code /distributed/std}, shared by all replicas and durable;</li>
 *   <li>{@code graph} : in Gdown, the platform's graph database.</li>
 * </ul>
 * To add your own storage: implement this interface, annotate it {@code @ApplicationScoped}, and wire it in {@link NoteStores}.
 */
public interface NoteStore {
    /** Short name shown by /api/platform. */
    String kind();

    Note create(NewNote in, String author);

    /** Most recent first, 200 at most. */
    List<Note> list();

    Optional<Note> get(String id);

    /** @return true if the note existed. */
    boolean delete(String id);

    /** True if the storage is usable (feeds /q/health/ready). */
    boolean ready();
}
