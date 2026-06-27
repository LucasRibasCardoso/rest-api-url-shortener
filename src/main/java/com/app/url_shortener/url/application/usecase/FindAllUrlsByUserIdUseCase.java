package com.app.url_shortener.url.application.usecase;

import com.app.url_shortener.url.application.command.FindAllUrlsByUserIdCommand;
import com.app.url_shortener.url.application.result.UrlPageResult;

public interface FindAllUrlsByUserIdUseCase {

  UrlPageResult execute(FindAllUrlsByUserIdCommand command);
}
