package com.app.url_shortener.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.app.url_shortener.config.BaseRedisSliceTest;
import com.app.url_shortener.shared.ratelimit.config.Bucket4jRedisConfig;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

@Tag("redis-slice")
@Import(Bucket4jRedisConfig.class)
@DisplayName("Slice Redis - Configuração Bucket4j")
class Bucket4jRedisConfigTest extends BaseRedisSliceTest {

  private static final String KEY_PREFIX = "bucket4j-config-test:";
  private static final String KEY_PATTERN = KEY_PREFIX + "*";

  @Autowired private Bucket4jRedisConfig.Bucket4jRedisConnection redisConnection;

  @Autowired private LettuceBasedProxyManager<String> proxyManager;

  @Autowired private StringRedisTemplate redisTemplate;

  @BeforeEach
  void setUp() {
    deleteBucketKeys();
  }

  @AfterEach
  void tearDown() {
    deleteBucketKeys();
  }

  @Nested
  @DisplayName("Conexão Redis")
  class RedisConnectionTests {

    @Test
    @DisplayName("Deve criar conexão standalone aberta para Bucket4j")
    void shouldCreateOpenStandaloneConnectionForBucket4j() {
      // 1. Arrange

      // 2. Act
      var standaloneConnection = redisConnection.standaloneConnection();
      var clusterConnection = redisConnection.clusterConnection();

      // 3. Assert
      assertThat(standaloneConnection).isNotNull();
      assertThat(standaloneConnection.isOpen()).isTrue();
      assertThat(clusterConnection).isNull();
    }

    @Test
    @DisplayName("Deve rejeitar wrapper sem conexão Redis")
    void shouldRejectWrapperWithoutRedisConnection() {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> new Bucket4jRedisConfig.Bucket4jRedisConnection(null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Only one Bucket4j Redis connection can be used");
    }

    @Test
    @DisplayName("Deve rejeitar wrapper com conexões standalone e cluster ao mesmo tempo")
    void shouldRejectWrapperWithStandaloneAndClusterConnections() {
      // 1. Arrange
      @SuppressWarnings("unchecked")
      var standaloneConnection =
          (StatefulRedisConnection<String, byte[]>) mock(StatefulRedisConnection.class);
      @SuppressWarnings("unchecked")
      var clusterConnection =
          (StatefulRedisClusterConnection<String, byte[]>)
              mock(StatefulRedisClusterConnection.class);

      // 2. Act & 3. Assert
      assertThatThrownBy(
              () ->
                  new Bucket4jRedisConfig.Bucket4jRedisConnection(
                      standaloneConnection, clusterConnection))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Only one Bucket4j Redis connection can be used");
    }
  }

  @Nested
  @DisplayName("Proxy Manager")
  class ProxyManagerTests {

    @Test
    @DisplayName("Deve criar bucket distribuído e persistir consumo no Redis")
    void shouldCreateDistributedBucketAndPersistConsumptionInRedis() {
      // 1. Arrange
      var key = KEY_PREFIX + "persist-consumption";
      var configuration = bucketConfiguration();
      var firstBucket = proxyManager.getProxy(key, () -> configuration);

      // 2. Act
      var firstProbe = firstBucket.tryConsumeAndReturnRemaining(1);
      var secondBucket = proxyManager.getProxy(key, () -> configuration);
      var secondProbe = secondBucket.tryConsumeAndReturnRemaining(1);
      var deniedProbe = secondBucket.tryConsumeAndReturnRemaining(1);

      // 3. Assert
      assertThat(firstProbe.isConsumed()).isTrue();
      assertThat(firstProbe.getRemainingTokens()).isEqualTo(1);
      assertThat(secondProbe.isConsumed()).isTrue();
      assertThat(secondProbe.getRemainingTokens()).isEqualTo(0);
      assertThat(deniedProbe.isConsumed()).isFalse();
      assertThat(deniedProbe.getNanosToWaitForRefill()).isPositive();
      assertThat(redisTemplate.hasKey(key)).isTrue();
    }
  }

  private void deleteBucketKeys() {
    Set<String> keys = redisTemplate.keys(KEY_PATTERN);
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }

  private BucketConfiguration bucketConfiguration() {
    return BucketConfiguration.builder()
        .addLimit(Bandwidth.builder().capacity(2).refillGreedy(2, Duration.ofMinutes(1)).build())
        .build();
  }
}
