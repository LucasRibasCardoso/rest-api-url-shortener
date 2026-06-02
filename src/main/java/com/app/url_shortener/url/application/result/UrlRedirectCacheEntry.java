package com.app.url_shortener.url.application.result;

public record UrlRedirectCacheEntry(RedirectCacheStatus status, String longUrl) {

  public boolean isRedirectable() {
    return status == RedirectCacheStatus.ACTIVE && longUrl != null;
  }

  public boolean isActive() {
    return status == RedirectCacheStatus.ACTIVE;
  }
}
