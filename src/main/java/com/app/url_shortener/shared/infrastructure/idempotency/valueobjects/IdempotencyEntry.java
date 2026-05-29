package com.app.url_shortener.shared.infrastructure.idempotency.valueobjects;

import com.app.url_shortener.shared.infrastructure.idempotency.enums.IdempotencyStatus;

import java.time.Instant;

public record IdempotencyEntry(
        IdempotencyStatus status,
        RequestFingerprint fingerprint,
        CachedResponse response,
        Instant createAt
) {

  public boolean isComplete() {
    return status == IdempotencyStatus.COMPLETED;
  }
}
