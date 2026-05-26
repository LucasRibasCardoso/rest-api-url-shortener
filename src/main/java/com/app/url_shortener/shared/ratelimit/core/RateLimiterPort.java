package com.app.url_shortener.shared.ratelimit.core;

import com.app.url_shortener.shared.ratelimit.key.RateLimitKey;

public interface RateLimiterPort {
  RateLimitDecision consume(RateLimitPolicy policy, RateLimitKey key);
}
