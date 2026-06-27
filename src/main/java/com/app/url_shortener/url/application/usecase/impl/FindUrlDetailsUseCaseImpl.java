package com.app.url_shortener.url.application.usecase.impl;

import com.app.url_shortener.url.application.command.UrlDetailsCommand;
import com.app.url_shortener.url.application.port.output.UrlRepositoryPort;
import com.app.url_shortener.url.application.result.UrlDetailsResult;
import com.app.url_shortener.url.application.usecase.FindUrlDetailsUseCase;
import com.app.url_shortener.url.domain.exception.UrlNotFoundException;
import com.app.url_shortener.url.domain.model.Url;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FindUrlDetailsUseCaseImpl implements FindUrlDetailsUseCase {

  private final UrlRepositoryPort urlRepositoryPort;

  @Override
  public UrlDetailsResult execute(UrlDetailsCommand command) {
    Url url =
        urlRepositoryPort
            .findByShortCode(command.shortCode())
            .filter(
                candidate ->
                    command.canReadAny() || candidate.getUserId().equals(command.requesterId()))
            .orElseThrow(UrlNotFoundException::new);

    return new UrlDetailsResult(
        url.getShortCode(),
        url.getOriginalUrl(),
        url.getUserId(),
        url.getStatus(),
        url.getCreatedAt(),
        url.getUpdatedAt(),
        url.getDeletedAt(),
        url.getDeletedBy(),
        url.getAccessCount(),
        url.getLastAccessedAt());
  }
}
