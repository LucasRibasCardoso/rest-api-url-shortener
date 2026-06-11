package com.app.url_shortener.shared.outbox.domain.exception;

import com.app.url_shortener.shared.exception.CommonErrorCode;
import com.app.url_shortener.shared.exception.validation.DomainValidationException;

public class OutboxEventSerializationException extends DomainValidationException {

  public OutboxEventSerializationException() {
    super(CommonErrorCode.OUTBOX_EVENT_SERIALIZER_ERROR_EXCEPTION);
  }
}
