package com.app.url_shortener.url.domain.exception;

import com.app.url_shortener.shared.exception.conflict.ConflictException;

public class ShortCodeCollisionException extends ConflictException {

  public ShortCodeCollisionException() {
    super(UrlErrorCode.URL_SHORT_CODE_COLLISION);
  }
}
