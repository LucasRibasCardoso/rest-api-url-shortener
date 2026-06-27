package com.app.url_shortener.iam.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.app.url_shortener.iam.application.event.EmailVerificationRequestedPayload;
import com.app.url_shortener.iam.application.event.IamOutboxEventTypes;
import com.app.url_shortener.iam.domain.enums.EmailDispatchReason;
import com.app.url_shortener.shared.outbox.application.port.OutboxEventRepositoryPort;
import com.app.url_shortener.shared.outbox.application.port.OutboxEventSerializerPort;
import com.app.url_shortener.shared.outbox.domain.exception.OutboxEventSerializationException;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEvent;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEventStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Adapter Outbox de Verificação de Email")
class EmailVerificationOutboxAdapterTest {

  private static final UUID USER_ID = UUID.fromString("019a1a4f-d0db-7f94-bd8d-63d831f40001");
  private static final String EMAIL = "user@email.com";
  private static final String PAYLOAD_JSON =
      """
      {"userId":"019a1a4f-d0db-7f94-bd8d-63d831f40001","email":"user@email.com","reason":"REGISTER"}
      """;

  @Mock private OutboxEventRepositoryPort outboxEventRepositoryPort;

  @Mock private OutboxEventSerializerPort outboxEventSerializerPort;

  @Captor private ArgumentCaptor<Object> payloadCaptor;

  @Captor private ArgumentCaptor<OutboxEvent> outboxEventCaptor;

  @InjectMocks private EmailVerificationOutboxAdapter adapter;

  @Nested
  @DisplayName("Publicação de solicitação de verificação")
  class PublishEmailVerificationRequestedEventTests {

    @Test
    @DisplayName("Deve serializar payload e salvar exatamente um evento pendente no Outbox")
    void shouldSerializePayloadAndSaveExactlyOnePendingOutboxEvent() {
      // 1. Arrange
      var beforeExecution = Instant.now();
      given(outboxEventSerializerPort.serialize(any(EmailVerificationRequestedPayload.class)))
          .willReturn(PAYLOAD_JSON);

      // 2. Act
      adapter.publishEmailVerificationRequestedEvent(
          USER_ID, " USER@EMAIL.COM ", EmailDispatchReason.REGISTER);

      // 3. Assert
      verify(outboxEventSerializerPort).serialize(payloadCaptor.capture());
      verify(outboxEventRepositoryPort, times(1)).save(outboxEventCaptor.capture());

      var payload = (EmailVerificationRequestedPayload) payloadCaptor.getValue();
      var outboxEvent = outboxEventCaptor.getValue();

      assertAll(
          () -> assertThat(payload.userId()).isEqualTo(USER_ID),
          () -> assertThat(payload.email()).isEqualTo(EMAIL),
          () -> assertThat(payload.reason()).isEqualTo(EmailDispatchReason.REGISTER),
          () ->
              assertThat(payload.getClass().getRecordComponents())
                  .extracting(component -> component.getName())
                  .containsExactly("userId", "email", "reason"),
          () -> assertThat(outboxEvent.getId()).isNotNull(),
          () ->
              assertThat(outboxEvent.getAggregateType().value())
                  .isEqualTo(IamOutboxEventTypes.AGGREGATE_USER),
          () ->
              assertThat(outboxEvent.getAggregateId().value())
                  .isEqualTo(payload.userId().toString()),
          () ->
              assertThat(outboxEvent.getEventType().value())
                  .isEqualTo(IamOutboxEventTypes.EMAIL_VERIFICATION_REQUESTED),
          () -> assertThat(outboxEvent.getPayload()).isEqualTo(PAYLOAD_JSON.strip()),
          () -> assertThat(outboxEvent.getPayload()).doesNotContainIgnoringCase("otp"),
          () -> assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING),
          () -> assertThat(outboxEvent.getAttempts()).isZero(),
          () -> assertThat(outboxEvent.getLastError()).isNull(),
          () -> assertThat(outboxEvent.getCreatedAt()).isAfterOrEqualTo(beforeExecution),
          () -> assertThat(outboxEvent.getPublishedAt()).isNull(),
          () -> assertThat(outboxEvent.getNextAttemptAt()).isNull());

      verifyNoMoreInteractions(outboxEventSerializerPort, outboxEventRepositoryPort);
    }

    @Test
    @DisplayName("Não deve salvar evento quando a serialização do payload falhar")
    void shouldNotSaveOutboxEventWhenPayloadSerializationFails() {
      // 1. Arrange
      var exception =
          new OutboxEventSerializationException(new IllegalStateException("Jackson failure"));
      given(outboxEventSerializerPort.serialize(any(EmailVerificationRequestedPayload.class)))
          .willThrow(exception);

      // 2. Act
      var throwableAssert =
          assertThatThrownBy(
              () ->
                  adapter.publishEmailVerificationRequestedEvent(
                      USER_ID, EMAIL, EmailDispatchReason.REGISTER));

      // 3. Assert
      throwableAssert.isSameAs(exception);

      verify(outboxEventSerializerPort).serialize(any(EmailVerificationRequestedPayload.class));
      verifyNoInteractions(outboxEventRepositoryPort);
      verifyNoMoreInteractions(outboxEventSerializerPort);
    }

    @Test
    @DisplayName("Deve propagar falha ao salvar evento no Outbox")
    void shouldPropagateExceptionWhenSavingOutboxEventFails() {
      // 1. Arrange
      var exception = new IllegalStateException("Falha ao salvar evento no Outbox.");
      given(outboxEventSerializerPort.serialize(any(EmailVerificationRequestedPayload.class)))
          .willReturn(PAYLOAD_JSON);
      given(outboxEventRepositoryPort.save(any(OutboxEvent.class))).willThrow(exception);

      // 2. Act
      var throwableAssert =
          assertThatThrownBy(
              () ->
                  adapter.publishEmailVerificationRequestedEvent(
                      USER_ID, EMAIL, EmailDispatchReason.REGISTER));

      // 3. Assert
      throwableAssert.isSameAs(exception);

      verify(outboxEventSerializerPort).serialize(any(EmailVerificationRequestedPayload.class));
      verify(outboxEventRepositoryPort).save(any(OutboxEvent.class));
      verifyNoMoreInteractions(outboxEventSerializerPort, outboxEventRepositoryPort);
    }
  }
}
