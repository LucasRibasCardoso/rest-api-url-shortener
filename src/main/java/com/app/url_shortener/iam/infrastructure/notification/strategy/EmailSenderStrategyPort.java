package com.app.url_shortener.iam.infrastructure.notification.strategy;

public interface EmailSenderStrategyPort {

  void sendEmailVerificationCode(String email, String code);
}
