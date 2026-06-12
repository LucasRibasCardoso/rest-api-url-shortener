package com.app.url_shortener.iam.application.port.output;

public interface EmailVerificationSenderPort {

  void sendEmailVerificationCode(String email, String code);
}
