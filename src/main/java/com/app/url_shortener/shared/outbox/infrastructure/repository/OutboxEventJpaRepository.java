package com.app.url_shortener.shared.outbox.infrastructure.repository;

import com.app.url_shortener.shared.outbox.infrastructure.entity.OutboxEventEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

  @Query(
      value =
          """
          SELECT *
          FROM outbox_events
          WHERE status = 'PENDING'
            AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
          ORDER BY created_at, id
          LIMIT :limit
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<OutboxEventEntity> findPendingToPublish(@Param("now") Instant now, @Param("limit") int limit);
}
