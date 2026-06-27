package com.app.url_shortener.shared.outbox.infrastructure.mapper;

import com.app.url_shortener.shared.outbox.domain.model.OutboxAggregateId;
import com.app.url_shortener.shared.outbox.domain.model.OutboxAggregateType;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEvent;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEventType;
import com.app.url_shortener.shared.outbox.infrastructure.entity.OutboxEventEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ObjectFactory;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OutboxEventPersistenceMapper {

  OutboxEventEntity toEntity(OutboxEvent event);

  default OutboxEvent toDomain(OutboxEventEntity entity) {
    if (entity == null) {
      return null;
    }

    return OutboxEvent.restore(
        entity.getId(),
        OutboxAggregateType.of(entity.getAggregateType()),
        OutboxAggregateId.of(entity.getAggregateId()),
        OutboxEventType.of(entity.getEventType()),
        entity.getSchemaVersion(),
        entity.getPayload(),
        entity.getStatus(),
        entity.getAttempts(),
        entity.getLastError(),
        entity.getCreatedAt(),
        entity.getPublishedAt(),
        entity.getNextAttemptAt());
  }

  @ObjectFactory
  default OutboxEventEntity createEntityObject(OutboxEvent event) {
    if (event == null) {
      return null;
    }

    return new OutboxEventEntity(
        event.getId(),
        event.getAggregateType().value(),
        event.getAggregateId().value(),
        event.getEventType().value(),
        event.getSchemaVersion(),
        event.getPayload(),
        event.getStatus(),
        event.getAttempts(),
        event.getLastError(),
        event.getCreatedAt(),
        event.getPublishedAt(),
        event.getNextAttemptAt());
  }
}
