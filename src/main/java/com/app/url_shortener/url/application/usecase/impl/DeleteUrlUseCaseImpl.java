package com.app.url_shortener.url.application.usecase.impl;

import com.app.url_shortener.url.application.command.DeleteUrlCommand;
import com.app.url_shortener.url.application.port.output.RedirectCachePort;
import com.app.url_shortener.url.application.port.output.UrlRepositoryPort;
import com.app.url_shortener.url.application.usecase.DeleteUrlUseCase;
import com.app.url_shortener.url.domain.exception.UrlDeleteForbiddenException;
import com.app.url_shortener.url.domain.exception.UrlNotFoundException;
import com.app.url_shortener.url.domain.model.Url;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeleteUrlUseCaseImpl implements DeleteUrlUseCase {

  private final UrlRepositoryPort urlRepositoryPort;
  private final RedirectCachePort redirectCachePort;

  @Override
  public void execute(DeleteUrlCommand command) {

    var shortCode = command.shortCode();
    var requesterId = command.requesterId();
    var canDeleteAny = command.canDeleteAny();

    Url url = fetchUrlByShortCode(shortCode);
    validateCanDelete(url, requesterId, canDeleteAny);

    if (!url.isDeleted()) {
      urlRepositoryPort.softDeleteByShortCode(url, requesterId);
    }

    redirectCachePort.saveDeleted(shortCode);
  }

  private Url fetchUrlByShortCode(String shortCode) {
    return urlRepositoryPort.findByShortCode(shortCode).orElseThrow(UrlNotFoundException::new);
  }

  private void validateCanDelete(Url url, UUID requesterId, boolean canDeleteAny) {
    boolean isUrlOwnership = url.getUserId().equals(requesterId);

    if (!canDeleteAny && !isUrlOwnership) {
      throw new UrlDeleteForbiddenException();
    }
  }
}
