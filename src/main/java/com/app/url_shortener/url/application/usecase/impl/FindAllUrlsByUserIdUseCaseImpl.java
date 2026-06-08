package com.app.url_shortener.url.application.usecase.impl;

import com.app.url_shortener.url.application.command.FindAllUrlsByUserIdCommand;
import com.app.url_shortener.url.application.port.output.UrlRepositoryPort;
import com.app.url_shortener.url.application.result.UrlPageResult;
import com.app.url_shortener.url.application.usecase.FindAllUrlsByUserIdUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FindAllUrlsByUserIdUseCaseImpl implements FindAllUrlsByUserIdUseCase {

  private final UrlRepositoryPort urlRepositoryPort;

  @Override
  public UrlPageResult execute(FindAllUrlsByUserIdCommand command) {
    return urlRepositoryPort.findAllByUserId(command.userId(), command.limit(), command.cursor(), command.status());
  }
}
