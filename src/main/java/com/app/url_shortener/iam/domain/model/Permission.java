package com.app.url_shortener.iam.domain.model;

import java.util.Objects;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Permission {

  @EqualsAndHashCode.Include private final UUID id;
  private final String name;
  private final String description;

  private Permission(UUID id, String name, String description) {
    this.id = Objects.requireNonNull(id, "id is required");
    this.name = Objects.requireNonNull(name, "name is required");
    this.description = normalizeDescription(description);
  }

  public static Permission create(String name, String description) {
    UUID id = UUID.randomUUID();
    return new Permission(id, name, description);
  }

  public static Permission restore(UUID id, String name, String description) {
    return new Permission(id, name, description);
  }

  private static String normalizeDescription(String description) {
    if (description == null) {
      return null;
    }

    String normalizedDescription = description.trim();
    return normalizedDescription.isBlank() ? null : normalizedDescription;
  }

  @Override
  public String toString() {
    return "Permission{"
        + "id="
        + id
        + ", name='"
        + name
        + '\''
        + ", description='"
        + description
        + '\''
        + '}';
  }
}
