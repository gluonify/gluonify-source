package io.gluonify.source.notes;

import java.time.Instant;

/**
 * A note: the business object of the example. A Java {@code record} is the simplest: Jackson (de)serializes it, OpenAPI describes it.
 * A note is IMMUTABLE (no update): this is deliberate, see {@link FileNoteStore} (write new files rather than rewrite).
 */
public record Note(String id, String title, String body, Instant createdAt, String author) {}
