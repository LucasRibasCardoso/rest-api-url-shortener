package com.app.url_shortener.iam.domain.exception.user;

import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.shared.exception.forbidden.ForbiddenException;

public class UserAccountDisabledException extends ForbiddenException {

  public UserAccountDisabledException() {
    super(IamErrorCode.USER_ACCOUNT_DISABLED);
  }
}
