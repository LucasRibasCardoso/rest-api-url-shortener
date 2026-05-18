package com.app.url_shortener.iam.domain.model;

import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.domain.exception.user.UserAccountDisabledException;
import com.app.url_shortener.iam.domain.exception.user.UserAccountLockedException;
import java.util.*;
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
    this.name = Objects.requireNonNull(name, "name is required").trim();
    this.email = Objects.requireNonNull(email, "email is required").trim();
    this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash is required").trim();
    this.status = status;
    this.plan = planType;
    this.emailVerified = emailVerified;
    this.roles = roles == null ? new HashSet<>() : new HashSet<>(roles);
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
