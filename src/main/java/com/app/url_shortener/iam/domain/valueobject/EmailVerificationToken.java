package com.app.url_shortener.iam.domain.valueobject;

import com.app.url_shortener.shared.domain.validation.RequiredText;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EmailVerificationToken(UUID userId, String email, VerificationCode code, Instant expiresAt) {

  public EmailVerificationToken(UUID userId, String email, VerificationCode code, Instant expiresAt) {
    this.userId = Objects.requireNonNull(userId, "userId is required");
    this.email = RequiredText.normalize(email, "email");
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
    return !Instant.now().isBefore(expiresAt);
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
