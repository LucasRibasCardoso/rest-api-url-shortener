package com.app.url_shortener.iam.infrastructure.notification.event;

import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import com.app.url_shortener.iam.application.event.EmailVerificationRequestedPayload;
import com.app.url_shortener.iam.application.event.IamOutboxEventTypes;
import com.app.url_shortener.iam.application.policy.EmailVerificationPolicy;
import com.app.url_shortener.iam.application.port.output.EmailVerificationIdempotencyPort;
import com.app.url_shortener.iam.application.service.EmailVerificationEventProcessorService;
import com.app.url_shortener.shared.outbox.application.message.OutboxMessageEnvelope;
import io.awspring.cloud.sqs.annotation.SqsListener;
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

  private static final int SUPPORTED_SCHEMA_VERSION = 1;

  private final ObjectMapper objectMapper;
  private final EmailVerificationPolicy emailVerificationPolicy;
  private final EmailVerificationIdempotencyPort idempotencyPort;
  private final EmailVerificationEventProcessorService processorService;

  @SqsListener("${app.aws.sqs.email-verification-events-queue}")
  public void consume(OutboxMessageEnvelope envelope) {
    var event = toEvent(envelope);

    boolean idempotencyMarkedAsProcessed = idempotencyPort.tryMarkAsProcessed(
            event.eventId(),
            emailVerificationPolicy.idempotencyTtl());
    if (!idempotencyMarkedAsProcessed) {
      log.debug("Evento de verificação de e-mail duplicado ignorado. eventId={}", event.eventId());
      return;
    }

    try {
      processorService.process(event);
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
