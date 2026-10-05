package io.gluonify.source.notes;

import io.gluonify.source.SourceConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.nio.file.Path;

/** Choisit la réalisation de {@link NoteStore} selon {@code source.store}. Un seul endroit à modifier pour ajouter un stockage. */
public class NoteStores {
    @Produces
    @ApplicationScoped
    NoteStore noteStore(SourceConfig cfg) {
        return switch (cfg.store()) {
            case "memory" -> new MemoryNoteStore();
            case "files" -> new FileNoteStore(Path.of(cfg.files().dir()));
            case "graph" -> new GraphNoteStore(
                    cfg.graph().url().filter(u -> !u.isBlank()).orElseThrow(() -> new IllegalStateException("source.store=graph : source.graph.url est vide (déployez avec \"uses\": [\"graphdb\"])")),
                    cfg.graph().database(),
                    cfg.graph().user().orElseThrow(() -> new IllegalStateException("source.store=graph : source.graph.user manquant")),
                    cfg.graph().password().orElseThrow(() -> new IllegalStateException("source.store=graph : mot de passe manquant (clé GRAPH_PASSWORD du coffre)")));
            default -> throw new IllegalStateException("source.store inconnu : « " + cfg.store() + " » (memory, files ou graph)");
        };
    }
}
