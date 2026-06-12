package com.app.url_shortener.shared.outbox.application.service;

import com.app.url_shortener.shared.outbox.application.port.OutboxEventPublisherPort;
import com.app.url_shortener.shared.outbox.application.port.OutboxEventRepositoryPort;
import com.app.url_shortener.shared.outbox.application.policy.OutboxPublisherPolicy;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEvent;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxPublisherService {

  private final OutboxEventRepositoryPort outboxEventRepositoryPort;
  private final OutboxEventPublisherPort outboxEventPublisherPort;
  private final OutboxPublisherPolicy outboxPublisherPolicy;

  @Transactional
  public void publishPendingEvents() {
    Instant now = Instant.now();
    List<OutboxEvent> events = outboxEventRepositoryPort.findPendingToPublish(now, outboxPublisherPolicy.batchSize());

    for (OutboxEvent event : events) {
      publishEvent(event, now);
    }

    outboxEventRepositoryPort.saveAll(events);
  }

  private void publishEvent(OutboxEvent event, Instant now) {
    try {
      outboxEventPublisherPort.publish(event);
      event.markAsPublished(now);
    }
    catch (Exception e) {
      event.registerFailure(
              e.getMessage(),
              now,
              outboxPublisherPolicy.maxAttempts(),
              outboxPublisherPolicy.retryDelay()
      );
    }
  }
}
