package com.app.url_shortener.iam.infrastructure.persistence.entity;

import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
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
@Table(name = "users")
public class UserEntity {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, columnDefinition = "citext", length = 120)
  private String email;

  @Column(name = "password_hash", nullable = false, columnDefinition = "TEXT")
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PlanType plan;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private UserStatus status;

  @Column(name = "email_verified", nullable = false)
  private boolean emailVerified;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by")
  private UserEntity createdBy;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "updated_by")
  private UserEntity updatedBy;

  @Getter(AccessLevel.NONE)
  @ManyToMany(fetch = FetchType.LAZY)
  @Column(nullable = false, length = 120)
  @JoinTable(
          name = "user_roles",
          joinColumns = @JoinColumn(name = "user_id"),
          inverseJoinColumns = @JoinColumn(name = "role_id"))
  private Set<RoleEntity> roles = new HashSet<>();

  public UserEntity() {
  }

  public UserEntity(
          UUID id,
          String name,
          String email,
          String passwordHash,
          UserStatus status,
          PlanType plan,
          boolean emailVerified,
          UserEntity createdBy,
          UserEntity updatedBy,
          Set<RoleEntity> roles) {
    this.id = id;
    this.name = name;
    this.email = email;
    this.passwordHash = passwordHash;
    this.plan = plan;
    this.status = status;
    this.emailVerified = emailVerified;
    this.createdBy = createdBy;
    this.updatedBy = updatedBy;
    this.roles = roles == null ? new HashSet<>() : new HashSet<>(roles);
  }

  public Set<RoleEntity> getRoles() {
    if (roles == null) {
      return Collections.emptySet();
    }

    return Collections.unmodifiableSet(roles);
  }
}
