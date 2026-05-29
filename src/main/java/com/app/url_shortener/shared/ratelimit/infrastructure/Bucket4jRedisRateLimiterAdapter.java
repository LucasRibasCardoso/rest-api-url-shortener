package com.app.url_shortener.shared.ratelimit.infrastructure;

import com.app.url_shortener.shared.ratelimit.config.RateLimitPolicyProperties;
import com.app.url_shortener.shared.ratelimit.config.RateLimitProperties;
import com.app.url_shortener.shared.ratelimit.exception.RateLimitInfrastructureException;
import com.app.url_shortener.shared.ratelimit.core.RateLimitDecision;
import com.app.url_shortener.shared.ratelimit.core.RateLimitPolicy;
import com.app.url_shortener.shared.ratelimit.core.RateLimiterPort;
import com.app.url_shortener.shared.ratelimit.key.RateLimitKey;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.BucketNotFoundException;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.lettuce.core.RedisException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Bucket4jRedisRateLimiterAdapter implements RateLimiterPort {

  private final RateLimitProperties properties;
  private final LettuceBasedProxyManager<String> proxyManager;

  private final Map<RateLimitPolicy, BucketConfiguration> bucketConfigs = new ConcurrentHashMap<>();

  @Override
  public RateLimitDecision consume(RateLimitPolicy policy, RateLimitKey key) {
    Objects.requireNonNull(policy, "policy must not be null");
    Objects.requireNonNull(key, "key must not be null");

    BucketConfiguration bucketConfiguration = bucketConfigs.computeIfAbsent(policy, this::createBucketConfiguration);
    try {

      Bucket bucket = proxyManager.getProxy(redisKey(key), () -> bucketConfiguration);
      ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

      if (probe.isConsumed()) {
        return RateLimitDecision.allowed(probe.getRemainingTokens());
      }
      return RateLimitDecision.denied(probe.getRemainingTokens(), probe.getNanosToWaitForRefill());
    }
    catch (BucketNotFoundException | RedisException e) {
      throw new RateLimitInfrastructureException(e);
    }
  }

  private BucketConfiguration createBucketConfiguration(RateLimitPolicy policy) {
    RateLimitPolicyProperties policyProperties = properties.getPolicyProperties(policy);

    return BucketConfiguration.builder()
        .addLimit(limit -> limit
                .capacity(policyProperties.capacity())
                .refillGreedy(policyProperties.refillTokens(), policyProperties.refillPeriod()))
        .build();
  }

  private String redisKey(RateLimitKey key) {
    return String.format("%s:%s", properties.keyPrefix(), key.getValue());
  }
}
