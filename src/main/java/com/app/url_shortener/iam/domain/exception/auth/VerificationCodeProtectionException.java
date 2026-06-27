package com.app.url_shortener.iam.domain.exception.auth;

import com.app.url_shortener.shared.exception.ErrorCode;
import com.app.url_shortener.shared.exception.internalservererror.InternalServerErrorException;

public class VerificationCodeProtectionException extends InternalServerErrorException {

  public VerificationCodeProtectionException(ErrorCode errorCode) {
    super(errorCode);
  }

  public VerificationCodeProtectionException(ErrorCode errorCode, Throwable cause) {
    super(errorCode, cause);
  }
}
