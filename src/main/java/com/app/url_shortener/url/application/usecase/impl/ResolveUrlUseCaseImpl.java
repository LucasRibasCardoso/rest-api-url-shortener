package com.app.url_shortener.url.application.usecase.impl;

import com.app.url_shortener.url.application.command.ResolveUrlCommand;
import com.app.url_shortener.url.application.port.output.RedirectCachePort;
import com.app.url_shortener.url.application.port.output.UrlRepositoryPort;
import com.app.url_shortener.url.application.result.ResolvedUrlResult;
import com.app.url_shortener.url.application.result.UrlRedirectCacheEntry;
import com.app.url_shortener.url.application.usecase.ResolveUrlUseCase;
import com.app.url_shortener.url.domain.exception.UrlNotFoundException;
import com.app.url_shortener.url.domain.model.Url;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResolveUrlUseCaseImpl implements ResolveUrlUseCase {

  private final RedirectCachePort redirectCachePort;
  private final UrlRepositoryPort urlRepositoryPort;

  @Override
  public ResolvedUrlResult execute(ResolveUrlCommand command) {
    String shortCode = command.shortCode();

    return redirectCachePort
        .findByShortCode(shortCode)
        .map(this::resolveCacheEntry)
        .orElseGet(() -> resolveCacheMiss(shortCode));
  }

  private ResolvedUrlResult resolveCacheEntry(UrlRedirectCacheEntry entry) {
    if (entry.isRedirectable()) {
      return new ResolvedUrlResult(entry.longUrl());
    }

    throw new UrlNotFoundException();
  }

  private ResolvedUrlResult resolveCacheMiss(String shortCode) {
    return urlRepositoryPort
        .findByShortCode(shortCode)
        .map(url -> resolvePersistedUrl(shortCode, url))
        .orElseGet(() -> cacheNotFound(shortCode));
  }

  private ResolvedUrlResult resolvePersistedUrl(String shortCode, Url url) {
    if (!url.isRedirectable()) {
      redirectCachePort.saveDeleted(shortCode);
      throw new UrlNotFoundException();
    }

    boolean cached = redirectCachePort.saveActiveIfAbsent(shortCode, url.getOriginalUrl());

    if (cached) {
      return new ResolvedUrlResult(url.getOriginalUrl());
    }

    return resolveAfterConcurrentCacheWrite(shortCode, url);
  }

  private ResolvedUrlResult resolveAfterConcurrentCacheWrite(String shortCode, Url url) {
    return redirectCachePort
        .findByShortCode(shortCode)
        .map(this::resolveCacheEntry)
        .orElseGet(() -> new ResolvedUrlResult(url.getOriginalUrl()));
  }

  private ResolvedUrlResult cacheNotFound(String shortCode) {
    redirectCachePort.saveNotFoundIfAbsent(shortCode);
    throw new UrlNotFoundException();
  }

}
