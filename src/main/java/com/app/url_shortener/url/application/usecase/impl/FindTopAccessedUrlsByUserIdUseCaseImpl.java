package com.app.url_shortener.url.application.usecase.impl;

import com.app.url_shortener.url.application.command.UrlRankingCommand;
import com.app.url_shortener.url.application.port.output.UrlRepositoryPort;
import com.app.url_shortener.url.application.result.UrlRankingResult;
import com.app.url_shortener.url.application.usecase.FindTopAccessedUrlsByUserIdUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FindTopAccessedUrlsByUserIdUseCaseImpl implements FindTopAccessedUrlsByUserIdUseCase {

  private final UrlRepositoryPort urlRepositoryPort;

  @Override
  public UrlRankingResult execute(UrlRankingCommand command) {
    return urlRepositoryPort.findTopAccessedActiveByUserId(command.userId(), command.rankingSize());
  }
}
