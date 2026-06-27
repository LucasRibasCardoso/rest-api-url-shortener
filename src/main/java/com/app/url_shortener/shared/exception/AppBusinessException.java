package com.app.url_shortener.shared.exception;

import java.util.Objects;

public abstract class AppBusinessException extends RuntimeException {

  private final ErrorCode errorCode;

  public AppBusinessException(ErrorCode errorCode) {
    super(Objects.requireNonNull(errorCode, "errorCode is required").getMessage());
    this.errorCode = errorCode;
  }

  public AppBusinessException(ErrorCode errorCode, Throwable cause) {
    super(errorCode.getMessage(), cause);
    this.errorCode = errorCode;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }
}
