package com.app.url_shortener.iam.infrastructure.entity;

import com.app.url_shortener.iam.domain.enums.EmailDispatchPurpose;
import com.app.url_shortener.iam.domain.enums.EmailDispatchReason;
import com.app.url_shortener.iam.domain.enums.EmailDispatchStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
    name = "email_dispatches",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_email_dispatches_event_id", columnNames = "event_id")
    },
    indexes = {
      @Index(name = "idx_email_dispatches_user_id", columnList = "user_id"),
      @Index(
          name = "idx_email_dispatches_verification_token_id",
          columnList = "verification_token_id"),
      @Index(name = "idx_email_dispatches_status", columnList = "status"),
      @Index(name = "idx_email_dispatches_user_email", columnList = "user_id, email"),
      @Index(
          name = "idx_email_dispatches_status_sending_started_at",
          columnList = "status, sending_started_at")
    })
public class EmailDispatchEntity {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "event_id", nullable = false, updatable = false)
  private UUID eventId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "verification_token_id", nullable = false)
  private UUID verificationTokenId;

  @Column(nullable = false, length = 320)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private EmailDispatchPurpose purpose;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private EmailDispatchReason reason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private EmailDispatchStatus status;

  @Column(name = "provider_message_id", length = 255)
  private String providerMessageId;

  @Column(name = "send_attempts", nullable = false)
  private int sendAttempts;

  @Column(name = "sending_started_at")
  private Instant sendingStartedAt;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @Column(name = "failed_at")
  private Instant failedAt;

  @Column(name = "last_error_code", length = 100)
  private String lastErrorCode;

  @Column(name = "last_error_message", columnDefinition = "TEXT")
  private String lastErrorMessage;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public EmailDispatchEntity() {}

  public EmailDispatchEntity(
      UUID id,
      UUID eventId,
      UUID userId,
      UUID verificationTokenId,
      String email,
      EmailDispatchPurpose purpose,
      EmailDispatchReason reason,
      EmailDispatchStatus status,
      String providerMessageId,
      int sendAttempts,
      Instant sendingStartedAt,
      Instant acceptedAt,
      Instant failedAt,
      String lastErrorCode,
      String lastErrorMessage,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.eventId = eventId;
    this.userId = userId;
    this.verificationTokenId = verificationTokenId;
    this.email = email;
    this.purpose = purpose;
    this.reason = reason;
    this.status = status;
    this.providerMessageId = providerMessageId;
    this.sendAttempts = sendAttempts;
    this.sendingStartedAt = sendingStartedAt;
    this.acceptedAt = acceptedAt;
    this.failedAt = failedAt;
    this.lastErrorCode = lastErrorCode;
    this.lastErrorMessage = lastErrorMessage;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }
}
