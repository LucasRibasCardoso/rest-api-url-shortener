package com.app.url_shortener.url.application.usecase.impl;

import com.app.url_shortener.url.application.command.ResolveUrlCommand;
import com.app.url_shortener.url.application.event.UrlRedirectedEvent;
import com.app.url_shortener.url.application.port.output.RedirectCachePort;
import com.app.url_shortener.url.application.port.output.UrlRedirectEventPublisherPort;
import com.app.url_shortener.url.application.port.output.UrlRepositoryPort;
import com.app.url_shortener.url.application.result.ResolvedUrlResult;
import com.app.url_shortener.url.application.result.UrlRedirectCacheEntry;
import com.app.url_shortener.url.application.usecase.ResolveUrlUseCase;
import com.app.url_shortener.url.domain.exception.RedirectCacheException;
import com.app.url_shortener.url.domain.exception.UrlNotFoundException;
import com.app.url_shortener.url.domain.model.Url;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResolveUrlUseCaseImpl implements ResolveUrlUseCase {

  private static final Logger log = LoggerFactory.getLogger(ResolveUrlUseCaseImpl.class);

  private final RedirectCachePort redirectCachePort;
  private final UrlRepositoryPort urlRepositoryPort;
  private final UrlRedirectEventPublisherPort urlRedirectEventPublisherPort;

  @Override
  public ResolvedUrlResult execute(ResolveUrlCommand command) {
    String shortCode = command.shortCode();
    ResolvedUrlResult result = resolve(shortCode);
    urlRedirectEventPublisherPort.publishAsync(UrlRedirectedEvent.create(shortCode));
    return result;
  }

  private ResolvedUrlResult resolve(String shortCode) {
    Optional<UrlRedirectCacheEntry> urlCacheEntry;

    try {
      urlCacheEntry = fetchInCache(shortCode);
    } catch (RedirectCacheException e) {
      return fetchInRepository(shortCode);
    }

    if (urlCacheEntry.isPresent()) {
      return resolveCachedUrl(urlCacheEntry.get());
    }

    return fetchInRepository(shortCode);
  }

  private Optional<UrlRedirectCacheEntry> fetchInCache(String shortCode) {
    try {
      return redirectCachePort.findByShortCode(shortCode);
    } catch (RedirectCacheException e) {
      log.warn("Falha na leitura do cache de redirecionamento: {}", e.getMessage());
      throw e;
    }
  }

  private ResolvedUrlResult fetchInRepository(String shortCode) {
    Optional<Url> urlOpt = urlRepositoryPort.findByShortCode(shortCode);

    if (urlOpt.isPresent()) {
      return resolvePersistedUrl(shortCode, urlOpt.get());
    }

    saveStatusNotFoundInRedirectCache(shortCode);
    throw new UrlNotFoundException();
  }

  private ResolvedUrlResult resolveCachedUrl(UrlRedirectCacheEntry entry) {
    if (entry.isRedirectable()) {
      return new ResolvedUrlResult(entry.longUrl());
    }

    throw new UrlNotFoundException();
  }

  private ResolvedUrlResult resolvePersistedUrl(String shortCode, Url url) {
    if (!url.isRedirectable()) {
      saveStatusDeleteInRedirectCache(shortCode);
      throw new UrlNotFoundException();
    }

    boolean cached = saveStatusActiveInRedirectCache(shortCode, url);

    if (cached) {
      return new ResolvedUrlResult(url.getOriginalUrl());
    }

    return resolveWithCacheRecheck(shortCode, url);
  }

  private ResolvedUrlResult resolveWithCacheRecheck(String shortCode, Url url) {
    Optional<UrlRedirectCacheEntry> urlCacheEntry;

    try {
      urlCacheEntry = fetchInCache(shortCode);
    } catch (RedirectCacheException exception) {
      return new ResolvedUrlResult(url.getOriginalUrl());
    }

    if (urlCacheEntry.isPresent()) {
      return resolveCachedUrl(urlCacheEntry.get());
    }

    return new ResolvedUrlResult(url.getOriginalUrl());
  }

  private void saveStatusDeleteInRedirectCache(String shortCode) {
    try {
      redirectCachePort.saveDeleted(shortCode);
    } catch (RedirectCacheException exception) {
      log.warn("Falha ao gravar DELETED no cache; respondendo com not found: {}", exception.getMessage());
    }
  }

  private void saveStatusNotFoundInRedirectCache(String shortCode) {
    try {
      redirectCachePort.saveNotFoundIfAbsent(shortCode);
    } catch (RedirectCacheException exception) {
      log.warn("Falha ao gravar NOT_FOUND no cache; respondendo com not found: {}", exception.getMessage());
    }
  }

  private boolean saveStatusActiveInRedirectCache(String shortCode, Url url) {
    try {
      return redirectCachePort.saveActiveIfAbsent(shortCode, url.getOriginalUrl());
    } catch (RedirectCacheException exception) {
      log.warn("Falha ao gravar ACTIVE no cache; retornando resultado do repositório: {}", exception.getMessage());
      return true;
    }
  }
}
