package com.app.url_shortener.iam.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.url_shortener.config.BaseRedisSliceTest;
import com.app.url_shortener.iam.application.port.output.model.EmailVerificationProcessingLease;
import com.app.url_shortener.iam.application.port.output.model.EmailVerificationProcessingLeaseStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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
@Import(EmailVerificationIdempotencyAdapter.class)
@DisplayName("Slice Redis - Idempotência de Eventos de Verificação de Email")
class EmailVerificationIdempotencyAdapterTest extends BaseRedisSliceTest {

  private static final String KEY_PREFIX = "iam:email-verification:event-idempotency:";
  private static final String KEY_PATTERN = KEY_PREFIX + "*";
  private static final UUID EVENT_ID = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac100");
  private static final UUID UNKNOWN_LEASE_ID =
      UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac200");
  private static final Duration PROCESSING_LEASE_TTL = Duration.ofSeconds(30);
  private static final Duration IDEMPOTENCY_TTL = Duration.ofMinutes(5);

  @Autowired
  private EmailVerificationIdempotencyAdapter adapter;

  @Autowired
  private StringRedisTemplate redisTemplate;

  @BeforeEach
  void setUp() {
    deleteIdempotencyKeys();
  }

  @AfterEach
  void tearDown() {
    deleteIdempotencyKeys();
  }

  @Nested
  @DisplayName("Aquisição de lease")
  class AcquireProcessingLeaseTests {

    @Test
    @DisplayName("Deve adquirir novo evento e armazenar lease PROCESSING")
    void shouldAcquireNewEventAndStoreOwnedProcessingLease() {
      // 1. Arrange

      // 2. Act
      var lease = adapter.acquireProcessingLease(EVENT_ID, PROCESSING_LEASE_TTL);

      // 3. Assert
      assertThat(lease.status()).isEqualTo(EmailVerificationProcessingLeaseStatus.ACQUIRED);
      assertThat(lease.leaseId()).isNotNull();
      assertThat(redisTemplate.opsForValue().get(key(EVENT_ID)))
          .isEqualTo(processingState(lease.leaseId()));
    }

    @Test
    @DisplayName("Deve usar processingLeaseTtl no lease PROCESSING")
    void shouldUseProcessingLeaseTtlForProcessingLease() {
      // 1. Arrange

      // 2. Act
      adapter.acquireProcessingLease(EVENT_ID, PROCESSING_LEASE_TTL);

      // 3. Assert
      assertTtlIsAtMost(key(EVENT_ID), PROCESSING_LEASE_TTL);
    }

    @Test
    @DisplayName("Deve retornar PROCESSING sem expor leaseId quando evento já estiver em processamento")
    void shouldReturnProcessingWithoutLeaseIdWhenEventIsAlreadyProcessing() {
      // 1. Arrange
      adapter.acquireProcessingLease(EVENT_ID, PROCESSING_LEASE_TTL);

      // 2. Act
      var lease = adapter.acquireProcessingLease(EVENT_ID, PROCESSING_LEASE_TTL);

      // 3. Assert
      assertThat(lease).isEqualTo(EmailVerificationProcessingLease.processing());
    }

    @Test
    @DisplayName("Deve retornar COMPLETED quando evento já estiver concluído")
    void shouldReturnCompletedWhenEventIsAlreadyCompleted() {
      // 1. Arrange
      var acquiredLease = adapter.acquireProcessingLease(EVENT_ID, PROCESSING_LEASE_TTL);
      adapter.markAsCompleted(EVENT_ID, acquiredLease.leaseId(), IDEMPOTENCY_TTL);

      // 2. Act
      var lease = adapter.acquireProcessingLease(EVENT_ID, PROCESSING_LEASE_TTL);

      // 3. Assert
      assertThat(lease).isEqualTo(EmailVerificationProcessingLease.completed());
    }

    @Test
    @DisplayName("Deve permitir somente uma aquisição concorrente")
    void shouldAllowOnlyOneConcurrentAcquisition() throws Exception {
      // 1. Arrange
      var consumers = 8;
      var readyLatch = new CountDownLatch(consumers);
      var startLatch = new CountDownLatch(1);
      var executor = Executors.newFixedThreadPool(consumers);
      var futures = new ArrayList<java.util.concurrent.Future<EmailVerificationProcessingLease>>();

      try {
        for (int index = 0; index < consumers; index++) {
          futures.add(
              executor.submit(
                  () -> {
                    readyLatch.countDown();
                    startLatch.await();
                    return adapter.acquireProcessingLease(EVENT_ID, PROCESSING_LEASE_TTL);
                  }));
        }
        assertThat(readyLatch.await(2, TimeUnit.SECONDS)).isTrue();

        // 2. Act
        startLatch.countDown();
        var leases = new ArrayList<EmailVerificationProcessingLease>();
        for (var future : futures) {
          leases.add(future.get(2, TimeUnit.SECONDS));
        }

        // 3. Assert
        assertThat(leases)
            .filteredOn(lease -> lease.status() == EmailVerificationProcessingLeaseStatus.ACQUIRED)
            .hasSize(1);
        assertThat(leases)
            .filteredOn(lease -> lease.status() == EmailVerificationProcessingLeaseStatus.PROCESSING)
            .hasSize(consumers - 1);
      } finally {
        executor.shutdownNow();
      }
    }

    @Test
    @DisplayName("Deve rejeitar estado Redis desconhecido")
    void shouldRejectUnknownRedisState() {
      // 1. Arrange
      redisTemplate.opsForValue().set(key(EVENT_ID), "UNKNOWN", PROCESSING_LEASE_TTL);

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> adapter.acquireProcessingLease(EVENT_ID, PROCESSING_LEASE_TTL))
          .isInstanceOf(RuntimeException.class)
          .hasRootCauseMessage("Unknown email verification idempotency state");
    }
  }

  @Nested
  @DisplayName("Conclusão")
  class MarkAsCompletedTests {

    @Test
    @DisplayName("Deve concluir somente quando leaseId for proprietário")
    void shouldCompleteOnlyWhenLeaseIdOwnsProcessingLease() {
      // 1. Arrange
      var lease = adapter.acquireProcessingLease(EVENT_ID, PROCESSING_LEASE_TTL);

      // 2. Act
      var completed = adapter.markAsCompleted(EVENT_ID, lease.leaseId(), IDEMPOTENCY_TTL);

      // 3. Assert
      assertThat(completed).isTrue();
      assertThat(redisTemplate.opsForValue().get(key(EVENT_ID))).isEqualTo("COMPLETED");
      assertTtlIsAtMost(key(EVENT_ID), IDEMPOTENCY_TTL);
    }

    @Test
    @DisplayName("Não deve concluir quando leaseId não for proprietário")
    void shouldNotCompleteWhenLeaseIdDoesNotOwnProcessingLease() {
      // 1. Arrange
      var lease = adapter.acquireProcessingLease(EVENT_ID, PROCESSING_LEASE_TTL);

      // 2. Act
      var completed = adapter.markAsCompleted(EVENT_ID, UNKNOWN_LEASE_ID, IDEMPOTENCY_TTL);

      // 3. Assert
      assertThat(completed).isFalse();
      assertThat(redisTemplate.opsForValue().get(key(EVENT_ID)))
          .isEqualTo(processingState(lease.leaseId()));
    }

    @Test
    @DisplayName("Consumer antigo não deve concluir lease readquirido")
    void shouldNotAllowExpiredLeaseOwnerToCompleteReacquiredLease() {
      // 1. Arrange
      var oldLease = adapter.acquireProcessingLease(EVENT_ID, Duration.ofMillis(100));
      awaitKeyExpiration(key(EVENT_ID));
      var currentLease = adapter.acquireProcessingLease(EVENT_ID, PROCESSING_LEASE_TTL);

      // 2. Act
      var completed = adapter.markAsCompleted(EVENT_ID, oldLease.leaseId(), IDEMPOTENCY_TTL);

      // 3. Assert
      assertThat(completed).isFalse();
      assertThat(redisTemplate.opsForValue().get(key(EVENT_ID)))
          .isEqualTo(processingState(currentLease.leaseId()));
    }
  }

  @Nested
  @DisplayName("Liberação de lease")
  class ReleaseProcessingLeaseTests {

    @Test
    @DisplayName("Deve remover somente lease PROCESSING proprietário")
    void shouldRemoveOnlyOwnedProcessingLease() {
      // 1. Arrange
      var lease = adapter.acquireProcessingLease(EVENT_ID, PROCESSING_LEASE_TTL);

      // 2. Act
      var released = adapter.releaseProcessingLease(EVENT_ID, lease.leaseId());

      // 3. Assert
      assertThat(released).isTrue();
      assertThat(redisTemplate.hasKey(key(EVENT_ID))).isFalse();
    }

    @Test
    @DisplayName("Não deve remover lease PROCESSING de outro proprietário")
    void shouldNotRemoveProcessingLeaseOwnedByAnotherConsumer() {
      // 1. Arrange
      var lease = adapter.acquireProcessingLease(EVENT_ID, PROCESSING_LEASE_TTL);

      // 2. Act
      var released = adapter.releaseProcessingLease(EVENT_ID, UNKNOWN_LEASE_ID);

      // 3. Assert
      assertThat(released).isFalse();
      assertThat(redisTemplate.opsForValue().get(key(EVENT_ID)))
          .isEqualTo(processingState(lease.leaseId()));
    }

    @Test
    @DisplayName("Consumer antigo não deve remover lease readquirido")
    void shouldNotAllowExpiredLeaseOwnerToRemoveReacquiredLease() {
      // 1. Arrange
      var oldLease = adapter.acquireProcessingLease(EVENT_ID, Duration.ofMillis(100));
      awaitKeyExpiration(key(EVENT_ID));
      var currentLease = adapter.acquireProcessingLease(EVENT_ID, PROCESSING_LEASE_TTL);

      // 2. Act
      var released = adapter.releaseProcessingLease(EVENT_ID, oldLease.leaseId());

      // 3. Assert
      assertThat(released).isFalse();
      assertThat(redisTemplate.opsForValue().get(key(EVENT_ID)))
          .isEqualTo(processingState(currentLease.leaseId()));
    }

    @Test
    @DisplayName("Não deve remover evento COMPLETED")
    void shouldNotRemoveCompletedEvent() {
      // 1. Arrange
      var lease = adapter.acquireProcessingLease(EVENT_ID, PROCESSING_LEASE_TTL);
      adapter.markAsCompleted(EVENT_ID, lease.leaseId(), IDEMPOTENCY_TTL);

      // 2. Act
      var released = adapter.releaseProcessingLease(EVENT_ID, lease.leaseId());

      // 3. Assert
      assertThat(released).isFalse();
      assertThat(redisTemplate.opsForValue().get(key(EVENT_ID))).isEqualTo("COMPLETED");
    }
  }

  @Nested
  @DisplayName("Expiração")
  class ExpirationTests {

    @Test
    @DisplayName("Deve expirar estados PROCESSING e COMPLETED")
    void shouldExpireProcessingAndCompletedStates() {
      // 1. Arrange
      var processingEventId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac101");
      var completedEventId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac102");
      var ttl = Duration.ofMillis(100);

      // 2. Act
      adapter.acquireProcessingLease(processingEventId, ttl);
      var completedLease = adapter.acquireProcessingLease(completedEventId, PROCESSING_LEASE_TTL);
      adapter.markAsCompleted(completedEventId, completedLease.leaseId(), ttl);

      // 3. Assert
      awaitKeyExpiration(key(processingEventId));
      awaitKeyExpiration(key(completedEventId));
    }
  }

  @Nested
  @DisplayName("Validação de parâmetros")
  class ParameterValidationTests {

    @Test
    @DisplayName("Deve rejeitar eventId nulo")
    void shouldRejectNullEventId() {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> adapter.acquireProcessingLease(null, PROCESSING_LEASE_TTL))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("eventId must not be null");
    }

    @Test
    @DisplayName("Deve rejeitar leaseId nulo")
    void shouldRejectNullLeaseId() {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> adapter.releaseProcessingLease(EVENT_ID, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("leaseId must not be null");
    }

    @Test
    @DisplayName("Deve rejeitar TTL nulo")
    void shouldRejectNullTtl() {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> adapter.acquireProcessingLease(EVENT_ID, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("processingLeaseTtl must not be null");
    }

    @Test
    @DisplayName("Deve rejeitar TTL não positivo")
    void shouldRejectNonPositiveTtl() {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(
              () -> adapter.markAsCompleted(EVENT_ID, UNKNOWN_LEASE_ID, Duration.ZERO))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("idempotencyTtl must be positive");
    }

    @Test
    @DisplayName("Deve rejeitar TTL menor que um milissegundo")
    void shouldRejectTtlBelowOneMillisecond() {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> adapter.acquireProcessingLease(EVENT_ID, Duration.ofNanos(1)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("processingLeaseTtl must be at least 1 millisecond");
    }
  }

  private void assertTtlIsAtMost(String key, Duration expectedTtl) {
    var ttlMillis = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
    assertThat(ttlMillis).isPositive().isLessThanOrEqualTo(expectedTtl.toMillis());
  }

  private void awaitKeyExpiration(String key) {
    var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    while (Boolean.TRUE.equals(redisTemplate.hasKey(key)) && System.nanoTime() < deadline) {
      try {
        Thread.sleep(10);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for Redis key expiration", exception);
      }
    }
    assertThat(redisTemplate.hasKey(key)).isFalse();
  }

  private void deleteIdempotencyKeys() {
    Set<String> keys = redisTemplate.keys(KEY_PATTERN);
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }

  private String key(UUID eventId) {
    return KEY_PREFIX + eventId;
  }

  private String processingState(UUID leaseId) {
    return "PROCESSING:" + leaseId;
  }
}
