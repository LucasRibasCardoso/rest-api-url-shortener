package com.app.url_shortener.iam.infrastructure.mapper;

import com.app.url_shortener.iam.domain.model.EmailDispatch;
import com.app.url_shortener.iam.infrastructure.entity.EmailDispatchEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public abstract class EmailDispatchPersistenceMapper {

  public EmailDispatch toDomain(EmailDispatchEntity entity) {
    if (entity == null) {
      return null;
    }

    return EmailDispatch.restore(
        entity.getId(),
        entity.getEventId(),
        entity.getUserId(),
        entity.getVerificationTokenId(),
        entity.getEmail(),
        entity.getPurpose(),
        entity.getReason(),
        entity.getStatus(),
        entity.getProviderMessageId(),
        entity.getSendAttempts(),
        entity.getSendingStartedAt(),
        entity.getAcceptedAt(),
        entity.getFailedAt(),
        entity.getLastErrorCode(),
        entity.getLastErrorMessage(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  public EmailDispatchEntity toEntity(EmailDispatch domain) {
    if (domain == null) {
      return null;
    }

    return new EmailDispatchEntity(
        domain.getId(),
        domain.getEventId(),
        domain.getUserId(),
        domain.getVerificationTokenId(),
        domain.getEmail(),
        domain.getPurpose(),
        domain.getReason(),
        domain.getStatus(),
        domain.getProviderMessageId(),
        domain.getSendAttempts(),
        domain.getSendingStartedAt(),
        domain.getAcceptedAt(),
        domain.getFailedAt(),
        domain.getLastErrorCode(),
        domain.getLastErrorMessage(),
        domain.getCreatedAt(),
        domain.getUpdatedAt());
  }
}
