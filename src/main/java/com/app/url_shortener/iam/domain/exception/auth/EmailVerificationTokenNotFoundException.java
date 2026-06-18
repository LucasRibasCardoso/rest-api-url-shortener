package com.app.url_shortener.iam.domain.exception.auth;

import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.shared.exception.notfound.NotFoundException;

public class EmailVerificationTokenNotFoundException extends NotFoundException {

  public EmailVerificationTokenNotFoundException() {
    super(IamErrorCode.AUTH_EMAIL_VERIFICATION_TOKEN_NOT_FOUND);
  }
}
