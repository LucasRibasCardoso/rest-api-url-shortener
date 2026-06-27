package com.app.url_shortener.shared.outbox.application.port;

import com.app.url_shortener.shared.outbox.domain.model.OutboxEvent;
import java.time.Instant;
import java.util.List;

public interface OutboxEventRepositoryPort {

  OutboxEvent save(OutboxEvent event);

  List<OutboxEvent> findPendingToPublish(Instant now, int limit);

  void saveAll(List<OutboxEvent> events);
}
