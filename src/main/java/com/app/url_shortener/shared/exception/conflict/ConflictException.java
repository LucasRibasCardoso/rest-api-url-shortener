package com.app.url_shortener.shared.exception.conflict;

import com.app.url_shortener.shared.exception.AppBusinessException;
import com.app.url_shortener.shared.exception.ErrorCode;

public abstract class ConflictException extends AppBusinessException {

  public ConflictException(ErrorCode errorCode) {
    super(errorCode);
  }
}
