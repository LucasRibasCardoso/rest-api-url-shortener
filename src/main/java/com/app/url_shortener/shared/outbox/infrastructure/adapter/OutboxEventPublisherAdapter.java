package com.app.url_shortener.shared.outbox.infrastructure.adapter;

import com.app.url_shortener.shared.outbox.application.message.OutboxMessageEnvelope;
import com.app.url_shortener.shared.outbox.application.port.OutboxEventPublisherPort;
import com.app.url_shortener.shared.outbox.domain.exception.OutboxPublishException;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEvent;
import com.app.url_shortener.shared.outbox.infrastructure.resolver.OutboxEventQueueResolver;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class OutboxEventPublisherAdapter implements OutboxEventPublisherPort {

  private final SqsTemplate sqsTemplate;
  private final OutboxEventQueueResolver queueResolver;
  private final ObjectMapper objectMapper;

  @Override
  public void publish(OutboxEvent event) {
    try {
      String queueName = queueResolver.resolve(event.getEventType());
      var outboxMessageEnvelope = toMessageEnvelope(event);
      sqsTemplate.send(queueName, outboxMessageEnvelope);
    } catch (OutboxPublishException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new OutboxPublishException(exception);
    }
  }

  private OutboxMessageEnvelope toMessageEnvelope(OutboxEvent event) {
    try {
      JsonNode payload = objectMapper.readTree(event.getPayload());

      if (!payload.isObject()) {
        throw new OutboxPublishException(
            new IllegalArgumentException("Persisted outbox event payload must be a JSON object"));
      }

      return new OutboxMessageEnvelope(
          event.getId(),
          event.getEventType().value(),
          event.getSchemaVersion(),
          event.getAggregateType().value(),
          event.getAggregateId().value(),
          event.getCreatedAt(),
          payload);
    } catch (JacksonException exception) {
      throw new OutboxPublishException(exception);
    }
  }
}
