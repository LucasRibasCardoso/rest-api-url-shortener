package com.app.url_shortener.url.domain.exception;

import com.app.url_shortener.shared.exception.CommonErrorCode;
import com.app.url_shortener.shared.exception.internalservererror.InternalServerErrorException;

public class RedirectCacheException extends InternalServerErrorException {

  public RedirectCacheException(Throwable cause) {
    super(CommonErrorCode.DEPENDENCY_FAILURE, cause);
  }
}
