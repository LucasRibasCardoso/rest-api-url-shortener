package com.app.url_shortener.shared.exception.unauthorized;

import com.app.url_shortener.shared.exception.AppBusinessException;
import com.app.url_shortener.shared.exception.ErrorCode;

public abstract class UnauthorizedException extends AppBusinessException {

  public UnauthorizedException(ErrorCode errorCode) {
    super(errorCode);
  }
}
