package com.app.url_shortener.iam.domain.exception.auth;

import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.shared.exception.conflict.ConflictException;
import java.util.Objects;
import java.util.UUID;

public class EmailVerificationProcessingLeaseLostException extends ConflictException {

  private final UUID eventId;

  public EmailVerificationProcessingLeaseLostException(UUID eventId) {
    super(IamErrorCode.EMAIL_VERIFICATION_PROCESSING_LEASE_LOST);
    this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
  }

  @Override
  public String getMessage() {
    return super.getMessage() + " eventId=" + eventId;
  }
}
