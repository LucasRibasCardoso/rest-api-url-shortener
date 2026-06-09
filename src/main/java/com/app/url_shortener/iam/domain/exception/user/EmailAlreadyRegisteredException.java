package com.app.url_shortener.iam.domain.exception.user;

import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.shared.exception.conflict.ConflictException;

public class EmailAlreadyRegisteredException extends ConflictException {

  public EmailAlreadyRegisteredException() {
    super(IamErrorCode.AUTH_EMAIL_ALREADY_EXISTS);
  }
}
