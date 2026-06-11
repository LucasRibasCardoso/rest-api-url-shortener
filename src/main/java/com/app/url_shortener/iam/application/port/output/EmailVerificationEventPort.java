package com.app.url_shortener.iam.application.port.output;

import com.app.url_shortener.iam.application.event.EmailVerificationReason;
import java.util.UUID;

public interface EmailVerificationEventPort {

  void publishEmailVerificationRequestedEvent(UUID userId, String email, EmailVerificationReason reason);
}
