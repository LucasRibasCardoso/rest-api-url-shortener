package com.app.url_shortener.iam.application.event;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record EmailVerificationRequestedEvent(
    UUID eventId,
    UUID userId,
    String email,
    EmailVerificationReason reason,
    Instant occurredAt) {

  public EmailVerificationRequestedEvent {
    Objects.requireNonNull(eventId, "eventId must not be null");
    Objects.requireNonNull(userId, "userId must not be null");
    Objects.requireNonNull(reason, "reason must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");

    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("email must not be blank");
    }

    email = email.trim().toLowerCase(Locale.ROOT);
  }

  public static EmailVerificationRequestedEvent create(UUID userId, String email, EmailVerificationReason reason) {
    return new EmailVerificationRequestedEvent(UUID.randomUUID(), userId, email, reason, Instant.now());
  }
}
