package com.app.url_shortener.url.application.port.output;

import com.app.url_shortener.url.application.command.UrlStatusFilter;
import com.app.url_shortener.url.application.result.PageUrlResult;
import com.app.url_shortener.url.domain.model.Url;
import java.util.Optional;
import java.util.UUID;

public interface UrlRepositoryPort {

  void save(Url url);

  Optional<Url> findByShortCode(String shortCode);

  PageUrlResult findAllByUserId(UUID userId, int limit, String cursor, UrlStatusFilter status);

  void softDeleteByShortCode(Url url, UUID deletedBy);
}
