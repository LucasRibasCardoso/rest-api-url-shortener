package com.app.url_shortener.iam.application.port.output;

import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;

public interface EmailVerificationEventPublisherPort {
  void publish(EmailVerificationRequestedEvent event);
}
