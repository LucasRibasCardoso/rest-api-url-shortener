package com.app.url_shortener.iam.domain.exception.auth;

import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.shared.exception.conflict.ConflictException;

public class EmailDispatchAlreadyProcessingException extends ConflictException {

  public EmailDispatchAlreadyProcessingException() {
    super(IamErrorCode.AUTH_EMAIL_DISPATCH_ALREADY_PROCESSING);
  }
}
