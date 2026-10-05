package io.gluonify.source;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.Optional;

/**
 * La configuration propre à l'application, lue dans application.properties sous le préfixe {@code source.}.
 * Chaque valeur peut être remplacée par une variable d'environnement (SOURCE_STORE, SOURCE_FILES_DIR…) : c'est ainsi que la plateforme (et son coffre, Top) configure l'application.
 */
@ConfigMapping(prefix = "source")
public interface SourceConfig {
    /** memory (défaut, développement), files (/distributed/std) ou graph (Gdown). */
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
        /** Fournie par la plateforme : SERVICE_GRAPHDB_URL (application déployée avec « uses »: ["graphdb"]). */
        Optional<String> url();

        @WithDefault("source")
        String database();

        Optional<String> user();

        /** Vient du coffre (clé GRAPH_PASSWORD de l'espace « app » -> variable APP_GRAPH_PASSWORD). Jamais dans le dépôt. */
        Optional<String> password();
    }

    interface Webhook {
        /** Clé que Photon envoie dans l'en-tête X-Api-Key (configurée dans la cible du webhook). Absente : le récepteur est fermé (404). */
        Optional<String> key();
    }
}
