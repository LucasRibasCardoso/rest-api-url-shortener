package com.app.url_shortener.iam.application.policy;

import java.time.Duration;
import java.util.Objects;

public record EmailVerificationPolicy(
    Duration codeTtl, Duration sendingTimeout, Duration resendCooldown) {

  public EmailVerificationPolicy {
    Objects.requireNonNull(codeTtl, "codeTtl must not be null");
    Objects.requireNonNull(sendingTimeout, "sendingTimeout must not be null");
    Objects.requireNonNull(resendCooldown, "resendCooldown must not be null");

    if (codeTtl.isZero() || codeTtl.isNegative()) {
      throw new IllegalArgumentException("codeTtl must be positive");
    }

    if (sendingTimeout.isZero() || sendingTimeout.isNegative()) {
      throw new IllegalArgumentException("sendingTimeout must be positive");
    }

    if (resendCooldown.isZero() || resendCooldown.isNegative()) {
      throw new IllegalArgumentException("resendCooldown must be positive");
    }
  }
}
