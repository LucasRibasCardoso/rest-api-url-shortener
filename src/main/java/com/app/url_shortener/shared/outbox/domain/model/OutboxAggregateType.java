package com.app.url_shortener.shared.outbox.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record OutboxAggregateType(String value) {

  private static final int MAX_LENGTH = 80;
  private static final Pattern PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");

  public OutboxAggregateType {
    Objects.requireNonNull(value, "aggregateType must not be null");

    value = value.trim();

    if (value.isBlank()) {
      throw new IllegalArgumentException("aggregateType must not be blank");
    }

    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "aggregateType must not exceed " + MAX_LENGTH + " characters");
    }

    if (!PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException("aggregateType must use format A-Z, 0-9 and underscore");
    }
  }

  public static OutboxAggregateType of(String value) {
    return new OutboxAggregateType(value);
  }
}
