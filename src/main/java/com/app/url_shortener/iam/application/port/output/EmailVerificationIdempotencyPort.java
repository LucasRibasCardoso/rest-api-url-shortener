package com.app.url_shortener.iam.application.port.output;

import com.app.url_shortener.iam.application.port.output.model.EmailVerificationProcessingLease;
import java.time.Duration;
import java.util.UUID;

public interface EmailVerificationIdempotencyPort {

  EmailVerificationProcessingLease acquireProcessingLease(
      UUID eventId, Duration processingLeaseTtl);

  boolean markAsCompleted(UUID eventId, UUID leaseId, Duration idempotencyTtl);

  boolean releaseProcessingLease(UUID eventId, UUID leaseId);
}
