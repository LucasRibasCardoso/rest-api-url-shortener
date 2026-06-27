package com.app.url_shortener.iam.domain.exception.auth;

import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.shared.exception.validation.DomainValidationException;

public class InvalidCredentialsException extends DomainValidationException {

  public InvalidCredentialsException() {
    super(IamErrorCode.AUTH_INVALID_CREDENTIALS);
  }
}
