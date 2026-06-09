package com.app.url_shortener.iam.application.port.output;

import java.time.Duration;
import java.util.UUID;

public interface EmailVerificationEventIdempotencyPort {

  boolean tryMarkAsProcessed(UUID eventId, Duration ttl);

  void removeProcessedMark(UUID eventId);
}
