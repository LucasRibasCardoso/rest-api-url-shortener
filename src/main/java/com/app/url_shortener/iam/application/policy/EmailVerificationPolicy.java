package com.app.url_shortener.iam.application.policy;

import java.time.Duration;
import java.util.Objects;

public record EmailVerificationPolicy(
    Duration codeTtl, Duration idempotencyTtl, Duration processingLeaseTtl) {

  public EmailVerificationPolicy {
    Objects.requireNonNull(codeTtl, "codeTtl must not be null");
    Objects.requireNonNull(idempotencyTtl, "idempotencyTtl must not be null");
    Objects.requireNonNull(processingLeaseTtl, "processingLeaseTtl must not be null");

    if (codeTtl.isZero() || codeTtl.isNegative()) {
      throw new IllegalArgumentException("codeTtl must be positive");
    }

    if (idempotencyTtl.isZero() || idempotencyTtl.isNegative()) {
      throw new IllegalArgumentException("idempotencyTtl must be positive");
    }

    if (processingLeaseTtl.isZero() || processingLeaseTtl.isNegative()) {
      throw new IllegalArgumentException("processingLeaseTtl must be positive");
    }
  }
}
