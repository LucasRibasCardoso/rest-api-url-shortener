package com.app.url_shortener.iam.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.url_shortener.config.BaseDataJpaSliceTest;
import com.app.url_shortener.iam.infrastructure.repository.RefreshTokenJpaRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("jpa-slice")
@Import(RefreshTokenCleanupTask.class)
@DisplayName("Slice Data JPA - Tarefa de Limpeza de Refresh Tokens")
class RefreshTokenCleanupTaskTest extends BaseDataJpaSliceTest {

  private static final Instant CREATED_AT = Instant.parse("2026-05-07T12:00:00Z");

  @Autowired private RefreshTokenCleanupTask cleanupTask;

  @Autowired private RefreshTokenJpaRepository refreshTokenJpaRepository;

  @Autowired private TestEntityManager entityManager;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Nested
  @DisplayName("Limpeza de Tokens Expirados")
  class CleanupExpiredTokensTests {

    @Test
    @DisplayName("Deve remover somente tokens expirados antes do cutoff da query")
    void shouldDeleteOnlyTokensExpiredBeforeQueryCutoff() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ae101");
      var cutoff = Instant.parse("2026-05-07T12:00:00Z");

      insertUser(userId, "cleanup-query@email.com");
      insertRefreshToken(
          UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ae102"),
          userId,
          "query-expired-before-cutoff",
          cutoff.minusSeconds(1));
      insertRefreshToken(
          UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ae103"),
          userId,
          "query-expired-at-cutoff",
          cutoff);
      insertRefreshToken(
          UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ae104"),
          userId,
          "query-not-expired",
          cutoff.plusSeconds(1));
      entityManager.flush();
      entityManager.clear();

      // 2. Act
      var deletedCount = refreshTokenJpaRepository.deleteExpiredTokensBefore(cutoff);
      entityManager.flush();
      entityManager.clear();

      // 3. Assert
      assertThat(deletedCount).isEqualTo(1);
      assertThat(refreshTokenJpaRepository.findByTokenHash("query-expired-before-cutoff"))
          .isEmpty();
      assertThat(refreshTokenJpaRepository.findByTokenHash("query-expired-at-cutoff")).isPresent();
      assertThat(refreshTokenJpaRepository.findByTokenHash("query-not-expired")).isPresent();
    }

    @Test
    @DisplayName("Deve executar task e remover apenas tokens expirados há mais de sete dias")
    void shouldRunCleanupTaskAndDeleteOnlyTokensExpiredMoreThanSevenDaysAgo() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ae201");

      insertUser(userId, "cleanup-task@email.com");
      insertRefreshToken(
          UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ae202"),
          userId,
          "task-expired-eight-days-ago",
          Instant.now().minusSeconds(8 * 24 * 60 * 60));
      insertRefreshToken(
          UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ae203"),
          userId,
          "task-expired-six-days-ago",
          Instant.now().minusSeconds(6 * 24 * 60 * 60));
      insertRefreshToken(
          UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ae204"),
          userId,
          "task-future-token",
          Instant.now().plusSeconds(24 * 60 * 60));
      entityManager.flush();
      entityManager.clear();

      // 2. Act
      cleanupTask.cleanupExpiredTokens();
      entityManager.flush();
      entityManager.clear();

      // 3. Assert
      assertThat(refreshTokenJpaRepository.findByTokenHash("task-expired-eight-days-ago"))
          .isEmpty();
      assertThat(refreshTokenJpaRepository.findByTokenHash("task-expired-six-days-ago"))
          .isPresent();
      assertThat(refreshTokenJpaRepository.findByTokenHash("task-future-token")).isPresent();
    }
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
        true);
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
        Timestamp.from(expiresAt));
  }
}
