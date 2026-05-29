package com.app.url_shortener.shared.infrastructure.idempotency.port;

import com.app.url_shortener.shared.infrastructure.idempotency.valueobjects.CachedResponse;
import com.app.url_shortener.shared.infrastructure.idempotency.valueobjects.IdempotencyEntry;
import com.app.url_shortener.shared.infrastructure.idempotency.valueobjects.IdempotencyKey;
import java.time.Duration;
import java.util.Optional;

public interface IdempotencyPort {

  void delete(IdempotencyKey idempotencyKey);

  Optional<IdempotencyEntry> find(IdempotencyKey idempotencyKey);

  boolean saveInProgress(IdempotencyKey idempotencyKey, Duration ttl);

  void saveCompleted(IdempotencyKey idempotencyKey, CachedResponse response, Duration ttl);
}
