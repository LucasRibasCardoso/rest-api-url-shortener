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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ShortenUrlUseCaseImpl implements ShortenUrlUseCase {

  private static final Logger log = LoggerFactory.getLogger(ShortenUrlUseCaseImpl.class);

  private final UrlEncoderPort urlEncoderPort;
  private final IdGeneratorPort idGeneratorService;
  private final UrlRepositoryPort urlRepositoryPort;
  private final RedirectCachePort redirectCachePort;
  private final CheckUrlRateLimitPort checkUrlRateLimitPort;
  private final UrlSafetyValidator urlSafetyValidator;

  @Override
  public ShortenUrlResult execute(ShortenUrlCommand command) {
    urlSafetyValidator.validate(command.originalUrl());
    checkUrlRateLimitPort.checkShorten(command.userId(), command.planType());

    long uniqueId = idGeneratorService.generateId();
    String shortCode = urlEncoderPort.encode(uniqueId);

    Url url = Url.create(command.userId(), shortCode, command.originalUrl());
    urlRepositoryPort.save(url);
    saveStatusActiveInRedirectCache(url);

    return new ShortenUrlResult(url.getOriginalUrl(), url.getShortCode(), url.getCreatedAt());
  }

  private void saveStatusActiveInRedirectCache(Url url) {
    try {
      redirectCachePort.saveActive(url.getShortCode(), url.getOriginalUrl());
    } catch (RedirectCacheException exception) {
      log.warn("Falha ao tentar salvar nova URL no cache de redirecionamento: {}", exception.getMessage());
    }
  }

}
