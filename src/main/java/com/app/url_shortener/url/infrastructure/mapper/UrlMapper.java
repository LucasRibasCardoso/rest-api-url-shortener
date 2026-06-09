package com.app.url_shortener.url.infrastructure.mapper;

import com.app.url_shortener.url.application.result.UrlListItemResult;
import com.app.url_shortener.url.application.result.UrlRankingItemResult;
import com.app.url_shortener.url.domain.model.Url;
import com.app.url_shortener.url.infrastructure.entity.UrlEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UrlMapper {

  @Mapping(target = "createdAtShortCodeGsi", expression = "java(toCreatedAtShortCodeGsi(domain))")
  @Mapping(target = "statusCreatedAtShortCodeGsi", expression = "java(toStatusCreatedAtShortCodeGsi(domain))")
  @Mapping(target = "activeRankingUserIdGsi", expression = "java(toActiveRankingUserIdGsi(domain))")
  UrlEntity toEntity(Url domain);

  default Url toDomain(UrlEntity entity) {
    if (entity == null) {
      return null;
    }

    return Url.restore(
        entity.getUserId(),
        entity.getShortCode(),
        entity.getOriginalUrl(),
        entity.getCreatedAt(),
        entity.getStatus(),
        entity.getDeletedAt(),
        entity.getDeletedBy(),
        entity.getUpdatedAt(),
        entity.getAccessCount(),
        entity.getLastAccessedAt());
  }

  default UrlListItemResult toListItemResult(Url url) {
    if (url == null) {
      return null;
    }

    return new UrlListItemResult(
        url.getOriginalUrl(), url.getShortCode(), url.getCreatedAt(), url.getStatus());
  }

  default UrlRankingItemResult toRankingItemResult(Url url) {
    if (url == null) {
      return null;
    }

    return new UrlRankingItemResult(
        url.getOriginalUrl(),
        url.getShortCode(),
        url.getCreatedAt(),
        url.getStatus(),
        url.getAccessCount(),
        url.getLastAccessedAt());
  }

  default String toCreatedAtShortCodeGsi(Url url) {
    if (url == null) {
      return null;
    }

    return url.getCreatedAt() + "#" + url.getShortCode();
  }

  default String toStatusCreatedAtShortCodeGsi(Url url) {
    if (url == null) {
      return null;
    }

    return url.getStatus().name() + "#" + url.getCreatedAt() + "#" + url.getShortCode();
  }

  default String toActiveRankingUserIdGsi(Url url) {
    if (url == null || !url.isActive()) {
      return null;
    }

    return url.getUserId().toString();
  }
}
