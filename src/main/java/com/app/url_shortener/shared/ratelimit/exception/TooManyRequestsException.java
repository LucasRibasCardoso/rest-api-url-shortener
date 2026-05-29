package com.app.url_shortener.shared.ratelimit.exception;

import com.app.url_shortener.shared.exception.AppBusinessException;
import com.app.url_shortener.shared.exception.CommonErrorCode;
import com.app.url_shortener.shared.exception.ErrorCode;
import java.time.Duration;

public class TooManyRequestsException extends AppBusinessException {

  private final Duration retryAfter;

  public TooManyRequestsException(Duration retryAfter) {
    super(CommonErrorCode.TOO_MANY_REQUESTS);
    this.retryAfter = retryAfter;
  }

  protected TooManyRequestsException(ErrorCode errorCode, Duration retryAfter) {
    super(errorCode);
    this.retryAfter = retryAfter;
  }

  public long getRetryAfterInSeconds() {
    long seconds = retryAfter.getSeconds();
    if (retryAfter.getNano() > 0) {
      return seconds + 1;
    }

    return seconds;
  }
}
