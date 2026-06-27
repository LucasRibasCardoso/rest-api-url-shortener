package com.app.url_shortener.iam.domain.model;

import com.app.url_shortener.shared.domain.validation.RequiredText;
import java.util.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Role {

  private static final String RESERVED_AUTHORITY_PREFIX = "ROLE_";

  @EqualsAndHashCode.Include private final UUID id;
  private final String name;
  private final boolean isDefault;
  private final Set<Permission> permissions;

  private Role(UUID id, String name, boolean isDefault, Set<Permission> permissions) {
    this.id = Objects.requireNonNull(id, "id is required");
    this.name = canonicalizeName(name);
    this.isDefault = isDefault;
    this.permissions = permissions == null ? new HashSet<>() : new HashSet<>(permissions);
  }

  public static Role create(String name, Set<Permission> permissions) {
    UUID id = UUID.randomUUID();
    boolean isDefault = false;
    return new Role(id, name, isDefault, permissions);
  }

  public static Role restore(UUID id, String name, boolean isDefault, Set<Permission> permissions) {
    return new Role(id, name, isDefault, permissions);
  }

  public Set<Permission> getPermissions() {
    return Collections.unmodifiableSet(permissions);
  }

  private static String canonicalizeName(String name) {
    String canonicalName = RequiredText.normalize(name, "name").toUpperCase(Locale.ROOT);

    if (canonicalName.startsWith(RESERVED_AUTHORITY_PREFIX)) {
      throw new IllegalArgumentException("name must not use the ROLE_ authority prefix");
    }

    return canonicalName;
  }

  @Override
  public String toString() {
    return "Role{"
        + "id="
        + id
        + ", name='"
        + name
        + '\''
        + ", isDefault="
        + isDefault
        + ", permissionsCount="
        + permissions.size()
        + '}';
  }
}
