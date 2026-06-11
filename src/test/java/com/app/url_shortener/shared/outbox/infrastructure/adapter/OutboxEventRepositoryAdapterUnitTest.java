package com.app.url_shortener.shared.outbox.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.app.url_shortener.shared.outbox.domain.model.OutboxAggregateId;
import com.app.url_shortener.shared.outbox.domain.model.OutboxAggregateType;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEvent;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEventType;
import com.app.url_shortener.shared.outbox.infrastructure.mapper.OutboxEventPersistenceMapper;
import com.app.url_shortener.shared.outbox.infrastructure.model.OutboxEventEntity;
import com.app.url_shortener.shared.outbox.infrastructure.repository.OutboxEventJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Adaptador de Repositório Outbox")
class OutboxEventRepositoryAdapterUnitTest {

  @Mock
  private OutboxEventJpaRepository outboxEventJpaRepository;

  @Mock
  private OutboxEventPersistenceMapper outboxEventPersistenceMapper;

  @InjectMocks
  private OutboxEventRepositoryAdapter adapter;

  @Nested
  @DisplayName("Persistência")
  class SaveTests {

    @Test
    @DisplayName("Deve mapear, salvar e retornar evento persistido")
    void shouldMapSaveAndReturnPersistedEvent() {
      // 1. Arrange
      var event = pendingEvent(UUID.fromString("019a1a60-8e31-73b0-bc44-238e6aea0001"));
      var entity = entity(event.getId());
      var savedEntity = entity(event.getId());
      var savedEvent = pendingEvent(event.getId());

      given(outboxEventPersistenceMapper.toEntity(event)).willReturn(entity);
      given(outboxEventJpaRepository.save(entity)).willReturn(savedEntity);
      given(outboxEventPersistenceMapper.toDomain(savedEntity)).willReturn(savedEvent);

      // 2. Act
      var result = adapter.save(event);

      // 3. Assert
      assertThat(result).isSameAs(savedEvent);

      verify(outboxEventPersistenceMapper).toEntity(event);
      verify(outboxEventJpaRepository).save(entity);
      verify(outboxEventPersistenceMapper).toDomain(savedEntity);
      verifyNoMoreInteractions(outboxEventPersistenceMapper, outboxEventJpaRepository);
    }

    @Test
    @DisplayName("Deve propagar falha do repositório sem mapear retorno")
    void shouldPropagateRepositoryFailureWithoutMappingResult() {
      // 1. Arrange
      var event = pendingEvent(UUID.fromString("019a1a60-8e31-73b0-bc44-238e6aea0004"));
      var entity = entity(event.getId());
      var exception = new IllegalStateException("Falha ao persistir evento Outbox.");

      given(outboxEventPersistenceMapper.toEntity(event)).willReturn(entity);
      given(outboxEventJpaRepository.save(entity)).willThrow(exception);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> adapter.save(event));

      // 3. Assert
      throwableAssert.isSameAs(exception);

      verify(outboxEventPersistenceMapper).toEntity(event);
      verify(outboxEventJpaRepository).save(entity);
      verifyNoMoreInteractions(outboxEventPersistenceMapper, outboxEventJpaRepository);
    }
  }

  private static OutboxEvent pendingEvent(UUID id) {
    return OutboxEvent.createPending(
        id,
        OutboxAggregateType.of("USER"),
        OutboxAggregateId.of("user-123"),
        OutboxEventType.of("EMAIL_VERIFICATION_REQUESTED"),
        "{\"userId\":\"user-123\"}",
        Instant.parse("2026-06-10T20:00:00Z"));
  }

  private static OutboxEventEntity entity(UUID id) {
    return new OutboxEventEntity(
        id,
        "USER",
        "user-123",
        "EMAIL_VERIFICATION_REQUESTED",
        1,
        "{\"userId\":\"user-123\"}",
        com.app.url_shortener.shared.outbox.domain.model.OutboxEventStatus.PENDING,
        0,
        null,
        Instant.parse("2026-06-10T20:00:00Z"),
        null,
        null);
  }
}
