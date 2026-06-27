package com.app.url_shortener.url.application.usecase;

import com.app.url_shortener.url.application.command.ResolveUrlCommand;
import com.app.url_shortener.url.application.result.ResolveUrlResult;

public interface ResolveUrlUseCase {
  ResolveUrlResult execute(ResolveUrlCommand command);
}
