package com.app.url_shortener.iam.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.url_shortener.config.BaseDataJpaSliceTest;
import com.app.url_shortener.iam.domain.exception.auth.DuplicateOpenEmailVerificationTokenException;
import com.app.url_shortener.iam.domain.model.EmailVerificationToken;
import com.app.url_shortener.iam.infrastructure.mapper.EmailVerificationTokenPersistenceMapperImpl;
import com.app.url_shortener.shared.config.JpaAuditingConfig;
import com.app.url_shortener.shared.database.DataIntegrityExceptionTranslator;
import com.app.url_shortener.shared.database.PostgresConstraintExtractor;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("jpa-slice")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
  EmailVerificationTokenRepositoryAdapter.class,
  EmailVerificationTokenPersistenceMapperImpl.class,
  JpaAuditingConfig.class,
  DataIntegrityExceptionTranslator.class,
  PostgresConstraintExtractor.class
})
@DisplayName("Slice Data JPA - Adaptador de Repositório de Tokens de Verificação de Email")
class EmailVerificationTokenRepositoryAdapterTest extends BaseDataJpaSliceTest {

  private static final Instant CREATED_AT = Instant.parse("2026-06-18T10:00:00Z");

  @Autowired private EmailVerificationTokenRepositoryAdapter adapter;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @Nested
  @DisplayName("Persistência")
  class SaveTests {

    @Test
    @DisplayName("Deve traduzir violação do índice parcial único de token aberto")
    void shouldTranslateDuplicateOpenTokenUniqueIndexViolation() {
      // 1. Arrange
      var userId = UUID.fromString("019a1f1f-a71d-79c2-a9da-7a8e1db50101");
      var email = "duplicate-open-token-adapter@email.com";
      insertUser(userId, email);
      adapter.save(token(UUID.fromString("019a1f1f-a71d-79c2-a9da-7a8e1db50201"), userId, email));

      var duplicateToken =
          token(UUID.fromString("019a1f1f-a71d-79c2-a9da-7a8e1db50202"), userId, email);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> adapter.save(duplicateToken));

      // 3. Assert
      throwableAssert.isInstanceOf(DuplicateOpenEmailVerificationTokenException.class);
    }
  }

  @Nested
  @DisplayName("Atualizações condicionais")
  class ConditionalUpdateTests {

    @Test
    @DisplayName("Deve revogar tokens abertos com transação própria")
    void shouldRevokeOpenTokensWithAdapterTransaction() {
      // 1. Arrange
      var userId = UUID.fromString("019a1f1f-a71d-79c2-a9da-7a8e1db50102");
      var tokenId = UUID.fromString("019a1f1f-a71d-79c2-a9da-7a8e1db50203");
      var email = "revoke-open-token-adapter@email.com";
      var now = CREATED_AT.plusSeconds(60);
      insertUser(userId, email);
      insertToken(tokenId, userId, email, CREATED_AT.plusSeconds(600), null, null);

      // 2. Act
      var updatedRows = adapter.revokeOpenByUserIdAndEmail(userId, email, now);

      // 3. Assert
      assertThat(updatedRows).isEqualTo(1);
      assertThat(timestampColumn(tokenId, "revoked_at")).isEqualTo(now);
    }

    @Test
    @DisplayName("Deve consumir token ativo com transação própria")
    void shouldConsumeActiveTokenWithAdapterTransaction() {
      // 1. Arrange
      var userId = UUID.fromString("019a1f1f-a71d-79c2-a9da-7a8e1db50103");
      var tokenId = UUID.fromString("019a1f1f-a71d-79c2-a9da-7a8e1db50204");
      var email = "consume-token-adapter@email.com";
      var now = CREATED_AT.plusSeconds(60);
      insertUser(userId, email);
      insertToken(tokenId, userId, email, CREATED_AT.plusSeconds(600), null, null);

      // 2. Act
      var consumed = adapter.consumeIfActive(tokenId, now);

      // 3. Assert
      assertThat(consumed).isTrue();
      assertThat(timestampColumn(tokenId, "consumed_at")).isEqualTo(now);
    }

    @Test
    @DisplayName("Deve registrar tentativa falha em token ativo com transação própria")
    void shouldRegisterFailedAttemptForActiveTokenWithAdapterTransaction() {
      // 1. Arrange
      var userId = UUID.fromString("019a1f1f-a71d-79c2-a9da-7a8e1db50104");
      var tokenId = UUID.fromString("019a1f1f-a71d-79c2-a9da-7a8e1db50205");
      var email = "failed-attempt-token-adapter@email.com";
      var now = CREATED_AT.plusSeconds(60);
      insertUser(userId, email);
      insertToken(tokenId, userId, email, CREATED_AT.plusSeconds(600), null, null);

      // 2. Act
      var registered = adapter.registerFailedAttempt(tokenId, now);

      // 3. Assert
      assertThat(registered).isTrue();
      assertThat(integerColumn(tokenId, "failed_attempts")).isOne();
      assertThat(timestampColumn(tokenId, "last_attempt_at")).isEqualTo(now);
    }

    @Test
    @DisplayName("Deve manter tentativa falha quando transação externa fizer rollback")
    void shouldKeepFailedAttemptWhenOuterTransactionRollsBack() {
      // 1. Arrange
      var userId = UUID.fromString("019a1f1f-a71d-79c2-a9da-7a8e1db50105");
      var tokenId = UUID.fromString("019a1f1f-a71d-79c2-a9da-7a8e1db50206");
      var email = "failed-attempt-rollback-token-adapter@email.com";
      var now = CREATED_AT.plusSeconds(60);
      var transactionTemplate = new TransactionTemplate(transactionManager);
      insertUser(userId, email);
      insertToken(tokenId, userId, email, CREATED_AT.plusSeconds(600), null, null);

      // 2. Act
      transactionTemplate.executeWithoutResult(
          status -> {
            adapter.registerFailedAttempt(tokenId, now);
            status.setRollbackOnly();
          });

      // 3. Assert
      assertThat(integerColumn(tokenId, "failed_attempts")).isOne();
      assertThat(timestampColumn(tokenId, "last_attempt_at")).isEqualTo(now);
    }
  }

  private static EmailVerificationToken token(UUID tokenId, UUID userId, String email) {
    var now = Instant.now();

    return EmailVerificationToken.restore(
        tokenId,
        userId,
        email,
        "verification-code-hash",
        "encrypted-code",
        now.plusSeconds(600),
        null,
        null,
        0,
        null,
        now,
        now);
  }

  private void insertUser(UUID userId, String email) {
    jdbcTemplate.update(
        """
        INSERT INTO users (id, name, email, password_hash, status, plan, email_verified)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        userId,
        "Email Verification User",
        email,
        "password-hash",
        "PENDING_EMAIL_VERIFICATION",
        "FREE",
        false);
  }

  private void insertToken(
      UUID tokenId,
      UUID userId,
      String email,
      Instant expiresAt,
      Instant consumedAt,
      Instant revokedAt) {
    jdbcTemplate.update(
        """
        INSERT INTO email_verification_tokens (
            id, user_id, email, verification_code_hash, encrypted_code, expires_at,
            consumed_at, revoked_at, failed_attempts, created_at, updated_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        tokenId,
        userId,
        email,
        "verification-code-hash",
        "encrypted-code",
        timestamp(expiresAt),
        timestamp(consumedAt),
        timestamp(revokedAt),
        0,
        timestamp(CREATED_AT),
        timestamp(CREATED_AT));
  }

  private Instant timestampColumn(UUID tokenId, String columnName) {
    return jdbcTemplate.queryForObject(
        "SELECT " + columnName + " FROM email_verification_tokens WHERE id = ?",
        Instant.class,
        tokenId);
  }

  private Integer integerColumn(UUID tokenId, String columnName) {
    return jdbcTemplate.queryForObject(
        "SELECT " + columnName + " FROM email_verification_tokens WHERE id = ?",
        Integer.class,
        tokenId);
  }

  private static Timestamp timestamp(Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }
}
