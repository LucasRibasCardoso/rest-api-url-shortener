package com.app.url_shortener.iam.domain.exception.auth;

import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.shared.exception.internalservererror.InternalServerErrorException;

public class EmailVerificationSendException extends InternalServerErrorException {

  public EmailVerificationSendException() {
    super(IamErrorCode.AUTH_EMAIL_VERIFICATION_SEND_FAILED);
  }

  public EmailVerificationSendException(Throwable cause) {
    super(IamErrorCode.AUTH_EMAIL_VERIFICATION_SEND_FAILED, cause);
  }
}
