package com.app.url_shortener.iam.infrastructure.mapper;

import com.app.url_shortener.iam.domain.model.RefreshToken;
import com.app.url_shortener.iam.infrastructure.entity.RefreshTokenEntity;
import com.app.url_shortener.iam.infrastructure.entity.UserEntity;
import jakarta.persistence.EntityManager;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ObjectFactory;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public abstract class RefreshTokenPersistenceMapper {

  @Autowired protected EntityManager entityManager;

  @Mapping(target = "userId", ignore = true)
  @Mapping(target = "replacedByTokenId", ignore = true)
  public abstract RefreshToken toDomain(RefreshTokenEntity entity);

  @Mapping(target = "user", ignore = true)
  @Mapping(target = "replacedByToken", ignore = true)
  public abstract RefreshTokenEntity toEntity(RefreshToken domain);

  @ObjectFactory
  public RefreshToken createDomainObject(RefreshTokenEntity entity) {
    if (entity == null) {
      return null;
    }

    return RefreshToken.restore(
        entity.getId(),
        entity.getUser() != null ? entity.getUser().getId() : null,
        entity.getTokenHash(),
        entity.getCreatedAt(),
        entity.getExpiresAt(),
        entity.getRevokedAt(),
        entity.getReplacedByToken() != null ? entity.getReplacedByToken().getId() : null);
  }

  @ObjectFactory
  public RefreshTokenEntity createEntityObject(RefreshToken domain) {
    if (domain == null) {
      return null;
    }

    UserEntity userProxy = entityManager.getReference(UserEntity.class, domain.getUserId());

    RefreshTokenEntity replacedByProxy =
        domain.getReplacedByTokenId() != null
            ? entityManager.getReference(RefreshTokenEntity.class, domain.getReplacedByTokenId())
            : null;

    return new RefreshTokenEntity(
        domain.getId(),
        userProxy,
        domain.getTokenHash(),
        domain.getCreatedAt(),
        domain.getExpiresAt(),
        domain.getRevokedAt(),
        replacedByProxy);
  }
}
