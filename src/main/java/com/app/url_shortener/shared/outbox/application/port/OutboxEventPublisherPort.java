package com.app.url_shortener.shared.outbox.application.port;

import com.app.url_shortener.shared.outbox.domain.model.OutboxEvent;

public interface OutboxEventPublisherPort {

  void publish(OutboxEvent event);
}
