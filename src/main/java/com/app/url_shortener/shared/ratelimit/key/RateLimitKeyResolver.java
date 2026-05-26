package com.app.url_shortener.shared.ratelimit.key;

import com.app.url_shortener.shared.ratelimit.core.RateLimitPolicy;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RateLimitKeyResolver {

  private final EmailRateLimitKeyHasher emailRateLimitKeyHasher;

  public RateLimitKey loginByEmail(String email) {
    requireNotBlank(email, "email");
    String emailHash = emailRateLimitKeyHasher.hash(email);
    return RateLimitKey.createForEmail(RateLimitPolicy.AUTH_LOGIN, emailHash);
  }

  public RateLimitKey loginByIpAndEmail(String clientIp, String email) {
    requireNotBlank(clientIp, "clientIp");
    requireNotBlank(email, "email");
    String emailHash = emailRateLimitKeyHasher.hash(email);
    return RateLimitKey.createForIpAndEmail(RateLimitPolicy.AUTH_LOGIN, clientIp, emailHash);
  }

  public RateLimitKey verifyEmailByEmail(String email) {
    requireNotBlank(email, "email");
    String emailHash = emailRateLimitKeyHasher.hash(email);
    return RateLimitKey.createForEmail(RateLimitPolicy.AUTH_VERIFY_EMAIL, emailHash);
  }

  public RateLimitKey resendVerificationByEmail(String email) {
    requireNotBlank(email, "email");
    String emailHash = emailRateLimitKeyHasher.hash(email);
    return RateLimitKey.createForEmail(RateLimitPolicy.AUTH_RESEND_VERIFICATION, emailHash);
  }

  public RateLimitKey shortenByUserIdAndPlan(UUID userId, RateLimitPolicy policy) {
    Objects.requireNonNull(userId, "userId must not be null");
    Objects.requireNonNull(policy, "policy must not be null");

    if (!policy.isPremium() && !policy.isFree()) {
      throw new IllegalArgumentException("Invalid shorten rate limit policy for user-based key: " + policy);
    }
    return RateLimitKey.createForUserId(policy, userId);
  }

  private static void requireNotBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
  }
}
