package com.app.url_shortener.url.application.usecase.impl;

import com.app.url_shortener.url.application.command.ShortenUrlCommand;
import com.app.url_shortener.url.application.port.output.CheckUrlRateLimitPort;
import com.app.url_shortener.url.application.port.output.IdGeneratorPort;
import com.app.url_shortener.url.application.port.output.RedirectCachePort;
import com.app.url_shortener.url.application.port.output.UrlEncoderPort;
import com.app.url_shortener.url.application.port.output.UrlRepositoryPort;
import com.app.url_shortener.url.application.result.ShortenUrlResult;
import com.app.url_shortener.url.application.usecase.ShortenUrlUseCase;
import com.app.url_shortener.url.application.validation.UrlSafetyValidator;
import com.app.url_shortener.url.domain.exception.RedirectCacheException;
import com.app.url_shortener.url.domain.model.Url;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortenUrlUseCaseImpl implements ShortenUrlUseCase {

  private final UrlSafetyValidator urlSafetyValidator;
  private final CheckUrlRateLimitPort checkUrlRateLimitPort;
  private final IdGeneratorPort idGeneratorPort;
  private final UrlEncoderPort urlEncoderPort;
  private final UrlRepositoryPort urlRepositoryPort;
  private final RedirectCachePort redirectCachePort;

  @Override
  public ShortenUrlResult execute(ShortenUrlCommand command) {
    String originalUrl = command.originalUrl();
    urlSafetyValidator.validate(originalUrl);
    checkUrlRateLimitPort.checkShorten(command.userId(), command.planType());

    long generatedId = idGeneratorPort.generateId();
    String shortCode = urlEncoderPort.encode(generatedId);

    Url url = Url.create(command.userId(), shortCode, originalUrl);
    urlRepositoryPort.save(url);
    saveActiveInRedirectCache(url);

    return new ShortenUrlResult(url.getOriginalUrl(), url.getShortCode(), url.getCreatedAt());
  }

  private void saveActiveInRedirectCache(Url url) {
    try {
      redirectCachePort.saveActive(url.getShortCode(), url.getOriginalUrl());
    } catch (RedirectCacheException exception) {
      log.warn(
          "Falha ao tentar salvar nova URL no cache de redirecionamento: {}",
          exception.getMessage());
    }
  }
}
