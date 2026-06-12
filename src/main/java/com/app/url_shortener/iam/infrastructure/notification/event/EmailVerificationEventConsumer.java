package com.app.url_shortener.iam.infrastructure.notification.event;

import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import com.app.url_shortener.iam.application.event.EmailVerificationRequestedPayload;
import com.app.url_shortener.iam.application.event.IamOutboxEventTypes;
import com.app.url_shortener.iam.application.port.output.EmailVerificationIdempotencyPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationTokenStorePort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.domain.valueobject.EmailVerificationToken;
import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import com.app.url_shortener.iam.infrastructure.notification.strategy.EmailSenderStrategyPort;
import com.app.url_shortener.shared.outbox.application.message.OutboxMessageEnvelope;
import io.awspring.cloud.sqs.annotation.SqsListener;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationEventConsumer {

  private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(4);
  private static final Duration VERIFICATION_CODE_TTL = Duration.ofMinutes(10);
  private static final int SUPPORTED_SCHEMA_VERSION = 1;

  private final EmailVerificationIdempotencyPort idempotencyPort;
  private final UserAccountRepositoryPort userAccountRepositoryPort;
  private final EmailVerificationTokenStorePort emailVerificationTokenStorePort;
  private final EmailSenderStrategyPort emailSenderStrategy;
  private final ObjectMapper objectMapper;

  @SqsListener("${app.aws.sqs.email-verification-events-queue}")
  public void consume(OutboxMessageEnvelope envelope) {
    var event = toEvent(envelope);

    if (!idempotencyPort.tryMarkAsProcessed(event.eventId(), IDEMPOTENCY_TTL)) {
      log.debug("Evento de verificação de email duplicado ignorado. eventId={}", event.eventId());
      return;
    }

    try {
      process(event);
    } catch (RuntimeException exception) {
      removeProcessedMark(event, exception);
      throw exception;
    }
  }

  private EmailVerificationRequestedEvent toEvent(OutboxMessageEnvelope envelope) {
    Objects.requireNonNull(envelope, "envelope must not be null");

    if (!IamOutboxEventTypes.EMAIL_VERIFICATION_REQUESTED.equals(envelope.eventType())) {
      throw new IllegalArgumentException("Unsupported outbox event type: " + envelope.eventType());
    }

    if (envelope.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
      throw new IllegalArgumentException("Unsupported outbox schema version: " + envelope.schemaVersion());
    }

    try {
      var payload = objectMapper.treeToValue(envelope.payload(), EmailVerificationRequestedPayload.class);
      return new EmailVerificationRequestedEvent(
          envelope.eventId(),
          payload.userId(),
          payload.email(),
          payload.reason(),
          envelope.occurredAt());
    } catch (JacksonException exception) {
      throw new IllegalArgumentException("Invalid email verification outbox payload", exception);
    }
  }

  private void process(EmailVerificationRequestedEvent event) {
    var userAccountOptional = userAccountRepositoryPort.findById(event.userId());

    if (userAccountOptional.isEmpty()) {
      log.debug("Evento de verificação ignorado para usuário inexistente. eventId={}", event.eventId());
      return;
    }

    var userAccount = userAccountOptional.get();
    if (!userAccount.getEmail().equals(event.email())) {
      log.warn(
          "Evento de verificação ignorado por divergência de email. eventId={}, userId={}",
          event.eventId(),
          event.userId());
      return;
    }

    if (userAccount.getStatus() != UserStatus.PENDING_EMAIL_VERIFICATION) {
      log.debug(
          "Evento de verificação ignorado para usuário não pendente. eventId={}, userId={}, status={}",
          event.eventId(),
          event.userId(),
          userAccount.getStatus());
      return;
    }

    VerificationCode verificationCode = VerificationCode.generate();
    EmailVerificationToken token =
        EmailVerificationToken.create(
            userAccount.getId(),
            userAccount.getEmail(),
            verificationCode,
            Instant.now().plus(VERIFICATION_CODE_TTL));

    emailVerificationTokenStorePort.store(token, VERIFICATION_CODE_TTL);
    emailSenderStrategy.sendEmailVerificationCode(userAccount.getEmail(), verificationCode.value());
  }

  private void removeProcessedMark(EmailVerificationRequestedEvent event, RuntimeException processingException) {
    try {
      idempotencyPort.removeProcessedMark(event.eventId());
    } catch (RuntimeException cleanupException) {
      processingException.addSuppressed(cleanupException);
      log.error(
          "Falha ao remover marca de idempotência após erro no processamento. eventId={}",
          event.eventId(),
          cleanupException);
    }
  }
}
