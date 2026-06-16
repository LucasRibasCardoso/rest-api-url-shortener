package com.app.url_shortener.iam.infrastructure.adapter;

import com.app.url_shortener.iam.application.port.output.EmailVerificationIdempotencyPort;
import com.app.url_shortener.iam.application.port.output.model.EmailVerificationProcessingLease;
import com.app.url_shortener.iam.application.port.output.model.EmailVerificationProcessingLeaseStatus;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailVerificationIdempotencyAdapter implements EmailVerificationIdempotencyPort {

  private static final String KEY_PREFIX = "iam:email-verification:event-idempotency:";

  private static final String STATE_PROCESSING_PREFIX = EmailVerificationProcessingLeaseStatus.PROCESSING.name() + ":";
  private static final String STATE_COMPLETED = EmailVerificationProcessingLeaseStatus.COMPLETED.name();

  private static final String STATUS_ACQUIRED = EmailVerificationProcessingLeaseStatus.ACQUIRED.name();
  private static final String STATUS_PROCESSING = EmailVerificationProcessingLeaseStatus.PROCESSING.name();
  private static final String STATUS_COMPLETED = EmailVerificationProcessingLeaseStatus.COMPLETED.name();

  private static final DefaultRedisScript<String> ACQUIRE_PROCESSING_LEASE_SCRIPT =
      new DefaultRedisScript<>(
          """
                  local current = redis.call('GET', KEYS[1])
                  if not current then
                    redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[1])
                    return ARGV[3]
                  end
                  if current == ARGV[4] then
                    return ARGV[5]
                  end
                  if string.sub(current, 1, string.len(ARGV[6])) == ARGV[6] then
                    return ARGV[7]
                  end
                  return redis.error_reply('Unknown email verification idempotency state')
                  """,
          String.class);

  private static final DefaultRedisScript<Long> MARK_AS_COMPLETED_SCRIPT =
      new DefaultRedisScript<>(
          """
                  local current = redis.call('GET', KEYS[1])
                  if current == ARGV[1] then
                    redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
                    return 1
                  end
                  return 0
                  """,
          Long.class);

  private static final DefaultRedisScript<Long> RELEASE_PROCESSING_LEASE_SCRIPT =
      new DefaultRedisScript<>(
          """
                  local current = redis.call('GET', KEYS[1])
                  if current == ARGV[1] then
                    return redis.call('DEL', KEYS[1])
                  end
                  return 0
                  """,
          Long.class);

  private final StringRedisTemplate redisTemplate;

  @Override
  public EmailVerificationProcessingLease acquireProcessingLease(
      UUID eventId, Duration processingLeaseTtl) {
    var leaseId = UUID.randomUUID();
    var processingState = processingState(leaseId);
    String result =
        redisTemplate.execute(
            ACQUIRE_PROCESSING_LEASE_SCRIPT,
            List.of(key(eventId)),
            String.valueOf(positiveTtlMillis(processingLeaseTtl, "processingLeaseTtl")),
            processingState,
            STATUS_ACQUIRED,
            STATE_COMPLETED,
            STATUS_COMPLETED,
            STATE_PROCESSING_PREFIX,
            STATUS_PROCESSING);

    if (result == null) {
      throw new IllegalStateException("Redis did not return a processing lease status for email verification event. eventId=" + eventId);
    }

    return switch (EmailVerificationProcessingLeaseStatus.valueOf(result)) {
      case ACQUIRED -> EmailVerificationProcessingLease.acquired(leaseId);
      case PROCESSING -> EmailVerificationProcessingLease.processing();
      case COMPLETED -> EmailVerificationProcessingLease.completed();
    };
  }

  @Override
  public boolean markAsCompleted(UUID eventId, UUID leaseId, Duration idempotencyTtl) {
    Long result =
        redisTemplate.execute(
            MARK_AS_COMPLETED_SCRIPT,
            List.of(key(eventId)),
            processingState(leaseId),
            STATE_COMPLETED,
            String.valueOf(positiveTtlMillis(idempotencyTtl, "idempotencyTtl")));
    return Long.valueOf(1).equals(result);
  }

  @Override
  public boolean releaseProcessingLease(UUID eventId, UUID leaseId) {
    Long result =
        redisTemplate.execute(
            RELEASE_PROCESSING_LEASE_SCRIPT, List.of(key(eventId)), processingState(leaseId));
    return Long.valueOf(1).equals(result);
  }

  private String key(UUID eventId) {
    return KEY_PREFIX + Objects.requireNonNull(eventId, "eventId must not be null");
  }

  private String processingState(UUID leaseId) {
    return STATE_PROCESSING_PREFIX + Objects.requireNonNull(leaseId, "leaseId must not be null");
  }

  private long positiveTtlMillis(Duration ttl, String parameterName) {
    Objects.requireNonNull(ttl, parameterName + " must not be null");
    if (ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException(parameterName + " must be positive");
    }

    long ttlMillis = ttl.toMillis();
    if (ttlMillis == 0) {
      throw new IllegalArgumentException(parameterName + " must be at least 1 millisecond");
    }
    return ttlMillis;
  }
}
