package io.gluonify.source.notes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corps d'une création : validé par Hibernate Validator (400 avec la liste des violations si invalide). */
public record NewNote(@NotBlank @Size(max = 120) String title, @Size(max = 4000) String body) {}
