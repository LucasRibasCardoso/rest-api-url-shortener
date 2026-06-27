package com.app.url_shortener.shared.outbox.domain.exception;

import com.app.url_shortener.shared.exception.CommonErrorCode;
import com.app.url_shortener.shared.exception.internalservererror.InternalServerErrorException;

public class OutboxEventSerializationException extends InternalServerErrorException {

  public OutboxEventSerializationException(Throwable cause) {
    super(CommonErrorCode.OUTBOX_EVENT_SERIALIZER_ERROR_EXCEPTION, cause);
  }
}
