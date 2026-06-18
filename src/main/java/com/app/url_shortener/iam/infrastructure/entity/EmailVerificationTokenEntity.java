package com.app.url_shortener.iam.infrastructure.entity;

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
    name = "email_verification_tokens",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_email_verification_tokens_id_user_id",
          columnNames = {"id", "user_id"})
    },
    indexes = {
      @Index(name = "idx_email_verification_tokens_user_id", columnList = "user_id"),
      @Index(name = "idx_email_verification_tokens_email", columnList = "email"),
      @Index(name = "idx_email_verification_tokens_user_email", columnList = "user_id, email"),
      @Index(name = "idx_email_verification_tokens_expires_at", columnList = "expires_at")
    })
public class EmailVerificationTokenEntity {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @Column(nullable = false, length = 320)
  private String email;

  @Column(name = "verification_code_hash", nullable = false, length = 255)
  private String hashedCode;

  @Column(name = "encrypted_code", nullable = false, columnDefinition = "TEXT")
  private String encryptedCode;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "failed_attempts", nullable = false)
  private int failedAttempts;

  @Column(name = "last_attempt_at")
  private Instant lastAttemptAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public EmailVerificationTokenEntity() {}

  public EmailVerificationTokenEntity(
      UUID id,
      UserEntity user,
      String email,
      String hashedCode,
      String encryptedCode,
      Instant expiresAt,
      Instant consumedAt,
      Instant revokedAt,
      int failedAttempts,
      Instant lastAttemptAt,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.user = user;
    this.email = email;
    this.hashedCode = hashedCode;
    this.encryptedCode = encryptedCode;
    this.expiresAt = expiresAt;
    this.consumedAt = consumedAt;
    this.revokedAt = revokedAt;
    this.failedAttempts = failedAttempts;
    this.lastAttemptAt = lastAttemptAt;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }
}
