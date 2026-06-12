package com.app.url_shortener.iam.infrastructure.notification.strategy;

import com.app.url_shortener.iam.application.port.output.EmailVerificationSenderPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"dev", "local", "test"})
public class ConsoleEmailVerificationSenderStrategy implements EmailVerificationSenderPort {

  @Override
  public void sendEmailVerificationCode(String email, String code) {
    log.info("========================================================");
    log.info("[DEV ONLY] Verification code generated for email={}: {}", email, code);
    log.info("========================================================");
  }
}
