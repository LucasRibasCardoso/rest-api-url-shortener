package com.app.url_shortener.iam.application.policy;

import java.time.Duration;
import java.util.Objects;

public record EmailVerificationPolicy(Duration codeTtl) {

  public EmailVerificationPolicy {
    Objects.requireNonNull(codeTtl, "codeTtl must not be null");

    if (codeTtl.isZero() || codeTtl.isNegative()) {
      throw new IllegalArgumentException("codeTtl must be positive");
    }
  }
}
