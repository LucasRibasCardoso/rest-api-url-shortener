package com.app.url_shortener.iam.infrastructure.notification.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.app.url_shortener.iam.application.event.EmailVerificationReason;
import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import com.app.url_shortener.iam.application.event.EmailVerificationRequestedPayload;
import com.app.url_shortener.iam.application.event.IamOutboxEventTypes;
import com.app.url_shortener.iam.application.service.EmailVerificationEventProcessorService;
import com.app.url_shortener.iam.domain.exception.auth.InvalidEmailVerificationEventException;
import com.app.url_shortener.shared.outbox.application.message.OutboxMessageEnvelope;
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

  @Mock
  private EmailVerificationEventProcessorService processorService;

  private ObjectMapper objectMapper;
  private EmailVerificationEventConsumer consumer;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    consumer = new EmailVerificationEventConsumer(objectMapper, processorService);
  }

  @Nested
  @DisplayName("Consumo")
  class ConsumeTests {

    @Test
    @DisplayName("Deve delegar evento válido para o processador")
    void shouldDelegateValidEventToProcessor() {
      // 1. Arrange
      var event = event();

      // 2. Act
      consumer.consume(envelope(event));

      // 3. Assert
      verify(processorService).process(event);
    }

    @Test
    @DisplayName("Deve rejeitar envelope nulo com exceção específica")
    void shouldRejectNullEnvelopeWithSpecificException() {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> consumer.consume(null))
          .isInstanceOf(InvalidEmailVerificationEventException.class)
          .hasMessage("Evento de verificação de e-mail inválido. envelope must not be null");
      verifyNoInteractions(processorService);
    }

    @Test
    @DisplayName("Deve rejeitar tipo de evento incompatível com exceção específica")
    void shouldRejectUnsupportedEventTypeWithSpecificException() {
      // 1. Arrange
      var event = event();
      var envelope = envelope(event, "UNSUPPORTED_EVENT", 1);

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> consumer.consume(envelope))
          .isInstanceOf(InvalidEmailVerificationEventException.class)
          .hasMessage("Evento de verificação de e-mail inválido. Unsupported outbox event type: UNSUPPORTED_EVENT");
      verifyNoInteractions(processorService);
    }

    @Test
    @DisplayName("Deve rejeitar versão de schema incompatível com exceção específica")
    void shouldRejectUnsupportedSchemaVersionWithSpecificException() {
      // 1. Arrange
      var event = event();
      var envelope = envelope(event, IamOutboxEventTypes.EMAIL_VERIFICATION_REQUESTED, 2);

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> consumer.consume(envelope))
          .isInstanceOf(InvalidEmailVerificationEventException.class)
          .hasMessage("Evento de verificação de e-mail inválido. Unsupported outbox schema version: 2");
      verifyNoInteractions(processorService);
    }

    @Test
    @DisplayName("Deve rejeitar payload incompatível com exceção específica")
    void shouldRejectInvalidPayloadWithSpecificException() {
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

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> consumer.consume(envelope))
          .isInstanceOf(InvalidEmailVerificationEventException.class)
          .hasMessage("Evento de verificação de e-mail inválido. Invalid email verification outbox payload");
      verifyNoInteractions(processorService);
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
