package com.app.url_shortener.url.application.port.output;

import com.app.url_shortener.url.application.command.UrlStatusFilter;
import com.app.url_shortener.url.application.result.UrlPageResult;
import com.app.url_shortener.url.application.result.UrlRankingResult;
import com.app.url_shortener.url.domain.model.Url;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UrlRepositoryPort {

  void save(Url url);

  Optional<Url> findByShortCode(String shortCode);

  UrlPageResult findAllByUserId(UUID userId, int limit, String cursor, UrlStatusFilter status);

  UrlRankingResult findTopAccessedActiveByUserId(UUID userId, int rankingSize);

  void softDeleteByShortCode(Url url, UUID deletedBy);

  void incrementAccessCount(String shortCode, long delta, Instant lastAccessedAt);
}
