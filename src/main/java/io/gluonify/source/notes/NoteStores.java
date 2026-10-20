package io.gluonify.source.notes;

import io.gluonify.source.SourceConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.nio.file.Path;

/** Chooses the {@link NoteStore} implementation according to {@code source.store}. A single place to modify to add a storage. */
public class NoteStores {
    /** Variable provided by the platform only for a dedicated database ("gdownDatabase": true, declared or inferred by the builder). */
    public static final String DEDICATED_DATABASE = "GLUONIFY_GDOWN_DATABASE";

    /**
     * The storage actually used: {@code auto} becomes {@code gdown} when the platform provides a dedicated database ({@code dedicatedDatabase} not blank),
     * {@code memory} otherwise; any other value is kept as is.
     */
    public static String effectiveStore(final String store, final String dedicatedDatabase) {
        if (!"auto".equals(store)) return store;
        return dedicatedDatabase != null && !dedicatedDatabase.isBlank() ? "gdown" : "memory";
    }

    /** {@link #effectiveStore} with the process environment. */
    public static String effectiveStore(final SourceConfig cfg) {
        return effectiveStore(cfg.store(), System.getenv(DEDICATED_DATABASE));
    }

    /** The storage modes {@code source.store} can resolve to (once {@code auto} is replaced). */
    private enum StoreKind {
        MEMORY, FILES, GDOWN;

        static StoreKind of(final String store) {
            for (final StoreKind k : values()) {
                if (k.name().toLowerCase(java.util.Locale.ROOT).equals(store)) return k;
            }
            throw new IllegalStateException("unknown source.store: \"" + store + "\" (auto, memory, files or gdown)");
        }
    }

    @Produces
    @ApplicationScoped
    NoteStore noteStore(final SourceConfig cfg) {
        return switch (StoreKind.of(effectiveStore(cfg))) {
            case MEMORY -> new MemoryNoteStore();
            case FILES -> new FileNoteStore(Path.of(cfg.files().dir()));
            case GDOWN -> graph(cfg);
        };
    }

    /** The Gdown connection built from the configuration (also used by the webhook ledger when source.store=gdown). */
    public static GraphNoteStore graph(final SourceConfig cfg) {
        return new GraphNoteStore(
                    cfg.graph().url().filter(u -> !u.isBlank()).orElseThrow(() -> new IllegalStateException("source.store=gdown : source.graph.url is empty (deploy with \"uses\": [\"gdown\"])")),
                    cfg.graph().database(),
                    cfg.graph().user().orElseThrow(() -> new IllegalStateException("source.store=gdown : source.graph.user is missing")),
                    cfg.graph().password().orElseThrow(() -> new IllegalStateException("source.store=gdown : password is missing (vault key GLUONIFY_GDOWN_PASSWORD)")));
    }
}
