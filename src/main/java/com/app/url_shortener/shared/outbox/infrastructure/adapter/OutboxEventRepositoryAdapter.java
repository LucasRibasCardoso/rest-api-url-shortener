package com.app.url_shortener.shared.outbox.infrastructure.adapter;

import com.app.url_shortener.shared.outbox.application.port.OutboxEventRepositoryPort;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEvent;
import com.app.url_shortener.shared.outbox.infrastructure.entity.OutboxEventEntity;
import com.app.url_shortener.shared.outbox.infrastructure.mapper.OutboxEventPersistenceMapper;
import com.app.url_shortener.shared.outbox.infrastructure.repository.OutboxEventJpaRepository;
import java.time.Instant;
import java.util.List;
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

  @Override
  public List<OutboxEvent> findPendingToPublish(Instant now, int limit) {
    return outboxEventJpaRepository.findPendingToPublish(now, limit).stream()
        .map(outboxEventPersistenceMapper::toDomain)
        .toList();
  }

  @Override
  public void saveAll(List<OutboxEvent> events) {
    List<OutboxEventEntity> entities =
        events.stream().map(outboxEventPersistenceMapper::toEntity).toList();
    outboxEventJpaRepository.saveAll(entities);
  }
}
