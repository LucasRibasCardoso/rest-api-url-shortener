package com.app.url_shortener.url.domain.exception;

import com.app.url_shortener.shared.exception.notfound.NotFoundException;

public class UrlNotFoundException extends NotFoundException {

  public UrlNotFoundException() {
    super(UrlErrorCode.URL_NOT_FOUND);
  }
}
