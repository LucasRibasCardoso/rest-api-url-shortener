package com.app.url_shortener.iam.application.event;

import com.app.url_shortener.shared.domain.validation.RequiredText;

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

    email = RequiredText.normalize(email, "email").toLowerCase(Locale.ROOT);
  }

  public static EmailVerificationRequestedEvent create(UUID userId, String email, EmailVerificationReason reason) {
    return new EmailVerificationRequestedEvent(UUID.randomUUID(), userId, email, reason, Instant.now());
  }

  public EmailVerificationRequestedPayload toPayload() {
    return new EmailVerificationRequestedPayload(userId, email, reason);
  }
}
