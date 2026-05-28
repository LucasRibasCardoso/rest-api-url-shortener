package com.app.url_shortener.iam.infrastructure.adapter;

import com.app.url_shortener.config.BaseRedisSliceTest;
import com.app.url_shortener.iam.domain.valueobject.EmailVerificationToken;
import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("redis-slice")
@Import(EmailVerificationTokenAdapter.class)
@DisplayName("Slice Redis - Adaptador de Token de Verificação de Email")
class EmailVerificationTokenAdapterTest extends BaseRedisSliceTest {

  private static final String KEY_PREFIX = "auth:email-verification:";
  private static final String KEY_PATTERN = KEY_PREFIX + "*";

  @Autowired
  private EmailVerificationTokenAdapter adapter;

  @Autowired
  private StringRedisTemplate redisTemplate;

  @BeforeEach
  void setUp() {
    deleteEmailVerificationKeys();
  }

  @AfterEach
  void tearDown() {
    deleteEmailVerificationKeys();
  }

  @Nested
  @DisplayName("Armazenamento")
  class StoreTests {

    @Test
    @DisplayName("Deve criar chave no Redis com valor serializado e TTL")
    void shouldCreateRedisKeyWithSerializedValueAndTtl() {
      // 1. Arrange
      var ttl = Duration.ofMinutes(15);
      var token = emailVerificationToken("user@email.com");
      var key = key(token.email());

      // 2. Act
      adapter.store(token, ttl);

      // 3. Assert
      assertThat(redisTemplate.hasKey(key)).isTrue();
      assertThat(redisTemplate.opsForValue().get(key)).isEqualTo(serializedValue(token));

      var ttlMillis = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
      assertThat(ttlMillis)
              .isNotNull()
              .isPositive()
              .isLessThanOrEqualTo(ttl.toMillis())
              .isGreaterThan(ttl.minusSeconds(5).toMillis());
    }

  }

  @Nested
  @DisplayName("Consumo")
  class ConsumeTests {

    @Test
    @DisplayName("Deve consumir token quando o código corresponder e remover chave")
    void shouldConsumeTokenWhenCodeMatchesAndRemoveKey() {
      // 1. Arrange
      var token = emailVerificationToken("consume@email.com");
      var key = key(token.email());
      redisTemplate.opsForValue().set(key, serializedValue(token), Duration.ofMinutes(10));

      // 2. Act
      var result = adapter.consumeByEmailAndCode(token.email(), token.code());

      // 3. Assert
      assertThat(result).isPresent();
      assertThat(result.get().userId()).isEqualTo(token.userId());
      assertThat(result.get().email()).isEqualTo(token.email());
      assertThat(result.get().code()).isEqualTo(token.code());
      assertThat(result.get().expiresAt()).isEqualTo(token.expiresAt());
      assertThat(redisTemplate.hasKey(key)).isFalse();
      assertThat(redisTemplate.getExpire(key, TimeUnit.MILLISECONDS)).isEqualTo(-2L);
    }

    @Test
    @DisplayName("Deve consumir token uma única vez")
    void shouldConsumeTokenOnlyOnce() {
      // 1. Arrange
      var token = emailVerificationToken("single-use@email.com");
      adapter.store(token, Duration.ofMinutes(10));

      // 2. Act
      var firstResult = adapter.consumeByEmailAndCode(token.email(), token.code());
      var secondResult = adapter.consumeByEmailAndCode(token.email(), token.code());

      // 3. Assert
      assertThat(firstResult).isPresent();
      assertThat(secondResult).isEmpty();
      assertThat(redisTemplate.hasKey(key(token.email()))).isFalse();
    }

    @Test
    @DisplayName("Deve retornar vazio e preservar chave quando o código não corresponder")
    void shouldReturnEmptyAndPreserveKeyWhenCodeDoesNotMatch() {
      // 1. Arrange
      var token = emailVerificationToken("invalid-code@email.com");
      var key = key(token.email());
      adapter.store(token, Duration.ofMinutes(10));

      // 2. Act
      var result = adapter.consumeByEmailAndCode(token.email(), VerificationCode.of("654321"));

      // 3. Assert
      assertThat(result).isEmpty();
      assertThat(redisTemplate.hasKey(key)).isTrue();
      assertThat(redisTemplate.opsForValue().get(key)).isEqualTo(serializedValue(token));
      assertThat(redisTemplate.getExpire(key, TimeUnit.MILLISECONDS)).isPositive();
    }

    @Test
    @DisplayName("Deve retornar vazio quando chave não existir")
    void shouldReturnEmptyWhenKeyDoesNotExist() {
      // 1. Arrange
      var email = "missing@email.com";

      // 2. Act
      var result = adapter.consumeByEmailAndCode(email, VerificationCode.of("123456"));

      // 3. Assert
      assertThat(result).isEmpty();
      assertThat(redisTemplate.hasKey(key(email))).isFalse();
    }

    @Test
    @DisplayName("Deve deixar token indisponível após expiração curta")
    void shouldMakeTokenUnavailableAfterShortExpiration() throws InterruptedException {
      // 1. Arrange
      var token = emailVerificationToken("expires@email.com");
      var key = key(token.email());
      adapter.store(token, Duration.ofMillis(50));

      // 2. Act
      waitUntilKeyIsMissing(key);
      var result = adapter.consumeByEmailAndCode(token.email(), token.code());

      // 3. Assert
      assertThat(result).isEmpty();
      assertThat(redisTemplate.hasKey(key)).isFalse();
      assertThat(redisTemplate.getExpire(key, TimeUnit.MILLISECONDS)).isEqualTo(-2L);
    }

    @Test
    @DisplayName("Deve permitir apenas um consumo em chamadas concorrentes")
    void shouldAllowOnlyOneConsumptionWhenRequestsAreConcurrent() throws Exception {
      // 1. Arrange
      var token = emailVerificationToken("concurrent@email.com");
      var readyLatch = new CountDownLatch(2);
      var startLatch = new CountDownLatch(1);
      var executor = Executors.newFixedThreadPool(2);
      adapter.store(token, Duration.ofMinutes(10));

      // 2. Act
      try {
        var tasks = List.of(
                executor.submit(() -> consumeAfterStart(token, readyLatch, startLatch)),
                executor.submit(() -> consumeAfterStart(token, readyLatch, startLatch))
        );

        var requestsAreReady = readyLatch.await(2, TimeUnit.SECONDS);
        startLatch.countDown();

        var results = List.of(tasks.get(0).get(2, TimeUnit.SECONDS), tasks.get(1).get(2, TimeUnit.SECONDS));

        // 3. Assert
        assertThat(requestsAreReady).isTrue();
        assertThat(results).hasSize(2);
        assertThat(results.stream().filter(Optional::isPresent)).hasSize(1);
        assertThat(results.stream().filter(Optional::isEmpty)).hasSize(1);
        assertThat(redisTemplate.hasKey(key(token.email()))).isFalse();
      }
      finally {
        executor.shutdownNow();
      }
    }
  }

  private Optional<EmailVerificationToken> consumeAfterStart(
          EmailVerificationToken token,
          CountDownLatch readyLatch,
          CountDownLatch startLatch) throws InterruptedException {
    readyLatch.countDown();
    startLatch.await();
    return adapter.consumeByEmailAndCode(token.email(), token.code());
  }

  private void waitUntilKeyIsMissing(String key) throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();

    while (Boolean.TRUE.equals(redisTemplate.hasKey(key)) && System.nanoTime() < deadline) {
      Thread.sleep(10);
    }
  }

  private void deleteEmailVerificationKeys() {
    Set<String> keys = redisTemplate.keys(KEY_PATTERN);
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }

  private EmailVerificationToken emailVerificationToken(String email) {
    return EmailVerificationToken.create(
            UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac123"),
            email,
            VerificationCode.of("123456"),
            Instant.parse("2026-05-07T18:00:00Z")
    );
  }

  private String serializedValue(EmailVerificationToken token) {
    return token.userId()
            + "|"
            + token.code().value()
            + "|"
            + token.expiresAt();
  }

  private String key(String email) {
    return KEY_PREFIX + email;
  }
}
