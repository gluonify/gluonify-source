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
    /** auto (default: gdown when the platform provides a dedicated database, GLUONIFY_GDOWN_DATABASE, otherwise memory), memory, files (/distributed/std) or gdown (Gdown). */
    @WithDefault("auto")
    String store();

    Files files();

    Graph graph();

    Webhook webhook();

    Auth auth();

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

    /**
     * Browser sign-in (the UI is a PUBLIC OIDC client of Charm: authorization code + PKCE, no secret). Only non-secret values: they are served to the browser by
     * {@code GET /api/auth-config}. Without a client id the UI cannot sign in (it says so).
     */
    interface Auth {
        /** The Charm client registered for this UI (public, PKCE, redirect = the address of the UI, audience gluonify-source). Variable GLUONIFY_SOURCE_OIDC_CLIENT_ID. */
        Optional<String> clientId();

        /**
         * The PUBLIC address of Charm, the one the browser reaches (https://id.example.com). Absent: derived from the token issuer
         * (https://gluonify.lan/realms/gluonify -> https://gluonify.lan). Never GLUONIFY_SERVICE_CHARM_URL: that one is an internal address.
         */
        Optional<String> charmUrl();

        /** Scopes asked at sign-in (the kit adds offline_access for the refresh token). */
        @WithDefault("openid profile")
        String scope();
    }
}
