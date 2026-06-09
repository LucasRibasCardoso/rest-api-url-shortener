package com.app.url_shortener.iam.infrastructure.adapter;

import com.app.url_shortener.iam.domain.model.RefreshToken;
import com.app.url_shortener.iam.infrastructure.mapper.RefreshTokenPersistenceMapper;
import com.app.url_shortener.iam.infrastructure.mapper.RefreshTokenPersistenceMapperImpl;
import com.app.url_shortener.iam.infrastructure.repository.RefreshTokenJpaRepository;
import com.app.url_shortener.config.BaseDataJpaSliceTest;
import com.app.url_shortener.shared.config.JpaAuditingConfig;
import org.junit.jupiter.api.*;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("jpa-slice")
@Import({
        RefreshTokenCleanupTask.class,
        RefreshTokenRepositoryAdapter.class,
        RefreshTokenPersistenceMapperImpl.class,
        JpaAuditingConfig.class
})
@DisplayName("Slice Data JPA - Adaptador de Repositório de Refresh Tokens")
class RefreshTokenRepositoryAdapterTest extends BaseDataJpaSliceTest {

  private static final Instant CREATED_AT = Instant.parse("2026-05-07T12:00:00Z");
  private static final Instant EXPIRES_AT = Instant.parse("2026-05-14T12:00:00Z");

  @Autowired
  private RefreshTokenRepositoryAdapter adapter;

  @Autowired
  private RefreshTokenCleanupTask cleanupTask;

  @Autowired
  private RefreshTokenJpaRepository refreshTokenJpaRepository;

  @Autowired
  private RefreshTokenPersistenceMapper refreshTokenPersistenceMapper;

  @Autowired
  private TestEntityManager entityManager;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Nested
  @DisplayName("Busca por hash")
  class FindByTokenHashTests {

    @Test
    @DisplayName("Deve encontrar token por hash e mapear relacionamento com usuário")
    void shouldFindByTokenHashAndMapUserRelationship() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad101");
      insertUser(userId, "token-owner@email.com");

      var token = refreshToken(
              UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad102"),
              userId,
              "hash-find-by-token",
              null,
              null
      );
      adapter.save(token);
      entityManager.flush();
      entityManager.clear();

      // 2. Act
      var result = adapter.findByTokenHash("hash-find-by-token");

      // 3. Assert
      assertThat(result).isPresent();
      assertThat(result.get().getId()).isEqualTo(token.getId());
      assertThat(result.get().getUserId()).isEqualTo(userId);
      assertThat(result.get().getTokenHash()).isEqualTo("hash-find-by-token");
      assertThat(result.get().getCreatedAt()).isNotNull();
      assertThat(result.get().getExpiresAt()).isEqualTo(EXPIRES_AT);
      assertThat(result.get().getRevokedAt()).isNull();
      assertThat(result.get().getReplacedByTokenId()).isNull();
    }

    @Test
    @DisplayName("Deve mapear relacionamento replacedByToken ao ler token rotacionado")
    void shouldMapReplacedByTokenRelationshipWhenReadingRotatedToken() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad201");
      var oldTokenId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad202");
      var newTokenId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad203");
      var revokedAt = Instant.parse("2026-05-08T10:00:00Z");

      insertUser(userId, "rotated@email.com");
      adapter.save(refreshToken(newTokenId, userId, "new-token-hash", null, null));
      entityManager.flush();

      adapter.save(refreshToken(oldTokenId, userId, "old-token-hash", revokedAt, newTokenId));
      entityManager.flush();
      entityManager.clear();

      // 2. Act
      var result = adapter.findByTokenHash("old-token-hash");

      // 3. Assert
      assertThat(result).isPresent();
      assertThat(result.get().getId()).isEqualTo(oldTokenId);
      assertThat(result.get().getRevokedAt()).isEqualTo(revokedAt);
      assertThat(result.get().getReplacedByTokenId()).isEqualTo(newTokenId);
    }
  }

  @Nested
  @DisplayName("Revogação e rotação por queries customizadas")
  class RevokeQueryTests {

    @Test
    @DisplayName("Deve revogar apenas tokens ativos do usuário informado")
    void shouldRevokeOnlyActiveTokensForGivenUser() {
      // 1. Arrange
      var targetUserId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad301");
      var otherUserId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad302");
      var alreadyRevokedAt = Instant.parse("2026-05-08T08:00:00Z");

      insertUser(targetUserId, "target-revoke@email.com");
      insertUser(otherUserId, "other-revoke@email.com");

      adapter.save(refreshToken(UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad303"), targetUserId, "target-active-1", null, null));
      adapter.save(refreshToken(UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad304"), targetUserId, "target-active-2", null, null));
      adapter.save(refreshToken(UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad305"), targetUserId, "target-revoked", alreadyRevokedAt, null));
      adapter.save(refreshToken(UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad306"), otherUserId, "other-active", null, null));
      entityManager.flush();
      entityManager.clear();

      // 2. Act
      adapter.revokeAllTokensForUser(targetUserId);
      entityManager.flush();
      entityManager.clear();

      // 3. Assert
      assertThat(revokedAt("target-active-1")).isNotNull();
      assertThat(revokedAt("target-active-2")).isNotNull();
      assertThat(revokedAt("target-revoked")).isEqualTo(alreadyRevokedAt);
      assertThat(revokedAt("other-active")).isNull();
    }

    @Test
    @DisplayName("Deve revogar somente token ativo pelo hash informado")
    void shouldRevokeOnlyActiveTokenByHash() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad401");
      var alreadyRevokedAt = Instant.parse("2026-05-08T09:00:00Z");

      insertUser(userId, "hash-revoke@email.com");
      adapter.save(refreshToken(UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad402"), userId, "hash-to-revoke", null, null));
      adapter.save(refreshToken(UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad403"), userId, "hash-to-keep", null, null));
      adapter.save(refreshToken(UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad404"), userId, "hash-already-revoked", alreadyRevokedAt, null));
      entityManager.flush();
      entityManager.clear();

      // 2. Act
      adapter.revokeActiveTokenByHash("hash-to-revoke");
      adapter.revokeActiveTokenByHash("hash-already-revoked");
      entityManager.flush();
      entityManager.clear();

      // 3. Assert
      assertThat(revokedAt("hash-to-revoke")).isNotNull();
      assertThat(revokedAt("hash-to-keep")).isNull();
      assertThat(revokedAt("hash-already-revoked")).isEqualTo(alreadyRevokedAt);
    }

    @Test
    @DisplayName("Deve marcar token ativo como rotacionado")
    void shouldMarkActiveTokenAsRotated() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad405");
      var oldTokenHash = "active-token-to-rotate";
      var newTokenId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad407");
      var rotatedAt = Instant.parse("2026-05-08T10:15:30Z");

      insertUser(userId, "active-rotation@email.com");
      adapter.save(refreshToken(UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad406"), userId, oldTokenHash, null, null));
      adapter.save(refreshToken(newTokenId, userId, "new-active-rotation-token", null, null));
      entityManager.flush();
      entityManager.clear();

      // 2. Act
      var updatedRows = adapter.markTokenAsRotatedIfActive(oldTokenHash, rotatedAt, newTokenId);
      entityManager.flush();
      entityManager.clear();

      // 3. Assert
      assertThat(updatedRows).isEqualTo(1);
      assertThat(revokedAt(oldTokenHash)).isEqualTo(rotatedAt);
      assertThat(replacedByTokenId(oldTokenHash)).isEqualTo(newTokenId);
    }

    @Test
    @DisplayName("Não deve rotacionar token já revogado")
    void shouldNotRotateAlreadyRevokedToken() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad408");
      var oldTokenHash = "already-rotated-token";
      var firstNewTokenId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad40a");
      var anotherNewTokenId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad40b");
      var originalRevokedAt = Instant.parse("2026-05-08T11:00:00Z");
      var anotherRotatedAt = Instant.parse("2026-05-08T12:00:00Z");

      insertUser(userId, "already-rotated@email.com");
      adapter.save(refreshToken(firstNewTokenId, userId, "first-new-rotation-token", null, null));
      adapter.save(refreshToken(
              UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad409"),
              userId,
              oldTokenHash,
              originalRevokedAt,
              firstNewTokenId
      ));
      adapter.save(refreshToken(anotherNewTokenId, userId, "another-new-rotation-token", null, null));
      entityManager.flush();
      entityManager.clear();

      // 2. Act
      var updatedRows = adapter.markTokenAsRotatedIfActive(oldTokenHash, anotherRotatedAt, anotherNewTokenId);
      entityManager.flush();
      entityManager.clear();

      // 3. Assert
      assertThat(updatedRows).isZero();
      assertThat(revokedAt(oldTokenHash)).isEqualTo(originalRevokedAt);
      assertThat(replacedByTokenId(oldTokenHash)).isEqualTo(firstNewTokenId);
    }

    @Test
    @DisplayName("Não deve rotacionar token expirado")
    void shouldNotRotateExpiredToken() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad40c");
      var expiredTokenHash = "expired-token-to-rotate";
      var newTokenId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad40e");
      var now = Instant.parse("2026-05-08T10:15:30Z");

      insertUser(userId, "expired-rotation@email.com");
      insertRefreshToken(
              UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad40d"),
              userId,
              expiredTokenHash,
              Instant.parse("2026-05-08T10:15:29Z")
      );
      adapter.save(refreshToken(newTokenId, userId, "new-expired-rotation-token", null, null));
      entityManager.flush();
      entityManager.clear();

      // 2. Act
      var updatedRows = adapter.markTokenAsRotatedIfActive(expiredTokenHash, now, newTokenId);
      entityManager.flush();
      entityManager.clear();

      // 3. Assert
      assertThat(updatedRows).isZero();
      assertThat(revokedAt(expiredTokenHash)).isNull();
      assertThat(replacedByTokenId(expiredTokenHash)).isNull();
    }
  }

  @Nested
  @DisplayName("Constraints")
  class ConstraintTests {

    @Test
    @DisplayName("Deve rejeitar hash de token duplicado")
    void shouldRejectDuplicateTokenHash() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad501");
      insertUser(userId, "duplicate-token@email.com");

      adapter.save(refreshToken(UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad502"), userId, "duplicate-hash", null, null));
      entityManager.flush();
      entityManager.clear();

      var duplicateToken = refreshToken(
              UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad503"),
              userId,
              "duplicate-hash",
              null,
              null
      );

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> {
        refreshTokenJpaRepository.saveAndFlush(refreshTokenPersistenceMapper.toEntity(duplicateToken));
      });

      // 3. Assert
      throwableAssert
              .isInstanceOf(DataIntegrityViolationException.class)
              .hasRootCauseInstanceOf(PSQLException.class)
              .hasMessageContaining("refresh_tokens_token_hash_key");
    }

    @Test
    @DisplayName("Deve rejeitar token para usuário inexistente")
    void shouldRejectTokenForMissingUser() {
      // 1. Arrange
      var missingUserId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad601");
      var token = refreshToken(
              UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad602"),
              missingUserId,
              "missing-user-hash",
              null,
              null
      );

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> {
        refreshTokenJpaRepository.saveAndFlush(refreshTokenPersistenceMapper.toEntity(token));
      });

      // 3. Assert
      throwableAssert
              .isInstanceOf(DataIntegrityViolationException.class)
              .hasRootCauseInstanceOf(PSQLException.class)
              .hasMessageContaining("refresh_tokens_user_id_fkey");
    }

    @Test
    @DisplayName("Deve remover refresh tokens por ON DELETE CASCADE ao excluir usuário")
    void shouldCascadeDeleteRefreshTokensWhenUserIsDeleted() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad701");
      insertUser(userId, "cascade-refresh@email.com");
      adapter.save(refreshToken(UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad702"), userId, "cascade-hash", null, null));
      entityManager.flush();
      entityManager.clear();

      // 2. Act
      jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
      entityManager.flush();
      entityManager.clear();

      // 3. Assert
      assertThat(refreshTokenJpaRepository.findByTokenHash("cascade-hash")).isEmpty();
    }
  }

  @Nested
  @DisplayName("Limpeza de tokens expirados")
  class CleanupExpiredTokensTests {

    @Test
    @DisplayName("Deve remover apenas tokens expirados há mais de sete dias")
    void shouldDeleteOnlyTokensExpiredMoreThanSevenDaysAgo() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad801");
      insertUser(userId, "cleanup-query@email.com");

      insertRefreshToken(
              UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad802"),
              userId,
              "cleanup-old-expired",
              Instant.now().minusSeconds(8 * 24 * 60 * 60)
      );
      insertRefreshToken(
              UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad803"),
              userId,
              "cleanup-recent-expired",
              Instant.now().minusSeconds(6 * 24 * 60 * 60)
      );
      insertRefreshToken(
              UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad804"),
              userId,
              "cleanup-future",
              Instant.now().plusSeconds(24 * 60 * 60)
      );
      entityManager.flush();
      entityManager.clear();

      // 2. Act
      var deletedCount = refreshTokenJpaRepository.deleteExpiredTokensBefore(Instant.now().minusSeconds(7 * 24 * 60 * 60));
      entityManager.flush();
      entityManager.clear();

      // 3. Assert
      assertThat(deletedCount).isEqualTo(1);
      assertThat(refreshTokenJpaRepository.findByTokenHash("cleanup-old-expired")).isEmpty();
      assertThat(refreshTokenJpaRepository.findByTokenHash("cleanup-recent-expired")).isPresent();
      assertThat(refreshTokenJpaRepository.findByTokenHash("cleanup-future")).isPresent();
    }

    @Test
    @DisplayName("Deve executar task e preservar tokens expirados recentemente")
    void shouldRunCleanupTaskAndPreserveRecentlyExpiredTokens() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad901");
      insertUser(userId, "cleanup-task@email.com");

      insertRefreshToken(
              UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad902"),
              userId,
              "task-old-expired",
              Instant.now().minusSeconds(8 * 24 * 60 * 60)
      );
      insertRefreshToken(
              UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad903"),
              userId,
              "task-recent-expired",
              Instant.now().minusSeconds(6 * 24 * 60 * 60)
      );
      entityManager.flush();
      entityManager.clear();

      // 2. Act
      cleanupTask.cleanupExpiredTokens();
      entityManager.flush();
      entityManager.clear();

      // 3. Assert
      assertThat(refreshTokenJpaRepository.findByTokenHash("task-old-expired")).isEmpty();
      assertThat(refreshTokenJpaRepository.findByTokenHash("task-recent-expired")).isPresent();
    }
  }

  private RefreshToken refreshToken(
          UUID id,
          UUID userId,
          String tokenHash,
          Instant revokedAt,
          UUID replacedByTokenId
  ) {
    return RefreshToken.restore(
            id,
            userId,
            tokenHash,
            CREATED_AT,
            EXPIRES_AT,
            revokedAt,
            replacedByTokenId
    );
  }

  private void insertUser(UUID userId, String email) {
    jdbcTemplate.update(
            """
                    INSERT INTO users (id, name, email, password_hash, status, plan, email_verified)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
            userId,
            "User Name",
            email,
            "password-hash",
            "ACTIVE",
            "FREE",
            true
    );
  }

  private void insertRefreshToken(UUID id, UUID userId, String tokenHash, Instant expiresAt) {
    jdbcTemplate.update(
            """
                    INSERT INTO refresh_tokens (id, user_id, token_hash, created_at, expires_at)
                    VALUES (?, ?, ?, ?, ?)
                    """,
            id,
            userId,
            tokenHash,
            Timestamp.from(CREATED_AT),
            Timestamp.from(expiresAt)
    );
  }

  private Instant revokedAt(String tokenHash) {
    return jdbcTemplate.queryForObject(
            "SELECT revoked_at FROM refresh_tokens WHERE token_hash = ?",
            Instant.class,
            tokenHash
    );
  }

  private UUID replacedByTokenId(String tokenHash) {
    return jdbcTemplate.queryForObject(
            "SELECT replaced_by_token_id FROM refresh_tokens WHERE token_hash = ?",
            UUID.class,
            tokenHash
    );
  }
}
