package com.app.url_shortener.shared.idempotency.valueobjects;

import java.util.Objects;

public record IdempotencyKey(String idempotencyKey, RequestFingerprint fingerprint) {

  public IdempotencyKey {
    Objects.requireNonNull(idempotencyKey, "idempotency key must not be null");
    if (idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("idempotency key must not be blank");
    }
    Objects.requireNonNull(fingerprint, "request fingerprint must not be null");
  }

  public String value() {
    return String.join(
        ":",
        fingerprint.principalScope(),
        fingerprint.method(),
        fingerprint.route(),
        idempotencyKey);
  }

  public static IdempotencyKey generate(String idempotencyKey, RequestFingerprint fingerprint) {
    return new IdempotencyKey(idempotencyKey, fingerprint);
  }
}
