package com.app.url_shortener.shared.outbox.infrastructure.adapter;

import com.app.url_shortener.shared.outbox.application.port.OutboxEventRepositoryPort;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEvent;
import com.app.url_shortener.shared.outbox.infrastructure.model.OutboxEventEntity;
import com.app.url_shortener.shared.outbox.infrastructure.repository.OutboxEventJpaRepository;
import com.app.url_shortener.shared.outbox.infrastructure.mapper.OutboxEventPersistenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OutboxEventRepositoryAdapter implements OutboxEventRepositoryPort {

  private final OutboxEventJpaRepository outboxEventJpaRepository;
  private final OutboxEventPersistenceMapper outboxEventPersistenceMapper;

  @Override
  public OutboxEvent save(OutboxEvent event) {
    OutboxEventEntity entity = outboxEventPersistenceMapper.toEntity(event);
    OutboxEventEntity savedEntity = outboxEventJpaRepository.save(entity);
    return outboxEventPersistenceMapper.toDomain(savedEntity);
  }
}
