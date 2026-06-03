package com.app.url_shortener.url.infrastructure.mapper;

import com.app.url_shortener.url.domain.model.Url;
import com.app.url_shortener.url.infrastructure.entity.UrlEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UrlMapper {

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
        entity.getUpdatedAt());
  }

  default Url createUrl(UrlEntity entity) {
    return toDomain(entity);
  }
}
