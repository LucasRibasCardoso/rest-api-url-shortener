package com.app.url_shortener.iam.domain.exception.user;

import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.shared.exception.notfound.NotFoundException;

public class UserNotFoundException extends NotFoundException {

  public UserNotFoundException() {
    super(IamErrorCode.AUTH_USER_NOT_FOUND);
  }
}
