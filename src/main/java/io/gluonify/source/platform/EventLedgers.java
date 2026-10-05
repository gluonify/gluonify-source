package io.gluonify.source.platform;

import io.gluonify.source.SourceConfig;
import io.gluonify.source.notes.NoteStores;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.nio.file.Path;

/** Chooses the {@link EventLedger} according to {@code source.store}, like {@code NoteStores} (same storage, so same sharing between replicas). */
public class EventLedgers {
    @Produces
    @ApplicationScoped
    EventLedger eventLedger(SourceConfig cfg) {
        return switch (cfg.store()) {
            case "files" -> new FileEventLedger(Path.of(cfg.files().dir()).resolveSibling("events"));
            case "graph" -> new GraphEventLedger(NoteStores.graph(cfg));
            default -> new MemoryEventLedger();
        };
    }
}
