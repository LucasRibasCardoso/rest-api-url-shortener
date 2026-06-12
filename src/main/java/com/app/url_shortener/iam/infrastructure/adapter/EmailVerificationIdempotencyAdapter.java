package com.app.url_shortener.iam.infrastructure.adapter;

import com.app.url_shortener.iam.application.port.output.EmailVerificationIdempotencyPort;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailVerificationIdempotencyAdapter implements EmailVerificationIdempotencyPort {

  private static final String KEY_PREFIX = "iam:email-verification:event-idempotency:";

  private final StringRedisTemplate redisTemplate;

  @Override
  public boolean tryMarkAsProcessed(UUID eventId, Duration ttl) {
    return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key(eventId), "processed", ttl));
  }

  @Override
  public void removeProcessedMark(UUID eventId) {
    redisTemplate.delete(key(eventId));
  }

  private String key(UUID eventId) {
    return KEY_PREFIX + eventId;
  }
}
