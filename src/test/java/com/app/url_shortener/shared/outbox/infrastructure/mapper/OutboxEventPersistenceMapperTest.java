package com.app.url_shortener.shared.outbox.infrastructure.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.url_shortener.shared.outbox.domain.model.OutboxAggregateId;
import com.app.url_shortener.shared.outbox.domain.model.OutboxAggregateType;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEvent;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEventStatus;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEventType;
import com.app.url_shortener.shared.outbox.infrastructure.entity.OutboxEventEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - Mapper de Persistência Outbox")
class OutboxEventPersistenceMapperTest {

  private static final UUID EVENT_ID =
      UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
  private static final Instant CREATED_AT = Instant.parse("2026-06-12T10:00:00Z");
  private static final Instant RETRY_AT = Instant.parse("2026-06-12T10:05:00Z");
  private static final Instant PUBLISHED_AT = Instant.parse("2026-06-12T10:01:00Z");

  private final OutboxEventPersistenceMapper mapper = new OutboxEventPersistenceMapperImpl();

  @Nested
  @DisplayName("Mapeamento para entidade")
  class ToEntityTests {

    @Test
    @DisplayName("Deve mapear evento com metadados de retry para entidade")
    void shouldMapRetriedPendingEventToEntity() {
      // 1. Arrange
      var event =
          OutboxEvent.restore(
              EVENT_ID,
              OutboxAggregateType.of("USER"),
              OutboxAggregateId.of("user-123"),
              OutboxEventType.of("EMAIL_VERIFICATION_REQUESTED"),
              2,
              "{\"email\":\"user@example.com\"}",
              OutboxEventStatus.PENDING,
              1,
              "SQS unavailable",
              CREATED_AT,
              null,
              RETRY_AT);

      // 2. Act
      var entity = mapper.toEntity(event);

      // 3. Assert
      assertThat(entity.getId()).isEqualTo(EVENT_ID);
      assertThat(entity.getAggregateType()).isEqualTo("USER");
      assertThat(entity.getAggregateId()).isEqualTo("user-123");
      assertThat(entity.getEventType()).isEqualTo("EMAIL_VERIFICATION_REQUESTED");
      assertThat(entity.getSchemaVersion()).isEqualTo(2);
      assertThat(entity.getPayload()).isEqualTo("{\"email\":\"user@example.com\"}");
      assertThat(entity.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
      assertThat(entity.getAttempts()).isEqualTo(1);
      assertThat(entity.getLastError()).isEqualTo("SQS unavailable");
      assertThat(entity.getCreatedAt()).isEqualTo(CREATED_AT);
      assertThat(entity.getPublishedAt()).isNull();
      assertThat(entity.getNextAttemptAt()).isEqualTo(RETRY_AT);
    }

    @Test
    @DisplayName("Deve retornar nulo quando evento for nulo")
    void shouldReturnNullWhenEventIsNull() {
      // 1. Arrange
      OutboxEvent event = null;

      // 2. Act
      var entity = mapper.toEntity(event);

      // 3. Assert
      assertThat(entity).isNull();
    }
  }

  @Nested
  @DisplayName("Mapeamento para domínio")
  class ToDomainTests {

    @Test
    @DisplayName("Deve restaurar evento publicado a partir da entidade")
    void shouldRestorePublishedEventFromEntity() {
      // 1. Arrange
      var entity =
          entity(
              OutboxEventStatus.PUBLISHED,
              1,
              null,
              PUBLISHED_AT,
              null);

      // 2. Act
      var event = mapper.toDomain(entity);

      // 3. Assert
      assertThat(event.getId()).isEqualTo(EVENT_ID);
      assertThat(event.getAggregateType()).isEqualTo(OutboxAggregateType.of("USER"));
      assertThat(event.getAggregateId()).isEqualTo(OutboxAggregateId.of("user-123"));
      assertThat(event.getEventType())
          .isEqualTo(OutboxEventType.of("EMAIL_VERIFICATION_REQUESTED"));
      assertThat(event.getSchemaVersion()).isEqualTo(2);
      assertThat(event.getPayload()).isEqualTo("{\"email\":\"user@example.com\"}");
      assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
      assertThat(event.getAttempts()).isEqualTo(1);
      assertThat(event.getLastError()).isNull();
      assertThat(event.getCreatedAt()).isEqualTo(CREATED_AT);
      assertThat(event.getPublishedAt()).isEqualTo(PUBLISHED_AT);
      assertThat(event.getNextAttemptAt()).isNull();
    }

    @Test
    @DisplayName("Deve restaurar evento pendente com metadados de retry")
    void shouldRestoreRetriedPendingEventFromEntity() {
      // 1. Arrange
      var entity =
          entity(
              OutboxEventStatus.PENDING,
              1,
              "SQS unavailable",
              null,
              RETRY_AT);

      // 2. Act
      var event = mapper.toDomain(entity);

      // 3. Assert
      assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
      assertThat(event.getAttempts()).isEqualTo(1);
      assertThat(event.getLastError()).isEqualTo("SQS unavailable");
      assertThat(event.getPublishedAt()).isNull();
      assertThat(event.getNextAttemptAt()).isEqualTo(RETRY_AT);
    }

    @Test
    @DisplayName("Deve preservar invariantes do domínio ao restaurar entidade inválida")
    void shouldPreserveDomainInvariantsWhenRestoringInvalidEntity() {
      // 1. Arrange
      var entity = entity(OutboxEventStatus.PUBLISHED, 1, null, null, null);

      // 2. Act
      var result = assertThatThrownBy(() -> mapper.toDomain(entity));

      // 3. Assert
      result
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("PUBLISHED event must have publishedAt");
    }

    @Test
    @DisplayName("Deve retornar nulo quando entidade for nula")
    void shouldReturnNullWhenEntityIsNull() {
      // 1. Arrange
      OutboxEventEntity entity = null;

      // 2. Act
      var event = mapper.toDomain(entity);

      // 3. Assert
      assertThat(event).isNull();
    }
  }

  private static OutboxEventEntity entity(
      OutboxEventStatus status,
      int attempts,
      String lastError,
      Instant publishedAt,
      Instant nextAttemptAt) {
    return new OutboxEventEntity(
        EVENT_ID,
        "USER",
        "user-123",
        "EMAIL_VERIFICATION_REQUESTED",
        2,
        "{\"email\":\"user@example.com\"}",
        status,
        attempts,
        lastError,
        CREATED_AT,
        publishedAt,
        nextAttemptAt);
  }
}
