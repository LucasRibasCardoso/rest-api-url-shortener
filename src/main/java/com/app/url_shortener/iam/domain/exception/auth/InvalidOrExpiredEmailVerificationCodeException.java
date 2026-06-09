package com.app.url_shortener.iam.domain.exception.auth;

import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.shared.exception.validation.DomainValidationException;

public class InvalidOrExpiredEmailVerificationCodeException extends DomainValidationException {

  public InvalidOrExpiredEmailVerificationCodeException() {
    super(IamErrorCode.AUTH_INVALID_OR_EXPIRED_VERIFICATION_CODE);
  }
}
