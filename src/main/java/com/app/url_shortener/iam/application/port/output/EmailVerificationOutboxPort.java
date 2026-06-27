package com.app.url_shortener.iam.application.port.output;

import com.app.url_shortener.iam.domain.enums.EmailDispatchReason;
import java.util.UUID;

public interface EmailVerificationOutboxPort {

  void publishEmailVerificationRequestedEvent(
      UUID userId, String email, EmailDispatchReason reason);
}
