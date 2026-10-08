package io.gluonify.source.notes;

import io.gluonify.source.SourceConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.nio.file.Path;

/** Chooses the {@link NoteStore} implementation according to {@code source.store}. A single place to modify to add a storage. */
public class NoteStores {
    @Produces
    @ApplicationScoped
    NoteStore noteStore(SourceConfig cfg) {
        return switch (cfg.store()) {
            case "memory" -> new MemoryNoteStore();
            case "files" -> new FileNoteStore(Path.of(cfg.files().dir()));
            case "gdown" -> graph(cfg);
            default -> throw new IllegalStateException("unknown source.store: \"" + cfg.store() + "\" (memory, files or graph)");
        };
    }

    /** The Gdown connection built from the configuration (also used by the webhook ledger in graph mode). */
    public static GraphNoteStore graph(SourceConfig cfg) {
        return new GraphNoteStore(
                    cfg.graph().url().filter(u -> !u.isBlank()).orElseThrow(() -> new IllegalStateException("source.store=graph : source.graph.url is empty (deploy with \"uses\": [\"graphdb\"])")),
                    cfg.graph().database(),
                    cfg.graph().user().orElseThrow(() -> new IllegalStateException("source.store=graph : source.graph.user is missing")),
                    cfg.graph().password().orElseThrow(() -> new IllegalStateException("source.store=graph : password is missing (vault key GLUONIFY_GDOWN_PASSWORD)")));
    }
}
