package io.gluonify.source.notes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Creation body: validated by Hibernate Validator (400 with the list of violations if invalid). */
public record NewNote(@NotBlank @Size(max = 120) String title, @Size(max = 4000) String body) {}
