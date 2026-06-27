package com.app.url_shortener.shared.outbox.infrastructure.resolver;

import com.app.url_shortener.shared.outbox.config.OutboxSqsProperties;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEventType;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxEventQueueResolver {

  private final OutboxSqsProperties properties;

  public String resolve(OutboxEventType eventType) {
    Objects.requireNonNull(eventType, "eventType must not be null");
    Map<String, String> queues = properties.queues();
    String queueName = queues == null ? null : queues.get(eventType.value());

    if (queueName == null || queueName.isBlank()) {
      throw new IllegalStateException(
          "No SQS queue configured for outbox event type: " + eventType.value());
    }

    return queueName.trim();
  }
}
