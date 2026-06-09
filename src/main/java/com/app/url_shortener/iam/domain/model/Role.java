package com.app.url_shortener.iam.domain.model;

import com.app.url_shortener.shared.domain.validation.RequiredText;
import java.util.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Role {

  @EqualsAndHashCode.Include private final UUID id;
  private final String name;
  private final boolean isDefault;
  private final Set<Permission> permissions;

  private Role(UUID id, String name, boolean isDefault, Set<Permission> permissions) {
    this.id = Objects.requireNonNull(id, "id is required");
    this.name = RequiredText.normalize(name, "name");
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
