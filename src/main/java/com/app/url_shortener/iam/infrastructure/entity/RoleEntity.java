package com.app.url_shortener.iam.infrastructure.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "roles")
public class RoleEntity {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(nullable = false, unique = true, length = 50)
  private String name;

  @Column(name = "is_default", nullable = false)
  private boolean isDefault;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Getter(AccessLevel.NONE)
  @ManyToMany(fetch = FetchType.LAZY)
  @Column(nullable = false, length = 120)
  @JoinTable(
      name = "role_permissions",
      joinColumns = @JoinColumn(name = "role_id"),
      inverseJoinColumns = @JoinColumn(name = "permission_id"))
  private Set<PermissionEntity> permissions = new HashSet<>();

  public RoleEntity() {}

  public RoleEntity(UUID id, String name, boolean isDefault, Set<PermissionEntity> permissions) {
    this.id = id;
    this.name = name;
    this.isDefault = isDefault;
    this.permissions = permissions == null ? new HashSet<>() : new HashSet<>(permissions);
  }

  public Set<PermissionEntity> getPermissions() {
    if (permissions == null) {
      return Collections.emptySet();
    }

    return Collections.unmodifiableSet(permissions);
  }
}
