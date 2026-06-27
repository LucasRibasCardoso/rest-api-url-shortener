package com.app.url_shortener.url.domain.exception;

import com.app.url_shortener.shared.exception.validation.DomainValidationException;

public class UnsafeUrlException extends DomainValidationException {

  public UnsafeUrlException() {
    super(UrlErrorCode.URL_UNSAFE_DESTINATION);
  }
}
