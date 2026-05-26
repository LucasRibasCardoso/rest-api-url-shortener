package com.app.url_shortener.shared.infrastructure.ratelimit.redis;

import com.app.url_shortener.config.BaseRedisSliceTest;
import com.app.url_shortener.shared.config.Bucket4jRedisConfig;
import com.app.url_shortener.shared.config.properties.RateLimitPolicyProperties;
import com.app.url_shortener.shared.config.properties.RateLimitProperties;
import com.app.url_shortener.shared.exception.ratelimit.RateLimitInfrastructureException;
import com.app.url_shortener.shared.ratelimit.core.RateLimitPolicy;
import com.app.url_shortener.shared.ratelimit.key.RateLimitKey;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.BucketNotFoundException;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Tag("redis-slice")
@Import({
    Bucket4jRedisConfig.class,
    Bucket4jRedisRateLimiterAdapter.class,
    Bucket4jRedisRateLimiterAdapterTest.RateLimitTestConfig.class
})
@DisplayName("Slice Redis - Rate limiter Bucket4j")
class Bucket4jRedisRateLimiterAdapterTest extends BaseRedisSliceTest {

  private static final String KEY_PREFIX = "bucket4j-rate-limit-test";
  private static final String KEY_PATTERN = KEY_PREFIX + ":*";
  private static final RateLimitPolicy POLICY = RateLimitPolicy.AUTH_LOGIN;

  @Autowired
  private Bucket4jRedisRateLimiterAdapter adapter;

  @Autowired
  private StringRedisTemplate redisTemplate;

  @BeforeEach
  void setUp() {
    deleteRateLimitKeys();
  }

  @AfterEach
  void tearDown() {
    deleteRateLimitKeys();
  }

  @Nested
  @DisplayName("Consumo de tokens")
  class ConsumeTests {

    @Test
    @DisplayName("Deve permitir consumo quando tokens estão disponíveis")
    void should_allow_consumption_when_tokens_are_available() {
      // 1. Arrange
      var key = RateLimitKey.createForEmail(POLICY, "available-token");

      // 2. Act
      var decision = adapter.consume(POLICY, key);

      // 3. Assert
      assertThat(decision.allowed()).isTrue();
      assertThat(decision.remainingTokens()).isPositive();
      assertThat(decision.retryAfter()).isZero();
      assertThat(redisTemplate.hasKey(redisKey(key))).isTrue();
    }

    @Test
    @DisplayName("Deve negar consumo quando limite é excedido")
    void should_deny_consumption_when_limit_is_exceeded() {
      // 1. Arrange
      var key = RateLimitKey.createForEmail(POLICY, "limit-exceeded");

      // 2. Act
      var firstDecision = adapter.consume(POLICY, key);
      var secondDecision = adapter.consume(POLICY, key);
      var deniedDecision = adapter.consume(POLICY, key);

      // 3. Assert
      assertThat(firstDecision.allowed()).isTrue();
      assertThat(secondDecision.allowed()).isTrue();
      assertThat(deniedDecision.allowed()).isFalse();
      assertThat(deniedDecision.remainingTokens()).isZero();
      assertThat(deniedDecision.retryAfter()).isPositive();
    }
  }

  @Nested
  @DisplayName("Falhas de infraestrutura")
  class InfrastructureFailureTests {

    @Test
    @DisplayName("Deve lançar exceção de infraestrutura quando bucket não existir")
    void should_throw_infrastructure_exception_when_bucket_is_not_found() {
      // 1. Arrange
      var properties = rateLimitProperties();
      var proxyManager = mockProxyManager();
      var adapter = new Bucket4jRedisRateLimiterAdapter(properties, proxyManager);
      var key = RateLimitKey.createForEmail(POLICY, "missing-bucket");

      given(proxyManager.getProxy(anyString(), any())).willThrow(new BucketNotFoundException());

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> adapter.consume(POLICY, key))
          .isInstanceOf(RateLimitInfrastructureException.class)
          .hasCauseInstanceOf(BucketNotFoundException.class);
    }

    @Test
    @DisplayName("Deve lançar exceção de infraestrutura quando Redis falhar")
    void should_throw_infrastructure_exception_when_redis_fails() {
      // 1. Arrange
      var properties = rateLimitProperties();
      var proxyManager = mockProxyManager();
      var adapter = new Bucket4jRedisRateLimiterAdapter(properties, proxyManager);
      var key = RateLimitKey.createForEmail(POLICY, "redis-failure");

      given(proxyManager.getProxy(anyString(), any())).willThrow(new RedisException("Redis timeout"));

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> adapter.consume(POLICY, key))
          .isInstanceOf(RateLimitInfrastructureException.class)
          .hasCauseInstanceOf(RedisException.class);
    }
  }

  @Nested
  @DisplayName("Validação de parâmetros")
  class ParameterValidationTests {

    @Test
    @DisplayName("Deve lançar NullPointerException quando policy for nula")
    void should_throw_null_pointer_exception_when_policy_is_null() {
      // 1. Arrange
      var key = RateLimitKey.createForEmail(POLICY, "null-policy");

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> adapter.consume(null, key))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("policy must not be null");
    }

    @Test
    @DisplayName("Deve lançar NullPointerException quando key for nula")
    void should_throw_null_pointer_exception_when_key_is_null() {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> adapter.consume(POLICY, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("key must not be null");
    }
  }

  @Nested
  @DisplayName("Configuração de buckets")
  class BucketConfigurationTests {

    @Test
    @DisplayName("Deve reutilizar configuração criada para mesma policy")
    void should_reuse_bucket_configuration_created_for_same_policy() {
      // 1. Arrange
      var properties = mock(RateLimitProperties.class);
      var proxyManager = mockProxyManager();
      var bucket = mock(BucketProxy.class);
      var adapter = new Bucket4jRedisRateLimiterAdapter(properties, proxyManager);
      var key = RateLimitKey.createForEmail(POLICY, "cached-configuration");

      given(properties.keyPrefix()).willReturn(KEY_PREFIX);
      given(properties.getPolicyProperties(POLICY)).willReturn(policyProperties());
      given(proxyManager.getProxy(anyString(), any())).willReturn(bucket);
      given(bucket.tryConsumeAndReturnRemaining(1)).willReturn(ConsumptionProbe.consumed(1, 0));

      // 2. Act
      adapter.consume(POLICY, key);
      adapter.consume(POLICY, key);
      adapter.consume(POLICY, key);

      // 3. Assert
      verify(properties, times(1)).getPolicyProperties(POLICY);
      verify(proxyManager, times(3)).getProxy(anyString(), any());
    }
  }

  private void deleteRateLimitKeys() {
    Set<String> keys = redisTemplate.keys(KEY_PATTERN);
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }

  private String redisKey(RateLimitKey key) {
    return KEY_PREFIX + ":" + key.getValue();
  }

  private static RateLimitProperties rateLimitProperties() {
    return new RateLimitProperties(true, KEY_PREFIX, "test-secret", Map.of(POLICY.getConfigKey(), policyProperties()));
  }

  private static RateLimitPolicyProperties policyProperties() {
    return new RateLimitPolicyProperties(true, 2, 2, Duration.ofMinutes(1), false);
  }

  @SuppressWarnings("unchecked")
  private static LettuceBasedProxyManager<String> mockProxyManager() {
    return mock(LettuceBasedProxyManager.class);
  }

  @TestConfiguration
  static class RateLimitTestConfig {

    @Bean
    RateLimitProperties rateLimitProperties() {
      return Bucket4jRedisRateLimiterAdapterTest.rateLimitProperties();
    }
  }
}
