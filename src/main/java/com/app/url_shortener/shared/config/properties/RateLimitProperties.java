package com.app.url_shortener.shared.config.properties;

import com.app.url_shortener.shared.ratelimit.core.RateLimitPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
    boolean enabled,
    @NotBlank String keyPrefix,
    @NotBlank String emailHashSecret,
    @Valid @NotNull Map<String, RateLimitPolicyProperties> policies) {

  public RateLimitPolicyProperties getPolicyProperties(RateLimitPolicy policy) {
    Objects.requireNonNull(policy, "policy must not be null");
    RateLimitPolicyProperties properties = policies.get(policy.getConfigKey());

    if (properties == null) {
      throw new IllegalStateException("Missing rate limit policy configuration for key: " + policy.getConfigKey());
    }

    return properties;
  }
}
