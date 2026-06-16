package com.app.url_shortener.shared.outbox.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.app.url_shortener.shared.outbox.application.message.OutboxMessageEnvelope;
import com.app.url_shortener.shared.outbox.domain.exception.OutboxPublishException;
import com.app.url_shortener.shared.outbox.domain.model.OutboxAggregateId;
import com.app.url_shortener.shared.outbox.domain.model.OutboxAggregateType;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEvent;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEventType;
import com.app.url_shortener.shared.outbox.infrastructure.resolver.OutboxEventQueueResolver;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Publisher de Eventos Outbox")
class OutboxEventPublisherAdapterTest {

  private static final String QUEUE_NAME = "email-verification-queue";

  @Mock
  private SqsTemplate sqsTemplate;

  @Mock
  private OutboxEventQueueResolver queueResolver;

  @Captor
  private ArgumentCaptor<OutboxMessageEnvelope> envelopeCaptor;

  private OutboxEventPublisherAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new OutboxEventPublisherAdapter(sqsTemplate, queueResolver, new ObjectMapper());
  }

  @Nested
  @DisplayName("Publicação")
  class PublishTests {

    @Test
    @DisplayName("Deve resolver fila e publicar envelope com metadados e payload objeto")
    void shouldResolveQueueAndPublishEnvelopeWithMetadataAndObjectPayload() {
      // 1. Arrange
      var event = event(validPayload());
      given(queueResolver.resolve(event.getEventType())).willReturn(QUEUE_NAME);

      // 2. Act
      adapter.publish(event);

      // 3. Assert
      verify(queueResolver).resolve(event.getEventType());
      verify(sqsTemplate).send(eq(QUEUE_NAME), envelopeCaptor.capture());

      var envelope = envelopeCaptor.getValue();
      assertAll(
          () -> assertThat(envelope.eventId()).isEqualTo(event.getId()),
          () -> assertThat(envelope.eventType()).isEqualTo(event.getEventType().value()),
          () -> assertThat(envelope.schemaVersion()).isEqualTo(event.getSchemaVersion()),
          () -> assertThat(envelope.aggregateType()).isEqualTo(event.getAggregateType().value()),
          () -> assertThat(envelope.aggregateId()).isEqualTo(event.getAggregateId().value()),
          () -> assertThat(envelope.occurredAt()).isEqualTo(event.getCreatedAt()),
          () -> assertThat(envelope.payload().isObject()).isTrue(),
          () -> assertThat(envelope.payload().get("email").asText()).isEqualTo("user@email.com"));

      verifyNoMoreInteractions(queueResolver, sqsTemplate);
    }

    @Test
    @DisplayName("Deve propagar payload persistido inválido sem enviar mensagem")
    void shouldPropagateInvalidPersistedPayloadWithoutSendingMessage() {
      // 1. Arrange
      var event = event("{invalid-json");
      given(queueResolver.resolve(event.getEventType())).willReturn(QUEUE_NAME);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> adapter.publish(event));

      // 3. Assert
      throwableAssert
          .isInstanceOf(OutboxPublishException.class)
          .hasMessage("Ocorreu um erro ao publicar o evento de outbox.")
          .hasCauseInstanceOf(JacksonException.class);

      verify(queueResolver).resolve(event.getEventType());
      verifyNoInteractions(sqsTemplate);
      verifyNoMoreInteractions(queueResolver);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "\"text\"", "123", "true"})
    @DisplayName("Deve rejeitar payload JSON que não seja objeto sem enviar mensagem")
    void shouldRejectNonObjectJsonPayloadWithoutSendingMessage(String payload) {
      // 1. Arrange
      var event = event(payload);
      given(queueResolver.resolve(event.getEventType())).willReturn(QUEUE_NAME);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> adapter.publish(event));

      // 3. Assert
      throwableAssert
          .isInstanceOf(OutboxPublishException.class)
          .hasMessage("Ocorreu um erro ao publicar o evento de outbox.")
          .hasCauseInstanceOf(IllegalArgumentException.class);
      verify(queueResolver).resolve(event.getEventType());
      verifyNoInteractions(sqsTemplate);
      verifyNoMoreInteractions(queueResolver);
    }

    @Test
    @DisplayName("Deve traduzir falha do SQS para permitir retry")
    void shouldTranslateSqsFailureToAllowRetry() {
      // 1. Arrange
      var event = event(validPayload());
      var exception = new IllegalStateException("SQS unavailable");
      given(queueResolver.resolve(event.getEventType())).willReturn(QUEUE_NAME);
      doThrow(exception)
          .when(sqsTemplate)
          .send(eq(QUEUE_NAME), any(OutboxMessageEnvelope.class));

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> adapter.publish(event));

      // 3. Assert
      throwableAssert
          .isInstanceOf(OutboxPublishException.class)
          .hasMessage("Ocorreu um erro ao publicar o evento de outbox.")
          .hasCause(exception);
      verify(queueResolver).resolve(event.getEventType());
      verify(sqsTemplate).send(eq(QUEUE_NAME), any(OutboxMessageEnvelope.class));
      verifyNoMoreInteractions(queueResolver, sqsTemplate);
    }

    @Test
    @DisplayName("Deve traduzir falha ao resolver fila sem enviar mensagem")
    void shouldTranslateQueueResolutionFailureWithoutSendingMessage() {
      // 1. Arrange
      var event = event(validPayload());
      var exception = new IllegalStateException("No SQS queue configured");
      given(queueResolver.resolve(event.getEventType())).willThrow(exception);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> adapter.publish(event));

      // 3. Assert
      throwableAssert
          .isInstanceOf(OutboxPublishException.class)
          .hasMessage("Ocorreu um erro ao publicar o evento de outbox.")
          .hasCause(exception);
      verify(queueResolver).resolve(event.getEventType());
      verifyNoInteractions(sqsTemplate);
      verifyNoMoreInteractions(queueResolver);
    }
  }

  private static OutboxEvent event(String payload) {
    return OutboxEvent.createPending(
        UUID.fromString("019a1a60-8e31-73b0-bc44-238e6aea0002"),
        OutboxAggregateType.of("USER"),
        OutboxAggregateId.of("019a1a60-8e31-73b0-bc44-238e6aea0003"),
        OutboxEventType.of("EMAIL_VERIFICATION_REQUESTED"),
        payload,
        Instant.parse("2026-06-11T20:00:00Z"));
  }

  private static String validPayload() {
    return """
        {"userId":"019a1a60-8e31-73b0-bc44-238e6aea0003","email":"user@email.com","reason":"REGISTER"}
        """;
  }
}
