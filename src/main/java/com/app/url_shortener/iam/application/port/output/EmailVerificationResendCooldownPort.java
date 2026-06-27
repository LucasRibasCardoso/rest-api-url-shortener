package com.app.url_shortener.iam.application.port.output;

import java.time.Duration;

public interface EmailVerificationResendCooldownPort {
  boolean reserve(String email, Duration cooldownTtl);
}
