package com.app.url_shortener.shared.exception.forbidden;

import com.app.url_shortener.shared.exception.AppBusinessException;
import com.app.url_shortener.shared.exception.ErrorCode;

public abstract class ForbiddenException extends AppBusinessException {

  public ForbiddenException(ErrorCode errorCode) {
    super(errorCode);
  }
}
