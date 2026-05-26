package com.app.url_shortener.shared.ratelimit.core;

import lombok.Getter;

@Getter
public enum RateLimitPolicy {
  AUTH_LOGIN("auth-login"),
  AUTH_VERIFY_EMAIL("auth-verify-email"),
  AUTH_RESEND_VERIFICATION("auth-resend-verification"),
  URL_SHORTEN_FREE("url-shorten-free"),
  URL_SHORTEN_PREMIUM("url-shorten-premium");

  private final String configKey;

  RateLimitPolicy(String configKey) {
    this.configKey = configKey;
  }

  public boolean isPremium() {
    return this == URL_SHORTEN_PREMIUM;
  }

  public boolean isFree() {
    return this == URL_SHORTEN_FREE;
  }
}
