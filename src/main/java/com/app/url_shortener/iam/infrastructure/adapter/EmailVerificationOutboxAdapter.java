package com.app.url_shortener.iam.infrastructure.adapter;

import com.app.url_shortener.iam.application.event.EmailVerificationReason;
import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import com.app.url_shortener.iam.application.event.IamOutboxEventTypes;
import com.app.url_shortener.iam.application.port.output.EmailVerificationOutboxPort;
import com.app.url_shortener.shared.outbox.application.port.OutboxEventRepositoryPort;
import com.app.url_shortener.shared.outbox.application.port.OutboxEventSerializerPort;
import com.app.url_shortener.shared.outbox.domain.model.OutboxAggregateId;
import com.app.url_shortener.shared.outbox.domain.model.OutboxAggregateType;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEvent;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEventType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailVerificationOutboxAdapter implements EmailVerificationOutboxPort {

  private final OutboxEventRepositoryPort outboxEventRepositoryPort;
  private final OutboxEventSerializerPort outboxEventSerializerPort;

  @Override
  public void publishEmailVerificationRequestedEvent(UUID userId, String email, EmailVerificationReason reason) {
    var eventPayload = EmailVerificationRequestedEvent.create(userId, email, reason);
    String payloadJson = outboxEventSerializerPort.serialize(eventPayload);

    var outboxEvent = OutboxEvent.createPending(
            eventPayload.eventId(),
            OutboxAggregateType.of(IamOutboxEventTypes.AGGREGATE_USER),
            OutboxAggregateId.of(userId.toString()),
            OutboxEventType.of(IamOutboxEventTypes.EMAIL_VERIFICATION_REQUESTED),
            payloadJson,
            eventPayload.occurredAt());

    outboxEventRepositoryPort.save(outboxEvent);
  }
}
