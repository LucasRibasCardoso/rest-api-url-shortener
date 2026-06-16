package com.app.url_shortener.iam.domain.exception.auth;

import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.shared.exception.conflict.ConflictException;
import java.util.Objects;
import java.util.UUID;

public class EmailVerificationEventAlreadyProcessingException extends ConflictException {

  private final UUID eventId;

  public EmailVerificationEventAlreadyProcessingException(UUID eventId) {
    super(IamErrorCode.EMAIL_VERIFICATION_EVENT_ALREADY_PROCESSING);
    this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
  }

  @Override
  public String getMessage() {
    return super.getMessage() + " eventId=" + eventId;
  }
}
