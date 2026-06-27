package com.app.url_shortener.iam.infrastructure.mapper;

import com.app.url_shortener.iam.domain.model.EmailVerificationToken;
import com.app.url_shortener.iam.infrastructure.entity.EmailVerificationTokenEntity;
import com.app.url_shortener.iam.infrastructure.entity.UserEntity;
import jakarta.persistence.EntityManager;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public abstract class EmailVerificationTokenPersistenceMapper {

  @Autowired protected EntityManager entityManager;

  public EmailVerificationToken toDomain(EmailVerificationTokenEntity entity) {
    if (entity == null) {
      return null;
    }

    return EmailVerificationToken.restore(
        entity.getId(),
        entity.getUser().getId(),
        entity.getEmail(),
        entity.getHashedCode(),
        entity.getEncryptedCode(),
        entity.getExpiresAt(),
        entity.getConsumedAt(),
        entity.getRevokedAt(),
        entity.getFailedAttempts(),
        entity.getLastAttemptAt(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  public EmailVerificationTokenEntity toEntity(EmailVerificationToken domain) {
    if (domain == null) {
      return null;
    }

    UserEntity userProxy = entityManager.getReference(UserEntity.class, domain.getUserId());

    return new EmailVerificationTokenEntity(
        domain.getId(),
        userProxy,
        domain.getEmail(),
        domain.getHashedCode(),
        domain.getEncryptedCode(),
        domain.getExpiresAt(),
        domain.getConsumedAt(),
        domain.getRevokedAt(),
        domain.getFailedAttempts(),
        domain.getLastAttemptAt(),
        domain.getCreatedAt(),
        domain.getUpdatedAt());
  }
}
