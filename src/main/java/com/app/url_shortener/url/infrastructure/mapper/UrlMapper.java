package com.app.url_shortener.url.infrastructure.mapper;

import com.app.url_shortener.url.domain.model.Url;
import com.app.url_shortener.url.infrastructure.entity.UrlEntity;
import java.time.Instant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UrlMapper {

  @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "instantToString")
  UrlEntity toEntity(Url domain);

  default Url toDomain(UrlEntity entity) {
    if (entity == null) {
      return null;
    }

    return Url.restore(
            entity.getUserId(),
            entity.getShortCode(),
            entity.getOriginalUrl(),
            stringToInstant(entity.getCreatedAt()));
  }

  default Url createUrl(UrlEntity entity) {
    return toDomain(entity);
  }

  @Named("instantToString")
  default String instantToString(Instant value) {
    return value == null ? null : value.toString();
  }

  @Named("stringToInstant")
  default Instant stringToInstant(String value) {
    return value == null ? null : Instant.parse(value);
  }

}
