package com.app.url_shortener.iam.infrastructure.adapter;

import com.app.url_shortener.iam.application.port.output.CheckAuthRateLimitPort;
import com.app.url_shortener.shared.ratelimit.RateLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthRateLimitAdapter implements CheckAuthRateLimitPort {

  private final RateLimitService rateLimitService;

  @Override
  public void checkLogin(String clientIp, String email) {
    rateLimitService.checkLogin(clientIp, email);
  }

  @Override
  public void checkResendVerification(String email) {
    rateLimitService.checkResendVerification(email);
  }

  @Override
  public void checkVerifyEmail(String email) {
    rateLimitService.checkVerifyEmail(email);
  }
}
