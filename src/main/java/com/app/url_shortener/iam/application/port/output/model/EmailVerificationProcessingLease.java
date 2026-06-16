package com.app.url_shortener.iam.application.port.output.model;

import java.util.Objects;
import java.util.UUID;

public record EmailVerificationProcessingLease(
    EmailVerificationProcessingLeaseStatus status, UUID leaseId) {

  public EmailVerificationProcessingLease {
    Objects.requireNonNull(status, "status must not be null");

    if (status == EmailVerificationProcessingLeaseStatus.ACQUIRED && leaseId == null) {
      throw new IllegalArgumentException("leaseId is required for an acquired lease");
    }

    if (status != EmailVerificationProcessingLeaseStatus.ACQUIRED && leaseId != null) {
      throw new IllegalArgumentException("leaseId is only allowed for an acquired lease");
    }
  }

  public static EmailVerificationProcessingLease acquired(UUID leaseId) {
    return new EmailVerificationProcessingLease(
        EmailVerificationProcessingLeaseStatus.ACQUIRED,
        Objects.requireNonNull(leaseId, "leaseId must not be null"));
  }

  public static EmailVerificationProcessingLease processing() {
    return new EmailVerificationProcessingLease(EmailVerificationProcessingLeaseStatus.PROCESSING, null);
  }

  public static EmailVerificationProcessingLease completed() {
    return new EmailVerificationProcessingLease(EmailVerificationProcessingLeaseStatus.COMPLETED, null);
  }
}
