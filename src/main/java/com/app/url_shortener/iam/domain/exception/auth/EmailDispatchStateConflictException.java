package com.app.url_shortener.iam.domain.exception.auth;

import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.shared.exception.conflict.ConflictException;

public class EmailDispatchStateConflictException extends ConflictException {

  public EmailDispatchStateConflictException() {
    super(IamErrorCode.AUTH_EMAIL_DISPATCH_STATE_CONFLICT);
  }
}
