package com.app.url_shortener.shared.idempotency.exception;

import com.app.url_shortener.shared.exception.CommonErrorCode;
import com.app.url_shortener.shared.exception.conflict.ConflictException;

public class IdempotencyConflictException extends ConflictException {

  public IdempotencyConflictException() {
    super(CommonErrorCode.IDEMPOTENCY_IN_PROCESSING);
  }
}
