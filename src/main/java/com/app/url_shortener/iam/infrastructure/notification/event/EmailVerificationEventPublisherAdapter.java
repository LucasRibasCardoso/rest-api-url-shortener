package com.app.url_shortener.iam.infrastructure.notification.event;

import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import com.app.url_shortener.iam.application.port.output.EmailVerificationEventPublisherPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailVerificationEventPublisherAdapter implements EmailVerificationEventPublisherPort {

  private final ApplicationEventPublisher applicationEventPublisher;

  @Override
  public void publish(EmailVerificationRequestedEvent event) {
    applicationEventPublisher.publishEvent(event);
  }
}
