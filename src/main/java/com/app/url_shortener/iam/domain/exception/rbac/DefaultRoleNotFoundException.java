package com.app.url_shortener.iam.domain.exception.rbac;

import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.shared.exception.notfound.NotFoundException;

public class DefaultRoleNotFoundException extends NotFoundException {

  public DefaultRoleNotFoundException() {
    super(IamErrorCode.AUTH_DEFAULT_ROLE_NOT_FOUND);
  }
}
