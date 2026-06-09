package com.app.url_shortener.iam.domain.model;

import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.domain.exception.user.UserAccountDisabledException;
import com.app.url_shortener.iam.domain.exception.user.UserAccountLockedException;
import com.app.url_shortener.shared.domain.validation.RequiredText;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UserAccount {

  @EqualsAndHashCode.Include private final UUID id;
  private final String name;
  private final String email;
  private final String passwordHash;

  private final Set<Role> roles;
  private final PlanType plan;

  private UserStatus status;
  private boolean emailVerified;

  private UserAccount(
      UUID id,
      String name,
      String email,
      String passwordHash,
      UserStatus status,
      PlanType planType,
      boolean emailVerified,
      Set<Role> roles) {
    this.id = Objects.requireNonNull(id, "id is required");
    this.name = RequiredText.normalize(name, "name");
    this.email = RequiredText.normalize(email, "email");
    this.passwordHash = RequiredText.normalize(passwordHash, "passwordHash");
    this.status = Objects.requireNonNull(status, "status is required");
    this.plan = Objects.requireNonNull(planType, "planType is required");
    this.emailVerified = emailVerified;
    this.roles = roles == null ? new HashSet<>() : new HashSet<>(roles);

    validateStatusConsistency();
  }

  public static UserAccount createPendingRegistration(
      String name, String email, String passwordHash) {

    UUID id = UUID.randomUUID();
    UserStatus status = UserStatus.PENDING_EMAIL_VERIFICATION;
    PlanType planType = PlanType.FREE;
    return new UserAccount(id, name, email, passwordHash, status, planType, false, null);
  }

  public static UserAccount restore(
      UUID id,
      String name,
      String email,
      String passwordHash,
      UserStatus status,
      PlanType planType,
      boolean emailVerified,
      Set<Role> roles) {
    return new UserAccount(id, name, email, passwordHash, status, planType, emailVerified, roles);
  }

  public boolean isActive() {
    return status == UserStatus.ACTIVE;
  }

  public boolean isPending() {
    return status == UserStatus.PENDING_EMAIL_VERIFICATION;
  }

  public Set<Role> getRoles() {
    return Collections.unmodifiableSet(roles);
  }

  private void validateStatusConsistency() {
    if (status == UserStatus.ACTIVE && !emailVerified) {
      throw new IllegalArgumentException("Active user account must have a verified email");
    }

    if (status == UserStatus.PENDING_EMAIL_VERIFICATION && emailVerified) {
      throw new IllegalArgumentException("Pending user account must not have a verified email");
    }
  }

  public void verifyEmail(Role defaultRole) {
    Objects.requireNonNull(defaultRole, "defaultRole must not be null");

    if (this.emailVerified && this.status == UserStatus.ACTIVE) {
      return;
    }

    if (this.status == UserStatus.LOCKED) {
      throw new UserAccountLockedException();
    }

    if (this.status == UserStatus.DISABLED) {
      throw new UserAccountDisabledException();
    }

    this.emailVerified = true;
    this.status = UserStatus.ACTIVE;
    this.roles.add(defaultRole);
  }

  @Override
  public String toString() {
    return "UserAccount{"
        + "id="
        + id
        + ", name='"
        + name
        + '\''
        + ", email='"
        + email
        + '\''
        + ", status="
        + status
        + ", plan="
        + plan
        + ", emailVerified="
        + emailVerified
        + ", roles="
        + roles
        + '}';
  }
}
