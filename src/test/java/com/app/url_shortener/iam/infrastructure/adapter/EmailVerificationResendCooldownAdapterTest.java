package com.app.url_shortener.iam.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.url_shortener.config.BaseRedisSliceTest;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
@Import(EmailVerificationResendCooldownAdapter.class)
@DisplayName("Slice Redis - Cooldown de Reenvio de Verificação de Email")
class EmailVerificationResendCooldownAdapterTest extends BaseRedisSliceTest {

  private static final String KEY_PREFIX = "auth:email-verification:resend-cooldown:";
  private static final String KEY_PATTERN = KEY_PREFIX + "*";

  @Autowired private EmailVerificationResendCooldownAdapter adapter;

  @Autowired private StringRedisTemplate redisTemplate;

  @BeforeEach
  void setUp() {
    deleteCooldownKeys();
  }

  @AfterEach
  void tearDown() {
    deleteCooldownKeys();
  }

  @Nested
  @DisplayName("Reserva")
  class ReserveTests {

    @Test
    @DisplayName("Deve reservar cooldown quando chave não existir")
    void shouldReserveCooldownWhenKeyDoesNotExist() {
      // 1. Arrange
      var email = "user@email.com";
      var ttl = Duration.ofMinutes(2);

      // 2. Act
      var result = adapter.reserve(email, ttl);

      // 3. Assert
      assertThat(result).isTrue();
      assertThat(redisTemplate.opsForValue().get(redisKey(email))).isEqualTo("1");
      assertThatTtlIsCloseTo(redisKey(email), ttl);
    }

    @Test
    @DisplayName("Deve retornar falso quando cooldown já estiver reservado")
    void shouldReturnFalseWhenCooldownIsAlreadyReserved() {
      // 1. Arrange
      var email = "user@email.com";
      var ttl = Duration.ofMinutes(2);
      var firstResult = adapter.reserve(email, ttl);

      // 2. Act
      var secondResult = adapter.reserve(email, ttl);

      // 3. Assert
      assertThat(firstResult).isTrue();
      assertThat(secondResult).isFalse();
      assertThat(redisTemplate.opsForValue().get(redisKey(email))).isEqualTo("1");
      assertThatTtlIsCloseTo(redisKey(email), ttl);
    }
  }

  private void assertThatTtlIsCloseTo(String redisKey, Duration expectedTtl) {
    Long ttlMillis = redisTemplate.getExpire(redisKey, TimeUnit.MILLISECONDS);
    assertThat(ttlMillis)
        .isNotNull()
        .isPositive()
        .isLessThanOrEqualTo(expectedTtl.toMillis())
        .isGreaterThan(expectedTtl.minusSeconds(5).toMillis());
  }

  private String redisKey(String email) {
    return KEY_PREFIX + email;
  }

  private void deleteCooldownKeys() {
    Set<String> keys = redisTemplate.keys(KEY_PATTERN);
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }
}
