package com.app.url_shortener.shared.outbox.domain.exception;

import com.app.url_shortener.shared.exception.CommonErrorCode;
import com.app.url_shortener.shared.exception.internalservererror.InternalServerErrorException;

public class OutboxPublishException extends InternalServerErrorException {

  public OutboxPublishException() {
    super(CommonErrorCode.OUTBOX_EVENT_PUBLISH_ERROR_EXCEPTION);
  }

  public OutboxPublishException(Throwable cause) {
    super(CommonErrorCode.OUTBOX_EVENT_PUBLISH_ERROR_EXCEPTION, cause);
  }
}
