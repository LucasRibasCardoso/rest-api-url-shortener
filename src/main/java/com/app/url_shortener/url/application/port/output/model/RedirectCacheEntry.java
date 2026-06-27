package com.app.url_shortener.url.application.port.output.model;

public record RedirectCacheEntry(RedirectCacheStatus status, String originalUrl) {

  public boolean isRedirectable() {
    return status == RedirectCacheStatus.ACTIVE && originalUrl != null;
  }

  public boolean isActive() {
    return status == RedirectCacheStatus.ACTIVE;
  }
}
