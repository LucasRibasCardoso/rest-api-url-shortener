package com.app.url_shortener.iam.domain.exception.auth;

import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.shared.exception.forbidden.ForbiddenException;

public class AccountPendingVerificationException extends ForbiddenException {

  public AccountPendingVerificationException() {
    super(IamErrorCode.AUTH_ACCOUNT_PENDING_VERIFICATION);
  }
}
