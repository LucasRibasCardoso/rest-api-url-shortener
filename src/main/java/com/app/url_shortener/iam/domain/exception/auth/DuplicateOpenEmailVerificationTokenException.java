package com.app.url_shortener.iam.domain.exception.auth;

import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.shared.exception.conflict.ConflictException;

public class DuplicateOpenEmailVerificationTokenException extends ConflictException {

  public DuplicateOpenEmailVerificationTokenException() {
    super(IamErrorCode.AUTH_DUPLICATE_OPEN_EMAIL_VERIFICATION_TOKEN);
  }
}
