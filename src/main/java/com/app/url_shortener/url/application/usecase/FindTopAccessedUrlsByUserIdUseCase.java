package com.app.url_shortener.url.application.usecase;

import com.app.url_shortener.url.application.command.UrlRankingCommand;
import com.app.url_shortener.url.application.result.UrlRankingResult;

public interface FindTopAccessedUrlsByUserIdUseCase {
  UrlRankingResult execute(UrlRankingCommand command);
}
