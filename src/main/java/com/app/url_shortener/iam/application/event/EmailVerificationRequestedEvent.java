package com.app.url_shortener.iam.application.event;

import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record EmailVerificationRequestedEvent(
        UUID eventId,
        UUID userId,
        String email,
        VerificationCode verificationCode) {

  public EmailVerificationRequestedEvent {
    Objects.requireNonNull(eventId, "eventId must not be null");
    Objects.requireNonNull(userId, "userId must not be null");
    Objects.requireNonNull(verificationCode, "verificationCode must not be null");

    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("email must not be blank");
    }

    email = email.trim().toLowerCase(Locale.ROOT);
  }

  public static EmailVerificationRequestedEvent create(UUID userId, String email, VerificationCode code) {
    return new EmailVerificationRequestedEvent(UUID.randomUUID(), userId, email, code);
  }

  @Override
  public String toString() {
    return "EmailVerificationRequestedEvent{"
        + "eventId="
        + eventId
        + ", userId="
        + userId
        + ", email='"
        + email
        + '\''
        + ", verificationCode='[REDACTED]'"
        + '}';
  }
}
