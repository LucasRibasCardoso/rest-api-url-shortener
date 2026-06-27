package com.app.url_shortener.shared.exception.notfound;

import com.app.url_shortener.shared.exception.AppBusinessException;
import com.app.url_shortener.shared.exception.ErrorCode;

public abstract class NotFoundException extends AppBusinessException {

  public NotFoundException(ErrorCode errorCode) {
    super(errorCode);
  }
}
