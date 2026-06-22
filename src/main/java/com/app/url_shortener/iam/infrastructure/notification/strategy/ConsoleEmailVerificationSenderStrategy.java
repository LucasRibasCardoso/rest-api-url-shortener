package com.app.url_shortener.iam.infrastructure.notification.strategy;

import com.app.url_shortener.iam.application.port.output.EmailVerificationSenderPort;
import com.app.url_shortener.iam.application.port.output.model.EmailVerificationSendResult;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
@ConditionalOnProperty(
    name = "app.iam.email-verification.sender",
    havingValue = "console")
public class ConsoleEmailVerificationSenderStrategy implements EmailVerificationSenderPort {

  @Override
  public EmailVerificationSendResult sendEmailVerificationCode(String email, String code) {
    String providerMessageId = UUID.randomUUID().toString();
    return new EmailVerificationSendResult(providerMessageId);
  }
}
