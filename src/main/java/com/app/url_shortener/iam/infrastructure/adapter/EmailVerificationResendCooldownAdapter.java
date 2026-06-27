package com.app.url_shortener.iam.infrastructure.adapter;

import com.app.url_shortener.iam.application.port.output.EmailVerificationResendCooldownPort;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailVerificationResendCooldownAdapter implements EmailVerificationResendCooldownPort {

  private static final String KEY_PREFIX = "auth:email-verification:resend-cooldown:";

  private final StringRedisTemplate redisTemplate;

  @Override
  public boolean reserve(String email, Duration cooldown) {
    return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key(email), "1", cooldown));
  }

  private String key(String email) {
    return KEY_PREFIX + email;
  }
}
