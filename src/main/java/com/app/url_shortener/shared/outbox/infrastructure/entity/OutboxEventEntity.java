package com.app.url_shortener.shared.outbox.infrastructure.entity;

import com.app.url_shortener.shared.outbox.domain.model.OutboxEventStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
    name = "outbox_events",
    indexes = {
      @Index(
          name = "idx_outbox_events_status_next_attempt_at",
          columnList = "status, next_attempt_at"),
      @Index(name = "idx_outbox_events_aggregate", columnList = "aggregate_type, aggregate_id"),
      @Index(name = "idx_outbox_events_event_type", columnList = "event_type")
    })
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEventEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "aggregate_type", nullable = false, length = 80)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false, length = 160)
  private String aggregateId;

  @Column(name = "event_type", nullable = false, length = 120)
  private String eventType;

  @Column(name = "schema_version", nullable = false)
  private int schemaVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private OutboxEventStatus status;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "last_error", columnDefinition = "text")
  private String lastError;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "next_attempt_at")
  private Instant nextAttemptAt;
}
