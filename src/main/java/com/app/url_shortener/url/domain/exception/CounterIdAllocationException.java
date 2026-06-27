package com.app.url_shortener.url.domain.exception;

import com.app.url_shortener.shared.exception.ErrorCode;
import com.app.url_shortener.shared.exception.internalservererror.InternalServerErrorException;

public class CounterIdAllocationException extends InternalServerErrorException {

  public CounterIdAllocationException(ErrorCode errorCode) {
    super(errorCode);
  }

  public CounterIdAllocationException(UrlErrorCode errorCode, Throwable cause) {
    super(errorCode, cause);
  }
}
