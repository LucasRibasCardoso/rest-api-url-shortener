package com.app.url_shortener.shared.ratelimit.core;

import java.time.Duration;

public record RateLimitDecision(boolean allowed, long remainingTokens, Duration retryAfter) {

  public static RateLimitDecision allowed(long remainingTokens) {
    return new RateLimitDecision(true, remainingTokens, Duration.ZERO);
  }

  public static RateLimitDecision denied(long remainingTokens, long retryAfterInNanos) {
    Duration retryAfter = Duration.ofNanos(retryAfterInNanos);
    return new RateLimitDecision(false, remainingTokens, retryAfter);
  }
}
