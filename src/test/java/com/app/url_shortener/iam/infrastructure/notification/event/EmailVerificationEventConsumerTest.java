package com.app.url_shortener.iam.infrastructure.notification.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.app.url_shortener.iam.application.event.EmailVerificationReason;
import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import com.app.url_shortener.iam.application.event.EmailVerificationRequestedPayload;
import com.app.url_shortener.iam.application.event.IamOutboxEventTypes;
import com.app.url_shortener.iam.application.port.output.EmailVerificationEventIdempotencyPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationTokenStorePort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.domain.model.UserAccount;
import com.app.url_shortener.iam.domain.valueobject.EmailVerificationToken;
import com.app.url_shortener.iam.infrastructure.notification.strategy.EmailSenderStrategy;
import com.app.url_shortener.shared.outbox.application.message.OutboxMessageEnvelope;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
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
import tools.jackson.databind.ObjectMapper;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Consumer de Verificação de Email")
class EmailVerificationEventConsumerTest {

  private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(4);
  private static final Duration VERIFICATION_CODE_TTL = Duration.ofMinutes(10);

  @Mock
  private EmailVerificationEventIdempotencyPort idempotencyPort;

  @Mock
  private UserAccountRepositoryPort userAccountRepositoryPort;

  @Mock
  private EmailVerificationTokenStorePort emailVerificationTokenStorePort;

  @Mock
  private EmailSenderStrategy emailSenderStrategy;

  private EmailVerificationEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer =
        new EmailVerificationEventConsumer(
            idempotencyPort,
            userAccountRepositoryPort,
            emailVerificationTokenStorePort,
            emailSenderStrategy,
            new ObjectMapper());
  }

  @Nested
  @DisplayName("Consumo")
  class ConsumeTests {

    @Test
    @DisplayName("Deve gerar, armazenar e enviar OTP para usuário pendente")
    void shouldGenerateStoreAndSendOtpForPendingUser() {
      // 1. Arrange
      var event = event();
      var user = user(UserStatus.PENDING_EMAIL_VERIFICATION, event.email());
      given(idempotencyPort.tryMarkAsProcessed(event.eventId(), IDEMPOTENCY_TTL)).willReturn(true);
      given(userAccountRepositoryPort.findById(event.userId())).willReturn(Optional.of(user));

      // 2. Act
      consumer.consume(envelope(event));

      // 3. Assert
      var tokenCaptor = ArgumentCaptor.forClass(EmailVerificationToken.class);
      verify(emailVerificationTokenStorePort).store(tokenCaptor.capture(), eq(VERIFICATION_CODE_TTL));
      var token = tokenCaptor.getValue();
      assertThat(token.userId()).isEqualTo(event.userId());
      assertThat(token.email()).isEqualTo(event.email());
      assertThat(token.code().value()).matches("\\d{6}");
      verify(emailSenderStrategy).sendEmailVerificationCode(event.email(), token.code().value());
      verify(idempotencyPort).tryMarkAsProcessed(event.eventId(), IDEMPOTENCY_TTL);
      verifyNoMoreInteractions(idempotencyPort);
    }

    @Test
    @DisplayName("Deve ignorar evento duplicado")
    void shouldIgnoreDuplicateEvent() {
      // 1. Arrange
      var event = event();
      given(idempotencyPort.tryMarkAsProcessed(event.eventId(), IDEMPOTENCY_TTL)).willReturn(false);

      // 2. Act
      consumer.consume(envelope(event));

      // 3. Assert
      verifyNoInteractions(userAccountRepositoryPort, emailVerificationTokenStorePort, emailSenderStrategy);
      verifyNoMoreInteractions(idempotencyPort);
    }

    @Test
    @DisplayName("Deve ignorar evento quando usuário não existir")
    void shouldIgnoreEventWhenUserDoesNotExist() {
      // 1. Arrange
      var event = event();
      given(idempotencyPort.tryMarkAsProcessed(event.eventId(), IDEMPOTENCY_TTL)).willReturn(true);
      given(userAccountRepositoryPort.findById(event.userId())).willReturn(Optional.empty());

      // 2. Act
      consumer.consume(envelope(event));

      // 3. Assert
      verifyNoInteractions(emailVerificationTokenStorePort, emailSenderStrategy);
      verifyNoMoreInteractions(idempotencyPort, userAccountRepositoryPort);
    }

    @Test
    @DisplayName("Deve ignorar evento quando email divergir")
    void shouldIgnoreEventWhenEmailDiffers() {
      // 1. Arrange
      var event = event();
      given(idempotencyPort.tryMarkAsProcessed(event.eventId(), IDEMPOTENCY_TTL)).willReturn(true);
      given(userAccountRepositoryPort.findById(event.userId()))
          .willReturn(Optional.of(user(UserStatus.PENDING_EMAIL_VERIFICATION, "other@email.com")));

      // 2. Act
      consumer.consume(envelope(event));

      // 3. Assert
      verifyNoInteractions(emailVerificationTokenStorePort, emailSenderStrategy);
      verifyNoMoreInteractions(idempotencyPort, userAccountRepositoryPort);
    }

    @Test
    @DisplayName("Deve ignorar evento quando usuário não estiver pendente")
    void shouldIgnoreEventWhenUserIsNotPending() {
      // 1. Arrange
      var event = event();
      given(idempotencyPort.tryMarkAsProcessed(event.eventId(), IDEMPOTENCY_TTL)).willReturn(true);
      given(userAccountRepositoryPort.findById(event.userId()))
          .willReturn(Optional.of(user(UserStatus.ACTIVE, event.email())));

      // 2. Act
      consumer.consume(envelope(event));

      // 3. Assert
      verifyNoInteractions(emailVerificationTokenStorePort, emailSenderStrategy);
      verifyNoMoreInteractions(idempotencyPort, userAccountRepositoryPort);
    }

    @Test
    @DisplayName("Deve remover marca e propagar falha técnica para permitir retry")
    void shouldRemoveMarkAndPropagateTechnicalFailureToAllowRetry() {
      // 1. Arrange
      var event = event();
      var failure = new IllegalStateException("Email unavailable");
      given(idempotencyPort.tryMarkAsProcessed(event.eventId(), IDEMPOTENCY_TTL)).willReturn(true);
      given(userAccountRepositoryPort.findById(event.userId()))
          .willReturn(Optional.of(user(UserStatus.PENDING_EMAIL_VERIFICATION, event.email())));
      doThrow(failure).when(emailSenderStrategy).sendEmailVerificationCode(eq(event.email()), any());

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> consumer.consume(envelope(event)));

      // 3. Assert
      throwableAssert.isSameAs(failure);
      verify(emailVerificationTokenStorePort)
          .store(any(EmailVerificationToken.class), eq(VERIFICATION_CODE_TTL));
      verify(idempotencyPort).removeProcessedMark(event.eventId());
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
      verifyNoInteractions(
          idempotencyPort,
          userAccountRepositoryPort,
          emailVerificationTokenStorePort,
          emailSenderStrategy);
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
      verifyNoInteractions(
          idempotencyPort,
          userAccountRepositoryPort,
          emailVerificationTokenStorePort,
          emailSenderStrategy);
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
        new ObjectMapper()
            .valueToTree(new EmailVerificationRequestedPayload(event.userId(), event.email(), event.reason()));

    return new OutboxMessageEnvelope(
        event.eventId(),
        eventType,
        schemaVersion,
        IamOutboxEventTypes.AGGREGATE_USER,
        event.userId().toString(),
        event.occurredAt(),
        payload);
  }

  private UserAccount user(UserStatus status, String email) {
    return UserAccount.restore(
        UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac101"),
        "User Name",
        email,
        "encoded-password",
        status,
        PlanType.FREE,
        status == UserStatus.ACTIVE,
        Set.of());
  }
}
