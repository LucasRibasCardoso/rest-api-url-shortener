package com.app.url_shortener.shared.exception.internalservererror;

import com.app.url_shortener.shared.exception.AppBusinessException;
import com.app.url_shortener.shared.exception.ErrorCode;

public abstract class InternalServerErrorException extends AppBusinessException {

  public InternalServerErrorException(ErrorCode errorCode) {
    super(errorCode);
  }

  public InternalServerErrorException(ErrorCode errorCode, Throwable cause) {
    super(errorCode, cause);
  }
}
