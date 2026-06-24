package com.app.url_shortener.shared.ratelimit.key;

import com.app.url_shortener.shared.ratelimit.core.RateLimitPolicy;
import java.util.Objects;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public final class RateLimitKey {

  private final String value;

  private RateLimitKey(String value) {
    requireNotBlank(value, "value");
    this.value = value;
  }

  // Format: {policy}:ip:{clientIp}:email:{emailHash}
  public static RateLimitKey createForIpAndEmail(RateLimitPolicy policy, String clientIp, String emailHash) {
    Objects.requireNonNull(policy, "policy must not be null");
    requireNotBlank(clientIp, "clientIp");
    requireNotBlank(emailHash, "emailHash");
    return new RateLimitKey(String.format("%s:ip:%s:email:%s", policy.getConfigKey(), clientIp, emailHash));
  }

  // Format: {configKey}:email:{emailHash}
  public static RateLimitKey createForEmail(RateLimitPolicy policy, String emailHash) {
    Objects.requireNonNull(policy, "policy must not be null");
    requireNotBlank(emailHash, "emailHash");
    return new RateLimitKey(String.format("%s:email:%s", policy.getConfigKey(), emailHash));
  }

  // Format: {configKey}:ip:{clientIp}
  public static RateLimitKey createForIp(RateLimitPolicy policy, String clientIp) {
    Objects.requireNonNull(policy, "policy must not be null");
    requireNotBlank(clientIp, "clientIp");
    return new RateLimitKey(String.format("%s:ip:%s", policy.getConfigKey(), clientIp));
  }

  // Format: {configKey}:user:{userId}
  public static RateLimitKey createForUserId(RateLimitPolicy policy, UUID userId) {
    Objects.requireNonNull(policy, "policy must not be null");
    Objects.requireNonNull(userId, "userId must not be null");
    return new RateLimitKey(String.format("%s:user:%s", policy.getConfigKey(), userId));
  }

  private static void requireNotBlank(String keyValue, String fieldName) {
    if (keyValue == null || keyValue.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
  }

  @Override
  public String toString() {
    return "RateLimitKey{value='[REDACTED]'}";
  }
}
