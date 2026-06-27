package com.app.url_shortener.iam.infrastructure.repository;

import com.app.url_shortener.iam.infrastructure.entity.EmailVerificationTokenEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EmailVerificationTokenJpaRepository
    extends JpaRepository<EmailVerificationTokenEntity, UUID> {

  @Query(
      """
      SELECT t
        FROM EmailVerificationTokenEntity t
       WHERE t.user.id = :userId
         AND t.email = :email
         AND t.consumedAt IS NULL
         AND t.revokedAt IS NULL
         AND t.expiresAt > :now
       ORDER BY t.createdAt DESC
      """)
  Optional<EmailVerificationTokenEntity> findActiveByUserIdAndEmail(
      UUID userId, String email, Instant now);

  @Query(
      """
          SELECT t
            FROM EmailVerificationTokenEntity t
           WHERE t.user.id = :userId
             AND t.email = :email
             AND t.consumedAt IS NULL
             AND t.revokedAt IS NULL
           ORDER BY t.createdAt DESC
          """)
  Optional<EmailVerificationTokenEntity> findOpenByUserIdAndEmail(UUID userId, String email);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      UPDATE EmailVerificationTokenEntity t
         SET t.revokedAt = :now,
             t.updatedAt = :now
       WHERE t.user.id = :userId
         AND t.email = :email
         AND t.consumedAt IS NULL
         AND t.revokedAt IS NULL
      """)
  int revokeOpenByUserIdAndEmail(UUID userId, String email, Instant now);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      UPDATE EmailVerificationTokenEntity t
         SET t.consumedAt = :now,
             t.updatedAt = :now
       WHERE t.id = :tokenId
         AND t.consumedAt IS NULL
         AND t.revokedAt IS NULL
         AND t.expiresAt > :now
      """)
  int consumeIfActive(UUID tokenId, Instant now);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      UPDATE EmailVerificationTokenEntity t
         SET t.failedAttempts = t.failedAttempts + 1,
             t.lastAttemptAt = :now,
             t.updatedAt = :now
       WHERE t.id = :tokenId
         AND t.consumedAt IS NULL
         AND t.revokedAt IS NULL
         AND t.expiresAt > :now
      """)
  int registerFailedAttempt(UUID tokenId, Instant now);
}
