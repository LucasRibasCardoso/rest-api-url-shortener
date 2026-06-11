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
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

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
        "{\"userId\":\"user-123\"}",
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
}
