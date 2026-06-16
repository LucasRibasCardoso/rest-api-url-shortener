package com.app.url_shortener.iam.infrastructure.notification.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.app.url_shortener.iam.application.event.EmailVerificationReason;
import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import com.app.url_shortener.iam.application.event.EmailVerificationRequestedPayload;
import com.app.url_shortener.iam.application.event.IamOutboxEventTypes;
import com.app.url_shortener.iam.application.policy.EmailVerificationPolicy;
import com.app.url_shortener.iam.application.port.output.EmailVerificationIdempotencyPort;
import com.app.url_shortener.iam.application.port.output.model.EmailVerificationProcessingLease;
import com.app.url_shortener.iam.application.service.EmailVerificationEventProcessorService;
import com.app.url_shortener.iam.domain.exception.auth.EmailVerificationEventAlreadyProcessingException;
import com.app.url_shortener.iam.domain.exception.auth.EmailVerificationProcessingLeaseLostException;
import com.app.url_shortener.shared.outbox.application.message.OutboxMessageEnvelope;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Consumer de Verificação de Email")
class EmailVerificationEventConsumerTest {

  private static final Duration CODE_TTL = Duration.ofMinutes(10);
  private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(4);
  private static final Duration PROCESSING_LEASE_TTL = Duration.ofMinutes(2);
  private static final UUID LEASE_ID =
      UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac200");

  @Mock
  private EmailVerificationIdempotencyPort idempotencyPort;

  @Mock
  private EmailVerificationEventProcessorService processorService;

  private ObjectMapper objectMapper;
  private EmailVerificationEventConsumer consumer;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    var policy = new EmailVerificationPolicy(CODE_TTL, IDEMPOTENCY_TTL, PROCESSING_LEASE_TTL);
    consumer =
        new EmailVerificationEventConsumer(objectMapper, policy, idempotencyPort, processorService);
  }

  @Nested
  @DisplayName("Consumo")
  class ConsumeTests {

    @Test
    @DisplayName("Deve processar lease adquirido e marcar evento como concluído")
    void shouldProcessAcquiredLeaseAndMarkEventAsCompleted() {
      // 1. Arrange
      var event = event();
      given(idempotencyPort.acquireProcessingLease(event.eventId(), PROCESSING_LEASE_TTL))
          .willReturn(EmailVerificationProcessingLease.acquired(LEASE_ID));
      given(idempotencyPort.markAsCompleted(event.eventId(), LEASE_ID, IDEMPOTENCY_TTL))
          .willReturn(true);

      // 2. Act
      consumer.consume(envelope(event));

      // 3. Assert
      verify(idempotencyPort).acquireProcessingLease(event.eventId(), PROCESSING_LEASE_TTL);
      verify(processorService).process(event);
      verify(idempotencyPort).markAsCompleted(event.eventId(), LEASE_ID, IDEMPOTENCY_TTL);
      verifyNoMoreInteractions(idempotencyPort, processorService);
    }

    @Test
    @DisplayName("Deve gerar retry quando outro consumer estiver processando")
    void shouldThrowRetriableExceptionWhenEventIsProcessing() {
      // 1. Arrange
      var event = event();
      given(idempotencyPort.acquireProcessingLease(event.eventId(), PROCESSING_LEASE_TTL))
          .willReturn(EmailVerificationProcessingLease.processing());

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> consumer.consume(envelope(event)));

      // 3. Assert
      throwableAssert
          .isInstanceOf(EmailVerificationEventAlreadyProcessingException.class)
          .hasMessage(
              "Evento de verificação de e-mail já em processamento. eventId=" + event.eventId());
      verify(idempotencyPort).acquireProcessingLease(event.eventId(), PROCESSING_LEASE_TTL);
      verifyNoInteractions(processorService);
      verifyNoMoreInteractions(idempotencyPort);
    }

    @Test
    @DisplayName("Deve ignorar evento já concluído")
    void shouldIgnoreCompletedEvent() {
      // 1. Arrange
      var event = event();
      given(idempotencyPort.acquireProcessingLease(event.eventId(), PROCESSING_LEASE_TTL))
          .willReturn(EmailVerificationProcessingLease.completed());

      // 2. Act
      consumer.consume(envelope(event));

      // 3. Assert
      verify(idempotencyPort).acquireProcessingLease(event.eventId(), PROCESSING_LEASE_TTL);
      verifyNoInteractions(processorService);
      verifyNoMoreInteractions(idempotencyPort);
    }

    @Test
    @DisplayName("Deve liberar lease e propagar falha do processador")
    void shouldReleaseLeaseAndPropagateProcessorFailure() {
      // 1. Arrange
      var event = event();
      var failure = new IllegalStateException("Email unavailable");
      given(idempotencyPort.acquireProcessingLease(event.eventId(), PROCESSING_LEASE_TTL))
          .willReturn(EmailVerificationProcessingLease.acquired(LEASE_ID));
      doThrow(failure).when(processorService).process(event);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> consumer.consume(envelope(event)));

      // 3. Assert
      throwableAssert.isSameAs(failure);
      verify(idempotencyPort).releaseProcessingLease(event.eventId(), LEASE_ID);
      verifyNoMoreInteractions(idempotencyPort, processorService);
    }

    @Test
    @DisplayName("Deve preservar falha de liberação como exceção suprimida")
    void shouldPreserveReleaseFailureAsSuppressedException() {
      // 1. Arrange
      var event = event();
      var processingFailure = new IllegalStateException("Email unavailable");
      var releaseFailure = new IllegalStateException("Redis unavailable");
      given(idempotencyPort.acquireProcessingLease(event.eventId(), PROCESSING_LEASE_TTL))
          .willReturn(EmailVerificationProcessingLease.acquired(LEASE_ID));
      doThrow(processingFailure).when(processorService).process(event);
      doThrow(releaseFailure)
          .when(idempotencyPort)
          .releaseProcessingLease(event.eventId(), LEASE_ID);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> consumer.consume(envelope(event)));

      // 3. Assert
      throwableAssert.isSameAs(processingFailure).hasSuppressedException(releaseFailure);
      verifyNoMoreInteractions(idempotencyPort, processorService);
    }

    @Test
    @DisplayName("Deve liberar lease quando marcação de conclusão falhar")
    void shouldReleaseLeaseWhenMarkAsCompletedFails() {
      // 1. Arrange
      var event = event();
      var failure = new IllegalStateException("Redis unavailable");
      given(idempotencyPort.acquireProcessingLease(event.eventId(), PROCESSING_LEASE_TTL))
          .willReturn(EmailVerificationProcessingLease.acquired(LEASE_ID));
      doThrow(failure)
          .when(idempotencyPort)
          .markAsCompleted(event.eventId(), LEASE_ID, IDEMPOTENCY_TTL);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> consumer.consume(envelope(event)));

      // 3. Assert
      throwableAssert.isSameAs(failure);
      verify(processorService).process(event);
      verify(idempotencyPort).releaseProcessingLease(event.eventId(), LEASE_ID);
      verifyNoMoreInteractions(idempotencyPort, processorService);
    }

    @Test
    @DisplayName("Deve gerar retry quando perder o lease antes da conclusão")
    void shouldThrowRetriableExceptionWhenLeaseIsLostBeforeCompletion() {
      // 1. Arrange
      var event = event();
      given(idempotencyPort.acquireProcessingLease(event.eventId(), PROCESSING_LEASE_TTL))
          .willReturn(EmailVerificationProcessingLease.acquired(LEASE_ID));
      given(idempotencyPort.markAsCompleted(event.eventId(), LEASE_ID, IDEMPOTENCY_TTL))
          .willReturn(false);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> consumer.consume(envelope(event)));

      // 3. Assert
      throwableAssert
          .isInstanceOf(EmailVerificationProcessingLeaseLostException.class)
          .hasMessage("Lease de processamento de verificação de e-mail perdido. eventId=" + event.eventId());
      verify(processorService).process(event);
      verify(idempotencyPort).releaseProcessingLease(event.eventId(), LEASE_ID);
      verifyNoMoreInteractions(idempotencyPort, processorService);
    }

    @Test
    @DisplayName("Deve rejeitar tipo de evento incompatível antes do processamento")
    void shouldRejectUnsupportedEventTypeBeforeProcessing() {
      // 1. Arrange
      var event = event();
      var envelope = envelope(event, "UNSUPPORTED_EVENT", 1);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> consumer.consume(envelope));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Unsupported outbox event type: UNSUPPORTED_EVENT");
      verifyNoInteractions(idempotencyPort, processorService);
    }

    @Test
    @DisplayName("Deve rejeitar versão de schema incompatível antes do processamento")
    void shouldRejectUnsupportedSchemaVersionBeforeProcessing() {
      // 1. Arrange
      var event = event();
      var envelope = envelope(event, IamOutboxEventTypes.EMAIL_VERIFICATION_REQUESTED, 2);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> consumer.consume(envelope));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Unsupported outbox schema version: 2");
      verifyNoInteractions(idempotencyPort, processorService);
    }

    @Test
    @DisplayName("Deve rejeitar payload incompatível antes do processamento")
    void shouldRejectInvalidPayloadBeforeProcessing() {
      // 1. Arrange
      var event = event();
      var envelope =
          new OutboxMessageEnvelope(
              event.eventId(),
              IamOutboxEventTypes.EMAIL_VERIFICATION_REQUESTED,
              1,
              IamOutboxEventTypes.AGGREGATE_USER,
              event.userId().toString(),
              event.occurredAt(),
              objectMapper.valueToTree("invalid"));

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> consumer.consume(envelope));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Invalid email verification outbox payload");
      verifyNoInteractions(idempotencyPort, processorService);
    }
  }

  private EmailVerificationRequestedEvent event() {
    return new EmailVerificationRequestedEvent(
        UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac100"),
        UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac101"),
        "user@email.com",
        EmailVerificationReason.REGISTER,
        Instant.parse("2026-06-09T12:00:00Z"));
  }

  private OutboxMessageEnvelope envelope(EmailVerificationRequestedEvent event) {
    return envelope(event, IamOutboxEventTypes.EMAIL_VERIFICATION_REQUESTED, 1);
  }

  private OutboxMessageEnvelope envelope(
      EmailVerificationRequestedEvent event, String eventType, int schemaVersion) {
    var payload =
        objectMapper.valueToTree(
            new EmailVerificationRequestedPayload(event.userId(), event.email(), event.reason()));

    return new OutboxMessageEnvelope(
        event.eventId(),
        eventType,
        schemaVersion,
        IamOutboxEventTypes.AGGREGATE_USER,
        event.userId().toString(),
        event.occurredAt(),
        payload);
  }
}
