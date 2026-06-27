package com.app.url_shortener.url.domain.exception;

import com.app.url_shortener.shared.exception.forbidden.ForbiddenException;

public class UrlDeleteForbiddenException extends ForbiddenException {

  public UrlDeleteForbiddenException() {
    super(UrlErrorCode.URL_DELETE_FORBIDDEN);
  }
}
