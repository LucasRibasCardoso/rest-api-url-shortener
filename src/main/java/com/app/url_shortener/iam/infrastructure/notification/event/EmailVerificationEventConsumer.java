package com.app.url_shortener.iam.infrastructure.notification.event;

import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import com.app.url_shortener.iam.application.event.EmailVerificationRequestedPayload;
import com.app.url_shortener.iam.application.event.IamOutboxEventTypes;
import com.app.url_shortener.iam.application.policy.EmailVerificationPolicy;
import com.app.url_shortener.iam.application.port.output.EmailVerificationIdempotencyPort;
import com.app.url_shortener.iam.application.port.output.model.EmailVerificationProcessingLeaseStatus;
import com.app.url_shortener.iam.application.service.EmailVerificationEventProcessorService;
import com.app.url_shortener.iam.domain.exception.auth.EmailVerificationEventAlreadyProcessingException;
import com.app.url_shortener.iam.domain.exception.auth.EmailVerificationProcessingLeaseLostException;
import com.app.url_shortener.shared.outbox.application.message.OutboxMessageEnvelope;
import io.awspring.cloud.sqs.annotation.SqsListener;
import java.util.Objects;
import java.util.UUID;
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
  private final EmailVerificationPolicy policy;
  private final EmailVerificationIdempotencyPort idempotencyPort;
  private final EmailVerificationEventProcessorService processorService;

  @SqsListener("${app.aws.sqs.email-verification-events-queue}")
  public void consume(OutboxMessageEnvelope envelope) {
    var event = toEvent(envelope);

    var lease = idempotencyPort.acquireProcessingLease(event.eventId(), policy.processingLeaseTtl());

    if (lease.status() == EmailVerificationProcessingLeaseStatus.COMPLETED) {
      log.debug("Evento de verificação de e-mail já processado. eventId={}", event.eventId());
      return;
    }

    if (lease.status() == EmailVerificationProcessingLeaseStatus.PROCESSING) {
      throw new EmailVerificationEventAlreadyProcessingException(event.eventId());
    }

    try {
      processorService.process(event);
      boolean completed =
          idempotencyPort.markAsCompleted(
              event.eventId(), lease.leaseId(), policy.idempotencyTtl());
      if (!completed) {
        throw new EmailVerificationProcessingLeaseLostException(event.eventId());
      }
    } catch (RuntimeException exception) {
      releaseProcessingLease(event, lease.leaseId(), exception);
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

  private void releaseProcessingLease(EmailVerificationRequestedEvent event, UUID leaseId, RuntimeException originalException) {
    try {
      idempotencyPort.releaseProcessingLease(event.eventId(), leaseId);
    } catch (RuntimeException releaseException) {
      originalException.addSuppressed(releaseException);
      log.error(
          "Falha ao liberar lease de processamento após erro no processamento. eventId={}",
          event.eventId(),
          releaseException);
    }
  }
}
