package com.app.url_shortener.iam.domain.exception.auth;

import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.shared.exception.validation.DomainValidationException;
import java.util.Objects;

public class InvalidEmailVerificationEventException extends DomainValidationException {

  private final String detail;

  public InvalidEmailVerificationEventException(String detail) {
    super(IamErrorCode.AUTH_INVALID_EMAIL_VERIFICATION_EVENT);
    this.detail = Objects.requireNonNull(detail, "detail must not be null");
  }

  @Override
  public String getMessage() {
    return super.getMessage() + " " + detail;
  }
}
