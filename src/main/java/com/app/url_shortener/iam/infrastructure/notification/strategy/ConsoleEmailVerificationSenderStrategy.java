package com.app.url_shortener.iam.infrastructure.notification.strategy;

import com.app.url_shortener.iam.application.port.output.EmailVerificationSenderPort;
import com.app.url_shortener.iam.application.port.output.model.EmailVerificationSendResult;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"dev", "local", "test"})
public class ConsoleEmailVerificationSenderStrategy implements EmailVerificationSenderPort {

  @Override
  public EmailVerificationSendResult sendEmailVerificationCode(String email, String code) {
    log.info("========================================================");
    log.info("[DEV ONLY] Verification code generated for email={}: {}", email, code);
    log.info("========================================================");
    String providerMessageId = UUID.randomUUID().toString();
    return new EmailVerificationSendResult(providerMessageId);
  }
}
