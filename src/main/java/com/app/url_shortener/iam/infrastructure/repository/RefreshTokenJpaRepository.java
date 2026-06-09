package com.app.url_shortener.iam.infrastructure.repository;

import com.app.url_shortener.iam.infrastructure.entity.RefreshTokenEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, UUID> {

  Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
              update RefreshTokenEntity r
                 set r.revokedAt = :revokedAt
               where r.user.id = :userId
                 and r.revokedAt is null
          """)
  void revokeAllActiveTokensByUserId(
      @Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
              update RefreshTokenEntity rt
                 set rt.revokedAt = :revokedAt
               where rt.tokenHash = :tokenHash
                 and rt.revokedAt is null
          """)
  void revokeActiveTokenByHash(
      @Param("tokenHash") String tokenHash, @Param("revokedAt") Instant revokedAt);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
    update RefreshTokenEntity token
       set token.revokedAt = :rotatedAt,
           token.replacedByToken = :replacedByToken
     where token.tokenHash = :tokenHash
       and token.revokedAt is null
       and token.expiresAt > :rotatedAt
    """)
  int markTokenAsRotatedIfActive(
      @Param("tokenHash") String tokenHash,
      @Param("rotatedAt") Instant rotatedAt,
      @Param("replacedByToken") RefreshTokenEntity replacedByToken);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("DELETE FROM RefreshTokenEntity r WHERE r.expiresAt < :cutoffDate")
  int deleteExpiredTokensBefore(@Param("cutoffDate") Instant cutoffDate);
}
