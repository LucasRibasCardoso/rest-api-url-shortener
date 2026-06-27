package com.app.url_shortener.iam.infrastructure.notification.event;

import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import com.app.url_shortener.iam.application.event.EmailVerificationRequestedPayload;
import com.app.url_shortener.iam.application.event.IamOutboxEventTypes;
import com.app.url_shortener.iam.application.service.EmailVerificationEventProcessorService;
import com.app.url_shortener.iam.domain.exception.auth.InvalidEmailVerificationEventException;
import com.app.url_shortener.shared.outbox.application.message.OutboxMessageEnvelope;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationEventConsumer {

  private static final int SUPPORTED_SCHEMA_VERSION = 1;

  private final ObjectMapper objectMapper;
  private final EmailVerificationEventProcessorService emailVerificationEventProcessorService;

  @SqsListener("${app.aws.sqs.email-verification-events-queue}")
  public void consume(OutboxMessageEnvelope envelope) {
    validateEnvelope(envelope);
    EmailVerificationRequestedEvent event = parseToEvent(envelope);
    emailVerificationEventProcessorService.process(event);
  }

  private void validateEnvelope(OutboxMessageEnvelope envelope) {
    if (envelope == null) {
      throw new InvalidEmailVerificationEventException("envelope must not be null");
    }

    if (!IamOutboxEventTypes.EMAIL_VERIFICATION_REQUESTED.equals(envelope.eventType())) {
      throw new InvalidEmailVerificationEventException(
          "Unsupported outbox event type: " + envelope.eventType());
    }

    if (envelope.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
      throw new InvalidEmailVerificationEventException(
          "Unsupported outbox schema version: " + envelope.schemaVersion());
    }

    if (envelope.eventId() == null) {
      throw new InvalidEmailVerificationEventException("eventId must not be null");
    }

    if (envelope.occurredAt() == null) {
      throw new InvalidEmailVerificationEventException("occurredAt must not be null");
    }

    if (envelope.payload() == null || envelope.payload().isNull()) {
      throw new InvalidEmailVerificationEventException("payload must not be null");
    }
  }

  private EmailVerificationRequestedEvent parseToEvent(OutboxMessageEnvelope envelope) {
    try {
      var payload =
          objectMapper.treeToValue(envelope.payload(), EmailVerificationRequestedPayload.class);
      return new EmailVerificationRequestedEvent(
          envelope.eventId(),
          payload.userId(),
          payload.email(),
          payload.reason(),
          envelope.occurredAt());
    } catch (Exception exception) {
      throw new InvalidEmailVerificationEventException("Invalid email verification outbox payload");
    }
  }
}
