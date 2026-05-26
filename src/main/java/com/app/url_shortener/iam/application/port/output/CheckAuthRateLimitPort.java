package com.app.url_shortener.iam.application.port.output;

public interface CheckAuthRateLimitPort {
  void checkLogin(String clientIp, String email);

  void checkResendVerification(String email);

  void checkVerifyEmail(String email);
}
