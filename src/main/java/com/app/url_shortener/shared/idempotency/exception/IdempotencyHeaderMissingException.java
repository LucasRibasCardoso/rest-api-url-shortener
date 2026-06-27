package com.app.url_shortener.shared.idempotency.exception;

import com.app.url_shortener.shared.exception.CommonErrorCode;
import com.app.url_shortener.shared.exception.validation.DomainValidationException;

public class IdempotencyHeaderMissingException extends DomainValidationException {

  public IdempotencyHeaderMissingException() {
    super(CommonErrorCode.IDEMPOTENCY_HEADER_MISSING);
  }
}
