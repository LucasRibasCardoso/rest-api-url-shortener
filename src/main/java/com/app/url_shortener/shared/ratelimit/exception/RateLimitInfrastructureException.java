package com.app.url_shortener.shared.ratelimit.exception;

import com.app.url_shortener.shared.exception.CommonErrorCode;
import com.app.url_shortener.shared.exception.internalservererror.InternalServerErrorException;

public class RateLimitInfrastructureException extends InternalServerErrorException {

  public RateLimitInfrastructureException(Throwable cause) {
    super(CommonErrorCode.RATE_LIMIT_INFRASTRUCTURE_ERROR, cause);
  }

  public RateLimitInfrastructureException() {
    super(CommonErrorCode.RATE_LIMIT_INFRASTRUCTURE_ERROR);
  }
}
