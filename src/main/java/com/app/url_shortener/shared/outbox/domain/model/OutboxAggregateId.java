package com.app.url_shortener.shared.outbox.domain.model;

import java.util.Objects;

public record OutboxAggregateId(String value) {

  private static final int MAX_LENGTH = 160;

  public OutboxAggregateId {
    Objects.requireNonNull(value, "aggregateId must not be null");

    value = value.trim();

    if (value.isBlank()) {
      throw new IllegalArgumentException("aggregateId must not be blank");
    }

    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("aggregateId must not exceed " + MAX_LENGTH + " characters");
    }
  }

  public static OutboxAggregateId of(String value) {
    return new OutboxAggregateId(value);
  }
}
