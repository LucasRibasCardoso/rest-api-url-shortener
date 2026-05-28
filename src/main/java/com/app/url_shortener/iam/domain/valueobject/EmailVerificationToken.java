package com.app.url_shortener.iam.domain.valueobject;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EmailVerificationToken(UUID userId, String email, VerificationCode code, Instant expiresAt) {

  public EmailVerificationToken(UUID userId, String email, VerificationCode code, Instant expiresAt) {
    this.userId = Objects.requireNonNull(userId, "userId is required");
    this.email = Objects.requireNonNull(email, "email is required").trim();
    this.code = Objects.requireNonNull(code, "code is required");
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt is required");
  }

  public static EmailVerificationToken create(
          UUID userId,
          String email,
          VerificationCode code,
          Instant expiresAt) {
    return new EmailVerificationToken(userId, email, code, expiresAt);
  }

  public boolean isExpired() {
    return !expiresAt.isAfter(Instant.now());
  }

  @Override
  public String toString() {
    return "EmailVerificationToken{"
        + "userId="
        + userId
        + ", email='"
        + email
        + '\''
        + ", code='[REDACTED]'"
        + ", expiresAt="
        + expiresAt
        + '}';
  }
}
