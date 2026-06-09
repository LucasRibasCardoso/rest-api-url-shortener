package com.app.url_shortener.iam.domain.exception.user;

import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.shared.exception.validation.DomainValidationException;

public class InvalidVerificationCodeException extends DomainValidationException {

  public InvalidVerificationCodeException() {
    super(IamErrorCode.AUTH_INVALID_VERIFICATION_CODE);
  }
}
