package com.app.url_shortener.url.application.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record UrlRedirectedEvent(UUID eventId, String shortCode, Instant lastAccessedAt) {

  public UrlRedirectedEvent {
    Objects.requireNonNull(eventId, "eventId must not be null");
    Objects.requireNonNull(lastAccessedAt, "lastAccessedAt must not be null");

    if (shortCode == null || shortCode.isBlank()) {
      throw new IllegalArgumentException("shortCode must not be blank");
    }
  }

  public static UrlRedirectedEvent create(String shortCode) {
    return new UrlRedirectedEvent(UUID.randomUUID(), shortCode.trim(), Instant.now());
  }
}
