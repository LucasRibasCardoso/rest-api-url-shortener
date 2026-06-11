package com.app.url_shortener.shared.outbox.infrastructure.repository;

import com.app.url_shortener.shared.outbox.infrastructure.model.OutboxEventEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {}
