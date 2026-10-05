package io.gluonify.source;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.Optional;

/**
 * The application's own configuration, read from application.properties under the {@code source.} prefix.
 * Each value can be overridden by an environment variable (SOURCE_STORE, SOURCE_FILES_DIR...): this is how the platform (and its vault, Top) configures the application.
 */
@ConfigMapping(prefix = "source")
public interface SourceConfig {
    /** memory (default, development), files (/distributed/std) or graph (Gdown). */
    @WithDefault("memory")
    String store();

    Files files();

    Graph graph();

    Webhook webhook();

    interface Files {
        @WithDefault("/distributed/std/notes")
        String dir();
    }

    interface Graph {
        /** Provided by the platform: SERVICE_GRAPHDB_URL (application deployed with "uses": ["graphdb"]). */
        Optional<String> url();

        @WithDefault("source")
        String database();

        Optional<String> user();

        /** Comes from the vault (key GRAPH_PASSWORD of the <uuid>.app space obtained with "vault": true -> variable APP_GRAPH_PASSWORD; no prefix with a literal vaultNamespace). Never in the repository. */
        Optional<String> password();
    }

    interface Webhook {
        /** Key that Photon sends in the X-Api-Key header (configured in the webhook target). Absent: the receiver is closed (404). */
        Optional<String> key();
    }
}
