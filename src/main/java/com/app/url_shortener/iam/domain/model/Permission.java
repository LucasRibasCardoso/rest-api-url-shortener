package com.app.url_shortener.iam.domain.model;

import com.app.url_shortener.shared.domain.validation.RequiredText;
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
    this.name = RequiredText.normalize(name, "name");
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

    return description.trim().isBlank() ? null : description.trim();
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
