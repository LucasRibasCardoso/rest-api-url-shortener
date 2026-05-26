package com.app.url_shortener.url.application.usecase.impl;

import com.app.url_shortener.url.application.command.ShortenUrlCommand;
import com.app.url_shortener.url.application.port.output.CheckUrlRateLimitPort;
import com.app.url_shortener.url.application.port.output.UrlEncoderPort;
import com.app.url_shortener.url.application.port.output.UrlRepositoryPort;
import com.app.url_shortener.url.application.port.output.IdGeneratorPort;
import com.app.url_shortener.url.application.result.ShortenUrlResult;
import com.app.url_shortener.url.application.usecase.ShortenUrlUseCase;
import com.app.url_shortener.url.domain.model.Url;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ShortenUrlUseCaseImpl implements ShortenUrlUseCase {

  private final UrlEncoderPort urlEncoderPort;
  private final IdGeneratorPort idGeneratorService;
  private final UrlRepositoryPort urlRepositoryPort;
  private final CheckUrlRateLimitPort  checkUrlRateLimitPort;

  @Override
  public ShortenUrlResult execute(ShortenUrlCommand command) {
    checkUrlRateLimitPort.checkShorten(command.userId(), command.planType());

    long uniqueId = idGeneratorService.generateId();
    String shortCode = urlEncoderPort.encode(uniqueId);

    Url url = Url.create(command.userId(), shortCode, command.originalUrl());
    urlRepositoryPort.save(url);

    return toResult(url);
  }

  private ShortenUrlResult toResult(Url url) {
    return new ShortenUrlResult(url.getOriginalUrl(), url.getShortCode(), url.getCreatedAt());
  }
}
