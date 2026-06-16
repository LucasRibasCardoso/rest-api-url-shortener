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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationEventConsumer {

  private static final int SUPPORTED_SCHEMA_VERSION = 1;
  private static final String SUPPORTED_EVENT_TYPE =
      IamOutboxEventTypes.EMAIL_VERIFICATION_REQUESTED;

  private final ObjectMapper objectMapper;
  private final EmailVerificationEventProcessorService processorService;

  @SqsListener("${app.aws.sqs.email-verification-events-queue}")
  public void consume(OutboxMessageEnvelope envelope) {
    if (envelope == null) {
      throw new InvalidEmailVerificationEventException("envelope must not be null");
    }

    if (!SUPPORTED_EVENT_TYPE.equals(envelope.eventType())) {
      throw new InvalidEmailVerificationEventException("Unsupported outbox event type: " + envelope.eventType());
    }

    if (envelope.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
      throw new InvalidEmailVerificationEventException("Unsupported outbox schema version: " + envelope.schemaVersion());
    }

    EmailVerificationRequestedEvent event = toEvent(envelope);
    processorService.process(event);
  }

  private EmailVerificationRequestedEvent toEvent(OutboxMessageEnvelope envelope) {
    try {
      var payload =
          objectMapper.treeToValue(envelope.payload(), EmailVerificationRequestedPayload.class);
      return new EmailVerificationRequestedEvent(
          envelope.eventId(),
          payload.userId(),
          payload.email(),
          payload.reason(),
          envelope.occurredAt());
    } catch (JacksonException exception) {
      throw new InvalidEmailVerificationEventException("Invalid email verification outbox payload");
    }
  }
}
