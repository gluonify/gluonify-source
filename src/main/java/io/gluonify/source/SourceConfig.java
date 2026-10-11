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
    /** memory (default, development), files (/distributed/std) or gdown (Gdown). */
    @WithDefault("memory")
    String store();

    Files files();

    Graph graph();

    Webhook webhook();

    Crystallon crystallon();

    interface Files {
        @WithDefault("/distributed/std/notes")
        String dir();
    }

    interface Graph {
        /** Provided by the platform: GLUONIFY_SERVICE_GDOWN_URL (application deployed with "uses": ["gdown"]). */
        Optional<String> url();

        @WithDefault("source")
        String database();

        Optional<String> user();

        /** Comes from the vault (key GLUONIFY_GDOWN_PASSWORD of the <uuid>.app space obtained with "top": true -> variable APP_GLUONIFY_GDOWN_PASSWORD; no prefix with a literal topNamespace). Never in the repository. */
        Optional<String> password();
    }

    interface Webhook {
        /** Key that Photon sends in the X-Api-Key header (configured in the webhook target). Absent: the receiver is closed (404). */
        Optional<String> key();
    }

    interface Crystallon {
        /**
         * Address of Crystallon (PDF documents), provided by the platform as GLUONIFY_SERVICE_CRYSTALLON_URL when the application is deployed with "uses": ["crystallon"].
         * Absent or blank: the documents feature is off ({@code POST /api/documents/invoice} answers 404) and the rest of the application is not affected.
         */
        Optional<String> url();

        /**
         * Which token Crystallon receives: {@code forwarding} (the default when absent or blank; the bearer token of the person calling this application, so Crystallon sees
         * that person) or {@code platform} (the application's own token, provided by the platform; works outside a request too). A mode, never a token: see {@code TokenMode}.
         * Optional, not {@code @WithDefault}: application.properties maps it to {@code ${SOURCE_CRYSTALLON_TOKEN:}}, and SmallRye treats that empty value as "no default applies".
         */
        Optional<String> token();

        /**
         * The file of the application's own token, for {@code token=platform}: {@code GLUONIFY_CRYSTALLON_TOKEN_FILE}, provided and renewed by the platform when the application is
         * deployed with {@code "uses": ["crystallon"]}. Read again at every call, so a renewal is taken into account. Absent while {@code token=platform}: the application fails to start.
         */
        Optional<String> tokenFile();
    }
}
