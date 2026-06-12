package com.app.url_shortener.shared.outbox.application.scheduler;

import com.app.url_shortener.shared.outbox.application.service.OutboxPublisherService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.outbox.publisher",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OutboxPublisherScheduler {

  private final OutboxPublisherService outboxPublisherService;

  @Scheduled(
      fixedDelayString = "${app.outbox.publisher.fixed-delay}",
      initialDelayString = "${app.outbox.publisher.initial-delay:5s}")
  public void publishPendingEvents() {
    outboxPublisherService.publishPendingEvents();
  }
}
