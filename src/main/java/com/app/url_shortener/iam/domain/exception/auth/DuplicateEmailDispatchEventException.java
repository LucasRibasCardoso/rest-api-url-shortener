package com.app.url_shortener.iam.domain.exception.auth;

import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.shared.exception.conflict.ConflictException;

public class DuplicateEmailDispatchEventException extends ConflictException {

  public DuplicateEmailDispatchEventException() {
    super(IamErrorCode.AUTH_DUPLICATE_EMAIL_DISPATCH_EVENT);
  }
}
