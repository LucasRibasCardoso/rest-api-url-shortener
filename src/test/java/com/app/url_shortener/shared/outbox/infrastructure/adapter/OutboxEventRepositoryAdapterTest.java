package com.app.url_shortener.shared.outbox.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.url_shortener.config.BaseDataJpaSliceTest;
import com.app.url_shortener.shared.outbox.domain.model.OutboxAggregateId;
import com.app.url_shortener.shared.outbox.domain.model.OutboxAggregateType;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEvent;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEventStatus;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEventType;
import com.app.url_shortener.shared.outbox.infrastructure.mapper.OutboxEventPersistenceMapperImpl;
import com.app.url_shortener.shared.outbox.infrastructure.repository.OutboxEventJpaRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("jpa-slice")
@Import({OutboxEventRepositoryAdapter.class, OutboxEventPersistenceMapperImpl.class})
@DisplayName("Slice Data JPA - Adaptador de Repositório Outbox")
class OutboxEventRepositoryAdapterTest extends BaseDataJpaSliceTest {

  private static final Instant CREATED_AT = Instant.parse("2026-06-10T20:00:00Z");

  @Autowired
  private OutboxEventRepositoryAdapter adapter;

  @Autowired
  private OutboxEventJpaRepository repository;

  @Autowired
  private TestEntityManager entityManager;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @Nested
  @DisplayName("Persistência")
  class PersistenceTests {

    @Test
    @DisplayName("Deve persistir JSONB e restaurar evento pendente")
    void shouldPersistJsonbAndRestorePendingEvent() {
      // 1. Arrange
      var event = pendingEvent(UUID.fromString("019a1a60-8e31-73b0-bc44-238e6aea0101"));

      // 2. Act
      var saved = adapter.save(event);
      entityManager.flush();
      entityManager.clear();
      var restored = repository.findById(event.getId()).orElseThrow();

      // 3. Assert
      assertThat(saved).isEqualTo(event);
      assertThat(restored.getAggregateType()).isEqualTo("USER");
      assertThat(restored.getAggregateId()).isEqualTo("user-123");
      assertThat(restored.getEventType()).isEqualTo("EMAIL_VERIFICATION_REQUESTED");
      assertThat(restored.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT payload ->> 'userId' FROM outbox_events WHERE id = ?",
                  String.class,
                  event.getId()))
          .isEqualTo("user-123");
    }

    @Test
    @DisplayName("Deve persistir lote com eventos publicados, em retry e falhos")
    void shouldPersistBatchWithPublishedRetryAndFailedEvents() {
      // 1. Arrange
      var transitionAt = CREATED_AT.plusSeconds(30);
      var published =
          pendingEvent(UUID.fromString("019a1a60-8e31-73b0-bc44-238e6aea0111"));
      var retry = pendingEvent(UUID.fromString("019a1a60-8e31-73b0-bc44-238e6aea0112"));
      var failed = pendingEvent(UUID.fromString("019a1a60-8e31-73b0-bc44-238e6aea0113"));
      published.markAsPublished(transitionAt);
      retry.registerFailure("temporary failure", transitionAt, 3, Duration.ofSeconds(30));
      failed.registerFailure("permanent failure", transitionAt, 1, Duration.ofSeconds(30));

      // 2. Act
      adapter.saveAll(List.of(published, retry, failed));
      entityManager.flush();
      entityManager.clear();

      // 3. Assert
      var restoredPublished = repository.findById(published.getId()).orElseThrow();
      var restoredRetry = repository.findById(retry.getId()).orElseThrow();
      var restoredFailed = repository.findById(failed.getId()).orElseThrow();
      assertThat(restoredPublished.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
      assertThat(restoredPublished.getPublishedAt()).isEqualTo(transitionAt);
      assertThat(restoredRetry.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
      assertThat(restoredRetry.getAttempts()).isEqualTo(1);
      assertThat(restoredRetry.getNextAttemptAt()).isEqualTo(transitionAt.plusSeconds(30));
      assertThat(restoredFailed.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
      assertThat(restoredFailed.getAttempts()).isEqualTo(1);
      assertThat(restoredFailed.getNextAttemptAt()).isNull();
    }
  }

  @Nested
  @DisplayName("Busca de eventos pendentes")
  class FindPendingToPublishTests {

    @Test
    @DisplayName("Deve retornar somente eventos elegíveis respeitando limite e ordenação")
    void shouldReturnOnlyEligibleEventsRespectingLimitAndOrder() {
      // 1. Arrange
      var now = CREATED_AT.plusSeconds(60);
      var firstId = UUID.fromString("019a1a60-8e31-73b0-bc44-238e6aea0301");
      var secondId = UUID.fromString("019a1a60-8e31-73b0-bc44-238e6aea0302");
      var futureRetryId = UUID.fromString("019a1a60-8e31-73b0-bc44-238e6aea0303");
      var publishedId = UUID.fromString("019a1a60-8e31-73b0-bc44-238e6aea0304");

      insertRaw(firstId, "PENDING", 0, null, null, null);
      insertRaw(secondId, "PENDING", 1, "temporary failure", null, now);
      insertRaw(
          futureRetryId, "PENDING", 1, "temporary failure", null, now.plusSeconds(30));
      insertRaw(publishedId, "PUBLISHED", 0, null, now, null);

      // 2. Act
      var result = adapter.findPendingToPublish(now, 2);

      // 3. Assert
      assertThat(result).extracting(OutboxEvent::getId).containsExactly(firstId, secondId);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Deve ignorar evento bloqueado por outra transação")
    void shouldSkipEventLockedByAnotherTransaction() throws Exception {
      // 1. Arrange
      var eventId = UUID.fromString("019a1a60-8e31-73b0-bc44-238e6aea0310");
      adapter.save(pendingEvent(eventId));

      var lockAcquired = new CountDownLatch(1);
      var releaseLock = new CountDownLatch(1);
      var transactionTemplate = new TransactionTemplate(transactionManager);

      try (var executor = Executors.newFixedThreadPool(2)) {
        var lockingTransaction =
            executor.submit(
                () ->
                    transactionTemplate.execute(
                        status -> {
                          var locked = adapter.findPendingToPublish(CREATED_AT.plusSeconds(30), 1);
                          lockAcquired.countDown();
                          await(releaseLock);
                          return locked;
                        }));

        assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

        // 2. Act
        var skipped =
            executor
                .submit(
                    () ->
                        transactionTemplate.execute(
                            status ->
                                adapter.findPendingToPublish(
                                    CREATED_AT.plusSeconds(30), 1)))
                .get(5, TimeUnit.SECONDS);

        releaseLock.countDown();
        var locked = lockingTransaction.get(5, TimeUnit.SECONDS);

        // 3. Assert
        assertThat(locked).extracting(OutboxEvent::getId).containsExactly(eventId);
        assertThat(skipped).isEmpty();
      } finally {
        releaseLock.countDown();
        repository.deleteById(eventId);
      }
    }
  }

  @Nested
  @DisplayName("Constraints da migration")
  class MigrationConstraintTests {

    @Test
    @DisplayName("Deve rejeitar status fora dos valores permitidos")
    void shouldRejectInvalidStatus() {
      // 1. Arrange
      var id = UUID.fromString("019a1a60-8e31-73b0-bc44-238e6aea0201");

      // 2. Act
      var throwableAssert =
          assertThatThrownBy(
              () ->
                  insertRaw(
                      id, "INVALID", 0, null, null, null));

      // 3. Assert
      throwableAssert
          .hasRootCauseInstanceOf(PSQLException.class)
          .hasStackTraceContaining("violates check constraint");
    }

    @Test
    @DisplayName("Deve rejeitar evento pendente em retry sem metadados completos")
    void shouldRejectRetriedPendingEventWithoutCompleteMetadata() {
      // 1. Arrange
      var id = UUID.fromString("019a1a60-8e31-73b0-bc44-238e6aea0202");

      // 2. Act
      var throwableAssert =
          assertThatThrownBy(() -> insertRaw(id, "PENDING", 1, "failure", null, null));

      // 3. Assert
      throwableAssert
          .hasRootCauseInstanceOf(PSQLException.class)
          .hasStackTraceContaining("chk_outbox_events_state");
    }

    @Test
    @DisplayName("Deve rejeitar payload JSONB que não seja objeto")
    void shouldRejectNonObjectJsonbPayload() {
      // 1. Arrange
      var id = UUID.fromString("019a1a60-8e31-73b0-bc44-238e6aea0203");

      // 2. Act
      var throwableAssert =
          assertThatThrownBy(
              () -> insertRaw(id, "[]", "PENDING", 0, null, null, null));

      // 3. Assert
      throwableAssert
          .hasRootCauseInstanceOf(PSQLException.class)
          .hasStackTraceContaining("chk_outbox_events_payload_object");
    }
  }

  private static OutboxEvent pendingEvent(UUID id) {
    return OutboxEvent.createPending(
        id,
        OutboxAggregateType.of("USER"),
        OutboxAggregateId.of("user-123"),
        OutboxEventType.of("EMAIL_VERIFICATION_REQUESTED"),
        "{\"userId\":\"user-123\"}",
        CREATED_AT);
  }

  private void insertRaw(
      UUID id,
      String status,
      int attempts,
      String lastError,
      Instant publishedAt,
      Instant nextAttemptAt) {
    insertRaw(
        id,
        "{\"userId\":\"user-123\"}",
        status,
        attempts,
        lastError,
        publishedAt,
        nextAttemptAt);
  }

  private void insertRaw(
      UUID id,
      String payload,
      String status,
      int attempts,
      String lastError,
      Instant publishedAt,
      Instant nextAttemptAt) {
    jdbcTemplate.update(
        """
        INSERT INTO outbox_events (
            id, aggregate_type, aggregate_id, event_type, schema_version, payload, status,
            attempts, last_error, created_at, published_at, next_attempt_at
        ) VALUES (?, ?, ?, ?, ?, CAST(? AS JSONB), ?, ?, ?, ?, ?, ?)
        """,
        id,
        "USER",
        "user-123",
        "EMAIL_VERIFICATION_REQUESTED",
        1,
        payload,
        status,
        attempts,
        lastError,
        Timestamp.from(CREATED_AT),
        toTimestamp(publishedAt),
        toTimestamp(nextAttemptAt));
  }

  private static Timestamp toTimestamp(Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting to release database lock");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting to release database lock", exception);
    }
  }
}
