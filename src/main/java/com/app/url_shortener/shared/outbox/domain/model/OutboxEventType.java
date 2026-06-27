package com.app.url_shortener.shared.outbox.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record OutboxEventType(String value) {

  private static final int MAX_LENGTH = 120;
  private static final Pattern PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");

  public OutboxEventType {
    Objects.requireNonNull(value, "eventType must not be null");

    value = value.trim();

    if (value.isBlank()) {
      throw new IllegalArgumentException("eventType must not be blank");
    }

    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("eventType must not exceed " + MAX_LENGTH + " characters");
    }

    if (!PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException("eventType must use format A-Z, 0-9 and underscore");
    }
  }

  public static OutboxEventType of(String value) {
    return new OutboxEventType(value);
  }
}
