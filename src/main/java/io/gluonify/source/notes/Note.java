package io.gluonify.source.notes;

import java.time.Instant;

/**
 * Une note : l'objet métier de l'exemple. Un {@code record} Java est le plus simple : Jackson le (dé)sérialise, OpenAPI le décrit.
 * Une note est IMMUABLE (pas de mise à jour) : c'est volontaire, voir {@link FileNoteStore} (écrire de nouveaux fichiers plutôt que réécrire).
 */
public record Note(String id, String title, String body, Instant createdAt, String author) {}
