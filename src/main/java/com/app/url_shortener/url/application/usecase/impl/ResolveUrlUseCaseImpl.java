package com.app.url_shortener.url.application.usecase.impl;

import com.app.url_shortener.url.application.command.ResolveUrlCommand;
import com.app.url_shortener.url.application.event.UrlRedirectedEvent;
import com.app.url_shortener.url.application.port.output.RedirectCachePort;
import com.app.url_shortener.url.application.port.output.UrlRedirectEventPublisherPort;
import com.app.url_shortener.url.application.port.output.UrlRepositoryPort;
import com.app.url_shortener.url.application.port.output.model.RedirectCacheEntry;
import com.app.url_shortener.url.application.result.ResolveUrlResult;
import com.app.url_shortener.url.application.usecase.ResolveUrlUseCase;
import com.app.url_shortener.url.domain.exception.RedirectCacheException;
import com.app.url_shortener.url.domain.exception.UrlNotFoundException;
import com.app.url_shortener.url.domain.model.Url;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResolveUrlUseCaseImpl implements ResolveUrlUseCase {

  private final RedirectCachePort redirectCachePort;
  private final UrlRepositoryPort urlRepositoryPort;
  private final UrlRedirectEventPublisherPort urlRedirectEventPublisherPort;

  @Override
  public ResolveUrlResult execute(ResolveUrlCommand command) {
    String shortCode = command.shortCode();
    ResolveUrlResult resolveUrlResult = resolve(shortCode);
    urlRedirectEventPublisherPort.publish(UrlRedirectedEvent.create(shortCode));
    return resolveUrlResult;
  }

  private ResolveUrlResult resolve(String shortCode) {
    Optional<RedirectCacheEntry> redirectCacheEntryOptional;

    try {
      redirectCacheEntryOptional = findInRedirectCache(shortCode);
    } catch (RedirectCacheException exception) {
      return resolveFromRepository(shortCode);
    }

    if (redirectCacheEntryOptional.isPresent()) {
      return resolveCachedUrl(redirectCacheEntryOptional.get());
    }

    return resolveFromRepository(shortCode);
  }

  private Optional<RedirectCacheEntry> findInRedirectCache(String shortCode) {
    try {
      return redirectCachePort.findByShortCode(shortCode);
    } catch (RedirectCacheException exception) {
      log.warn("Falha na leitura do cache de redirecionamento: {}", exception.getMessage());
      throw exception;
    }
  }

  private ResolveUrlResult resolveFromRepository(String shortCode) {
    Optional<Url> urlOptional = urlRepositoryPort.findByShortCode(shortCode);

    if (urlOptional.isPresent()) {
      return resolvePersistedUrl(shortCode, urlOptional.get());
    }

    saveNotFoundInRedirectCache(shortCode);
    throw new UrlNotFoundException();
  }

  private ResolveUrlResult resolveCachedUrl(RedirectCacheEntry redirectCacheEntry) {
    if (redirectCacheEntry.isRedirectable()) {
      return new ResolveUrlResult(redirectCacheEntry.originalUrl());
    }

    throw new UrlNotFoundException();
  }

  private ResolveUrlResult resolvePersistedUrl(String shortCode, Url url) {
    if (!url.isRedirectable()) {
      saveDeletedInRedirectCache(shortCode);
      throw new UrlNotFoundException();
    }

    boolean activeCacheEntryCreated = saveActiveIfAbsentInRedirectCache(shortCode, url);

    if (activeCacheEntryCreated) {
      return new ResolveUrlResult(url.getOriginalUrl());
    }

    return resolveWithCacheRecheck(shortCode, url);
  }

  private ResolveUrlResult resolveWithCacheRecheck(String shortCode, Url url) {
    Optional<RedirectCacheEntry> redirectCacheEntryOptional;

    try {
      redirectCacheEntryOptional = findInRedirectCache(shortCode);
    } catch (RedirectCacheException exception) {
      return new ResolveUrlResult(url.getOriginalUrl());
    }

    if (redirectCacheEntryOptional.isPresent()) {
      return resolveCachedUrl(redirectCacheEntryOptional.get());
    }

    return new ResolveUrlResult(url.getOriginalUrl());
  }

  private void saveDeletedInRedirectCache(String shortCode) {
    try {
      redirectCachePort.saveDeleted(shortCode);
    } catch (RedirectCacheException exception) {
      log.warn(
          "Falha ao gravar DELETED no cache; respondendo com not found: {}",
          exception.getMessage());
    }
  }

  private void saveNotFoundInRedirectCache(String shortCode) {
    try {
      redirectCachePort.saveNotFoundIfAbsent(shortCode);
    } catch (RedirectCacheException exception) {
      log.warn(
          "Falha ao gravar NOT_FOUND no cache; respondendo com not found: {}",
          exception.getMessage());
    }
  }

  private boolean saveActiveIfAbsentInRedirectCache(String shortCode, Url url) {
    try {
      return redirectCachePort.saveActiveIfAbsent(shortCode, url.getOriginalUrl());
    } catch (RedirectCacheException exception) {
      log.warn(
          "Falha ao gravar ACTIVE no cache; retornando resultado do repositório: {}",
          exception.getMessage());
      return true;
    }
  }
}
