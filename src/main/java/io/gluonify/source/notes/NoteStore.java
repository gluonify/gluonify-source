package io.gluonify.source.notes;

import java.util.List;
import java.util.Optional;

/**
 * Où vivent les notes. Trois réalisations, choisies par {@code source.store} (voir application.properties) :
 * <ul>
 *   <li>{@code memory} : en mémoire, pour le développement local et les tests (perdues au redémarrage) ;</li>
 *   <li>{@code files} : des fichiers dans {@code /distributed/std}, partagés par toutes les répliques et durables ;</li>
 *   <li>{@code graph} : dans Gdown, la base graphe de la plateforme.</li>
 * </ul>
 * Pour ajouter votre propre stockage : implémentez cette interface, annotez-la {@code @ApplicationScoped}, et branchez-la dans {@link NoteStores}.
 */
public interface NoteStore {
    /** Nom court affiché par /api/platform. */
    String kind();

    Note create(NewNote in, String author);

    /** Les plus récentes d'abord, 200 au plus. */
    List<Note> list();

    Optional<Note> get(String id);

    /** @return vrai si la note existait. */
    boolean delete(String id);

    /** Vrai si le stockage est utilisable (alimente /q/health/ready). */
    boolean ready();
}
