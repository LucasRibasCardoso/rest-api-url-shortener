package com.app.url_shortener.iam.domain.exception.user;

import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.shared.exception.forbidden.ForbiddenException;

public class UserAccountLockedException extends ForbiddenException {

  public UserAccountLockedException() {
    super(IamErrorCode.AUTH_ACCOUNT_LOCKED);
  }
}
