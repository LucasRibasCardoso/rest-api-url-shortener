package com.app.url_shortener.url.domain.exception;

import com.app.url_shortener.shared.exception.validation.DomainValidationException;

public class InvalidUrlCursorException extends DomainValidationException {

  public InvalidUrlCursorException() {
    super(UrlErrorCode.URL_CURSOR_INVALID);
  }
}
