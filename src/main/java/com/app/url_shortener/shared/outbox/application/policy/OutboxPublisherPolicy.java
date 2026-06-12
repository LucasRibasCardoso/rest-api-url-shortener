package com.app.url_shortener.shared.outbox.application.policy;

import java.time.Duration;
import java.util.Objects;

public record OutboxPublisherPolicy(int batchSize, int maxAttempts, Duration retryDelay) {

  public OutboxPublisherPolicy {
    if (batchSize <= 0) {
      throw new IllegalArgumentException("batchSize must be positive");
    }
    if (maxAttempts <= 0) {
      throw new IllegalArgumentException("maxAttempts must be positive");
    }
    Objects.requireNonNull(retryDelay, "retryDelay must not be null");
    if (retryDelay.isZero() || retryDelay.isNegative()) {
      throw new IllegalArgumentException("retryDelay must be positive");
    }
  }
}
