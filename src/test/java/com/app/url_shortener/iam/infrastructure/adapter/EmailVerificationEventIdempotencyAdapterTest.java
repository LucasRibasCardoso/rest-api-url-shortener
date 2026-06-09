package com.app.url_shortener.iam.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Idempotência de Eventos de Verificação de Email")
class EmailVerificationEventIdempotencyAdapterTest {

  private static final UUID EVENT_ID = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac100");
  private static final String KEY = "iam:email-verification:event-idempotency:" + EVENT_ID;

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  @InjectMocks
  private EmailVerificationEventIdempotencyAdapter adapter;

  @Nested
  @DisplayName("Marcação atômica")
  class AtomicMarkTests {

    @Test
    @DisplayName("Deve adquirir marca usando operação atômica com TTL")
    void shouldAcquireMarkAtomicallyWithTtl() {
      // 1. Arrange
      var ttl = Duration.ofDays(4);
      when(redisTemplate.opsForValue()).thenReturn(valueOperations);
      when(valueOperations.setIfAbsent(KEY, "processed", ttl)).thenReturn(true);

      // 2. Act
      var acquired = adapter.tryMarkAsProcessed(EVENT_ID, ttl);

      // 3. Assert
      assertThat(acquired).isTrue();
      verify(valueOperations).setIfAbsent(KEY, "processed", ttl);
      verifyNoMoreInteractions(valueOperations);
    }

    @Test
    @DisplayName("Deve informar duplicata quando a marca já existir")
    void shouldReportDuplicateWhenMarkAlreadyExists() {
      // 1. Arrange
      var ttl = Duration.ofDays(4);
      when(redisTemplate.opsForValue()).thenReturn(valueOperations);
      when(valueOperations.setIfAbsent(KEY, "processed", ttl)).thenReturn(false);

      // 2. Act
      var acquired = adapter.tryMarkAsProcessed(EVENT_ID, ttl);

      // 3. Assert
      assertThat(acquired).isFalse();
    }

    @Test
    @DisplayName("Deve remover marca para permitir retry")
    void shouldRemoveMarkToAllowRetry() {
      // 1. Arrange

      // 2. Act
      adapter.removeProcessedMark(EVENT_ID);

      // 3. Assert
      verify(redisTemplate).delete(KEY);
    }
  }
}
