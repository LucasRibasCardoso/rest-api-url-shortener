package com.app.url_shortener.shared.outbox.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.app.url_shortener.shared.outbox.application.policy.OutboxPublisherPolicy;
import com.app.url_shortener.shared.outbox.application.port.OutboxEventPublisherPort;
import com.app.url_shortener.shared.outbox.application.port.OutboxEventRepositoryPort;
import com.app.url_shortener.shared.outbox.domain.exception.OutboxPublishException;
import com.app.url_shortener.shared.outbox.domain.model.OutboxAggregateId;
import com.app.url_shortener.shared.outbox.domain.model.OutboxAggregateType;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEvent;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEventStatus;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEventType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Serviço Publisher Outbox")
class OutboxPublisherServiceTest {

  private static final int BATCH_SIZE = 20;
  private static final int MAX_ATTEMPTS = 3;
  private static final Duration RETRY_DELAY = Duration.ofSeconds(30);
  private static final Instant CREATED_AT = Instant.parse("2026-06-10T10:00:00Z");

  @Mock private OutboxEventRepositoryPort outboxEventRepositoryPort;

  @Mock private OutboxEventPublisherPort outboxEventPublisherPort;

  private OutboxPublisherService service;

  @BeforeEach
  void setUp() {
    service = service(MAX_ATTEMPTS);
  }

  @Nested
  @DisplayName("Publicação de eventos pendentes")
  class PublishPendingEventsTests {

    @Test
    @DisplayName("Deve publicar e persistir evento como publicado")
    void shouldPublishAndPersistEventAsPublished() {
      // 1. Arrange
      var event = event("019a1a60-8e31-73b0-bc44-238e6aea0001");
      var events = List.of(event);
      given(outboxEventRepositoryPort.findPendingToPublish(any(Instant.class), eq(BATCH_SIZE)))
          .willReturn(events);

      // 2. Act
      service.publishPendingEvents();

      // 3. Assert
      var nowCaptor = ArgumentCaptor.forClass(Instant.class);
      verify(outboxEventRepositoryPort).findPendingToPublish(nowCaptor.capture(), eq(BATCH_SIZE));
      verify(outboxEventPublisherPort).publish(event);
      verify(outboxEventRepositoryPort).saveAll(same(events));

      assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
      assertThat(event.getPublishedAt()).isEqualTo(nowCaptor.getValue());
      assertThat(event.getAttempts()).isZero();
      assertThat(event.getLastError()).isNull();
      assertThat(event.getNextAttemptAt()).isNull();
      verifyNoMoreInteractions(outboxEventRepositoryPort, outboxEventPublisherPort);
    }

    @Test
    @DisplayName("Deve registrar retry e persistir evento quando publicação falhar")
    void shouldRegisterRetryAndPersistEventWhenPublicationFails() {
      // 1. Arrange
      var event = event("019a1a60-8e31-73b0-bc44-238e6aea0002");
      var events = List.of(event);
      given(outboxEventRepositoryPort.findPendingToPublish(any(Instant.class), eq(BATCH_SIZE)))
          .willReturn(events);
      doThrow(new OutboxPublishException(new IllegalStateException("SQS unavailable")))
          .when(outboxEventPublisherPort)
          .publish(event);

      // 2. Act
      service.publishPendingEvents();

      // 3. Assert
      var nowCaptor = ArgumentCaptor.forClass(Instant.class);
      verify(outboxEventRepositoryPort).findPendingToPublish(nowCaptor.capture(), eq(BATCH_SIZE));
      verify(outboxEventPublisherPort).publish(event);
      verify(outboxEventRepositoryPort).saveAll(same(events));

      assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
      assertThat(event.getAttempts()).isEqualTo(1);
      assertThat(event.getLastError()).isEqualTo("Ocorreu um erro ao publicar o evento de outbox.");
      assertThat(event.getNextAttemptAt()).isEqualTo(nowCaptor.getValue().plus(RETRY_DELAY));
      assertThat(event.getPublishedAt()).isNull();
      verifyNoMoreInteractions(outboxEventRepositoryPort, outboxEventPublisherPort);
    }

    @Test
    @DisplayName("Deve marcar evento como falho ao esgotar tentativas")
    void shouldMarkEventAsFailedWhenMaximumAttemptsIsReached() {
      // 1. Arrange
      service = service(1);
      var event = event("019a1a60-8e31-73b0-bc44-238e6aea0003");
      var events = List.of(event);
      given(outboxEventRepositoryPort.findPendingToPublish(any(Instant.class), eq(BATCH_SIZE)))
          .willReturn(events);
      doThrow(new OutboxPublishException(new IllegalStateException("Permanent failure")))
          .when(outboxEventPublisherPort)
          .publish(event);

      // 2. Act
      service.publishPendingEvents();

      // 3. Assert
      verify(outboxEventPublisherPort).publish(event);
      verify(outboxEventRepositoryPort).saveAll(same(events));
      assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
      assertThat(event.getAttempts()).isEqualTo(1);
      assertThat(event.getLastError()).isEqualTo("Ocorreu um erro ao publicar o evento de outbox.");
      assertThat(event.getNextAttemptAt()).isNull();
    }

    @Test
    @DisplayName("Deve continuar processando lote após falha de um evento")
    void shouldContinueProcessingBatchAfterEventFailure() {
      // 1. Arrange
      var failedEvent = event("019a1a60-8e31-73b0-bc44-238e6aea0004");
      var publishedEvent = event("019a1a60-8e31-73b0-bc44-238e6aea0005");
      var events = List.of(failedEvent, publishedEvent);
      given(outboxEventRepositoryPort.findPendingToPublish(any(Instant.class), eq(BATCH_SIZE)))
          .willReturn(events);
      doThrow(new OutboxPublishException(new IllegalStateException("Temporary failure")))
          .when(outboxEventPublisherPort)
          .publish(failedEvent);

      // 2. Act
      service.publishPendingEvents();

      // 3. Assert
      var inOrder = inOrder(outboxEventPublisherPort, outboxEventRepositoryPort);
      inOrder.verify(outboxEventPublisherPort).publish(failedEvent);
      inOrder.verify(outboxEventPublisherPort).publish(publishedEvent);
      inOrder.verify(outboxEventRepositoryPort).saveAll(same(events));
      assertThat(failedEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
      assertThat(publishedEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
    }

    @Test
    @DisplayName("Deve propagar falha inesperada não traduzida pelo port")
    void shouldPropagateUnexpectedFailureNotTranslatedByPort() {
      // 1. Arrange
      var event = event("019a1a60-8e31-73b0-bc44-238e6aea0006");
      var events = List.of(event);
      var exception = new IllegalStateException("Unexpected failure");
      given(outboxEventRepositoryPort.findPendingToPublish(any(Instant.class), eq(BATCH_SIZE)))
          .willReturn(events);
      doThrow(exception).when(outboxEventPublisherPort).publish(event);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> service.publishPendingEvents());

      // 3. Assert
      throwableAssert.isSameAs(exception);
      verify(outboxEventRepositoryPort).findPendingToPublish(any(Instant.class), eq(BATCH_SIZE));
      verify(outboxEventPublisherPort).publish(event);
      verifyNoMoreInteractions(outboxEventRepositoryPort, outboxEventPublisherPort);
    }

    @Test
    @DisplayName("Deve persistir lote vazio sem tentar publicar eventos")
    void shouldPersistEmptyBatchWithoutPublishingEvents() {
      // 1. Arrange
      var events = List.<OutboxEvent>of();
      given(outboxEventRepositoryPort.findPendingToPublish(any(Instant.class), eq(BATCH_SIZE)))
          .willReturn(events);

      // 2. Act
      service.publishPendingEvents();

      // 3. Assert
      verify(outboxEventRepositoryPort).findPendingToPublish(any(Instant.class), eq(BATCH_SIZE));
      verify(outboxEventRepositoryPort).saveAll(same(events));
      verifyNoInteractions(outboxEventPublisherPort);
      verifyNoMoreInteractions(outboxEventRepositoryPort);
    }
  }

  private OutboxPublisherService service(int maxAttempts) {
    var policy = new OutboxPublisherPolicy(BATCH_SIZE, maxAttempts, RETRY_DELAY);
    return new OutboxPublisherService(outboxEventRepositoryPort, outboxEventPublisherPort, policy);
  }

  private OutboxEvent event(String eventId) {
    return OutboxEvent.createPending(
        UUID.fromString(eventId),
        OutboxAggregateType.of("USER"),
        OutboxAggregateId.of("user-123"),
        OutboxEventType.of("EMAIL_VERIFICATION_REQUESTED"),
        "{\"userId\":\"user-123\"}",
        CREATED_AT);
  }
}
