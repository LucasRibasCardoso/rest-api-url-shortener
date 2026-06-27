package com.app.url_shortener.iam.domain.model;

import com.app.url_shortener.shared.domain.validation.RequiredText;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class EmailVerificationToken {

  @EqualsAndHashCode.Include private final UUID id;

  private final UUID userId;
  private final String email;
  private final String hashedCode;
  private final String encryptedCode;
  private final Instant expiresAt;
  private final Instant consumedAt;
  private final Instant revokedAt;
  private final int failedAttempts;
  private final Instant lastAttemptAt;
  private final Instant createdAt;
  private final Instant updatedAt;

  private EmailVerificationToken(
      UUID id,
      UUID userId,
      String email,
      String hashedCode,
      String encryptedCode,
      Instant expiresAt,
      Instant consumedAt,
      Instant revokedAt,
      int failedAttempts,
      Instant lastAttemptAt,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id is required");
    this.userId = Objects.requireNonNull(userId, "userId is required");
    this.email = RequiredText.normalize(email, "email");
    this.hashedCode = RequiredText.normalize(hashedCode, "verificationCodeHash");
    this.encryptedCode = RequiredText.normalize(encryptedCode, "encryptedCode");
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt is required");
    this.consumedAt = consumedAt;
    this.revokedAt = revokedAt;
    this.failedAttempts = failedAttempts;
    this.lastAttemptAt = lastAttemptAt;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");

    validateAttempts();
    validateTimestamps();
    validateStateConsistency();
  }

  public static EmailVerificationToken create(
      UUID userId,
      String email,
      String verificationCodeHash,
      String encryptedCode,
      Instant expiresAt,
      Instant now) {
    Instant timestamp = Objects.requireNonNull(now, "now is required");

    return new EmailVerificationToken(
        UUID.randomUUID(),
        userId,
        email,
        verificationCodeHash,
        encryptedCode,
        expiresAt,
        null,
        null,
        0,
        null,
        timestamp,
        timestamp);
  }

  public static EmailVerificationToken restore(
      UUID id,
      UUID userId,
      String email,
      String verificationCodeHash,
      String encryptedCode,
      Instant expiresAt,
      Instant consumedAt,
      Instant revokedAt,
      int failedAttempts,
      Instant lastAttemptAt,
      Instant createdAt,
      Instant updatedAt) {
    return new EmailVerificationToken(
        id,
        userId,
        email,
        verificationCodeHash,
        encryptedCode,
        expiresAt,
        consumedAt,
        revokedAt,
        failedAttempts,
        lastAttemptAt,
        createdAt,
        updatedAt);
  }

  public boolean isConsumed() {
    return consumedAt != null;
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }

  public boolean isExpired(Instant now) {
    Objects.requireNonNull(now, "now is required");
    return !expiresAt.isAfter(now);
  }

  public boolean isActive(Instant now) {
    return !isConsumed() && !isRevoked() && !isExpired(now);
  }

  private void validateAttempts() {
    if (failedAttempts < 0) {
      throw new IllegalArgumentException("failedAttempts must not be negative");
    }
  }

  private void validateTimestamps() {
    if (updatedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("updatedAt must not be before createdAt");
    }

    if (!expiresAt.isAfter(createdAt)) {
      throw new IllegalArgumentException("expiresAt must be after createdAt");
    }

    if (consumedAt != null && consumedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("consumedAt must not be before createdAt");
    }

    if (revokedAt != null && revokedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("revokedAt must not be before createdAt");
    }

    if (lastAttemptAt != null && lastAttemptAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("lastAttemptAt must not be before createdAt");
    }

    if (consumedAt != null && consumedAt.isAfter(expiresAt)) {
      throw new IllegalArgumentException("consumedAt must not be after expiresAt");
    }
  }

  private void validateStateConsistency() {
    if (consumedAt != null && revokedAt != null) {
      throw new IllegalArgumentException(
          "Email verification token cannot be both consumed and revoked");
    }

    if (failedAttempts == 0 && lastAttemptAt != null) {
      throw new IllegalArgumentException(
          "Email verification token without failed attempts cannot have lastAttemptAt");
    }

    if (failedAttempts > 0 && lastAttemptAt == null) {
      throw new IllegalArgumentException(
          "Email verification token with failed attempts requires lastAttemptAt");
    }
  }

  @Override
  public String toString() {
    return "EmailVerificationToken{"
        + "id="
        + id
        + ", userId="
        + userId
        + ", email='"
        + email
        + '\''
        + ", expiresAt="
        + expiresAt
        + ", consumedAt="
        + consumedAt
        + ", revokedAt="
        + revokedAt
        + ", failedAttempts="
        + failedAttempts
        + ", lastAttemptAt="
        + lastAttemptAt
        + ", createdAt="
        + createdAt
        + ", updatedAt="
        + updatedAt
        + '}';
  }
}
