package com.app.url_shortener.iam.infrastructure.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.url_shortener.config.BaseDataJpaSliceTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("jpa-slice")
@DisplayName("Slice Data JPA - Schema de Verificação de Email")
class EmailVerificationPersistenceSchemaTest extends BaseDataJpaSliceTest {

  private static final Instant CREATED_AT = Instant.parse("2026-06-18T10:00:00Z");

  @Autowired private JdbcTemplate jdbcTemplate;

  @Nested
  @DisplayName("Migration V9")
  class EmailVerificationTokensSchemaTests {

    @Test
    @DisplayName("Deve refletir colunas e constraints da tabela de tokens")
    void shouldReflectEmailVerificationTokensTableSchema() {
      // 1. Arrange

      // 2. Act
      var columns = columnsByName("email_verification_tokens");

      // 3. Assert
      assertThat(columns.keySet())
          .containsExactlyInAnyOrder(
              "id",
              "user_id",
              "email",
              "verification_code_hash",
              "encrypted_code",
              "expires_at",
              "consumed_at",
              "revoked_at",
              "failed_attempts",
              "last_attempt_at",
              "created_at",
              "updated_at");
      assertColumn(columns, "email", "character varying", 320, false);
      assertColumn(columns, "verification_code_hash", "character varying", 255, false);
      assertColumn(columns, "encrypted_code", "text", null, false);
      assertThat(
              hasConstraint(
                  "email_verification_tokens", "uk_email_verification_tokens_id_user_id", "u"))
          .isTrue();
      assertThat(
              hasConstraint("email_verification_tokens", "fk_email_verification_tokens_user", "f"))
          .isTrue();
      assertThat(hasIndex("email_verification_tokens", "idx_email_verification_tokens_open_lookup"))
          .isTrue();
      assertThat(hasPartialUniqueOpenTokenIndex()).isTrue();
    }

    @Test
    @DisplayName("Deve rejeitar dois tokens abertos para o mesmo usuário e email")
    void shouldRejectDuplicateOpenTokenForSameUserAndEmail() {
      // 1. Arrange
      var userId = UUID.fromString("019a1b4c-0c35-77b0-aaf4-336bd00f0101");
      insertUser(userId, "open-token@email.com");
      insertToken(
          UUID.fromString("019a1b4c-0c35-77b0-aaf4-336bd00f0201"),
          userId,
          "open-token@email.com",
          null,
          null);

      // 2. Act
      var throwableAssert =
          assertThatThrownBy(
              () ->
                  insertToken(
                      UUID.fromString("019a1b4c-0c35-77b0-aaf4-336bd00f0202"),
                      userId,
                      "open-token@email.com",
                      null,
                      null));

      // 3. Assert
      throwableAssert
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasRootCauseInstanceOf(PSQLException.class)
          .hasStackTraceContaining("uk_email_verification_tokens_open_user_email");
    }

    @Test
    @DisplayName("Deve permitir novo token quando o anterior estiver revogado")
    void shouldAllowNewTokenWhenPreviousTokenIsRevoked() {
      // 1. Arrange
      var userId = UUID.fromString("019a1b4c-0c35-77b0-aaf4-336bd00f0102");
      var email = "revoked-token@email.com";
      insertUser(userId, email);
      insertToken(
          UUID.fromString("019a1b4c-0c35-77b0-aaf4-336bd00f0211"),
          userId,
          email,
          null,
          CREATED_AT.plusSeconds(30));

      // 2. Act
      insertToken(
          UUID.fromString("019a1b4c-0c35-77b0-aaf4-336bd00f0212"), userId, email, null, null);

      // 3. Assert
      var tokenCount =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM email_verification_tokens WHERE user_id = ? AND email = ?",
              Integer.class,
              userId,
              email);
      assertThat(tokenCount).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("Migration V10")
  class EmailDispatchesSchemaTests {

    @Test
    @DisplayName("Deve refletir colunas, constraints e índices da tabela de dispatches")
    void shouldReflectEmailDispatchesTableSchema() {
      // 1. Arrange

      // 2. Act
      var columns = columnsByName("email_dispatches");

      // 3. Assert
      assertThat(columns.keySet())
          .containsExactlyInAnyOrder(
              "id",
              "event_id",
              "user_id",
              "verification_token_id",
              "email",
              "purpose",
              "reason",
              "status",
              "provider_message_id",
              "send_attempts",
              "sending_started_at",
              "accepted_at",
              "failed_at",
              "last_error_code",
              "last_error_message",
              "created_at",
              "updated_at");
      assertColumn(columns, "email", "character varying", 320, false);
      assertColumn(columns, "purpose", "character varying", 50, false);
      assertColumn(columns, "reason", "character varying", 50, false);
      assertColumn(columns, "status", "character varying", 30, false);
      assertColumn(columns, "provider_message_id", "character varying", 255, true);
      assertColumn(columns, "last_error_code", "character varying", 100, true);
      assertColumn(columns, "last_error_message", "text", null, true);
      assertThat(hasConstraint("email_dispatches", "uk_email_dispatches_event_id", "u")).isTrue();
      assertThat(hasConstraint("email_dispatches", "fk_email_dispatches_user", "f")).isTrue();
      assertThat(hasConstraint("email_dispatches", "fk_email_dispatches_verification_token", "f"))
          .isTrue();
      assertThat(hasConstraint("email_dispatches", "chk_email_dispatches_purpose", "c")).isTrue();
      assertThat(hasConstraint("email_dispatches", "chk_email_dispatches_reason", "c")).isTrue();
      assertThat(hasConstraint("email_dispatches", "chk_email_dispatches_status", "c")).isTrue();
      assertThat(hasIndex("email_dispatches", "idx_email_dispatches_status_sending_started_at"))
          .isTrue();
    }

    @Test
    @DisplayName("Deve rejeitar dispatch com token de outro usuário")
    void shouldRejectDispatchWhenTokenBelongsToAnotherUser() {
      // 1. Arrange
      var tokenOwnerId = UUID.fromString("019a1b4c-0c35-77b0-aaf4-336bd00f0111");
      var dispatchUserId = UUID.fromString("019a1b4c-0c35-77b0-aaf4-336bd00f0112");
      var tokenId = UUID.fromString("019a1b4c-0c35-77b0-aaf4-336bd00f0221");
      insertUser(tokenOwnerId, "token-owner@email.com");
      insertUser(dispatchUserId, "dispatch-user@email.com");
      insertToken(tokenId, tokenOwnerId, "token-owner@email.com", null, null);

      // 2. Act
      var throwableAssert =
          assertThatThrownBy(
              () ->
                  insertDispatch(
                      UUID.fromString("019a1b4c-0c35-77b0-aaf4-336bd00f0301"),
                      UUID.fromString("019a1b4c-0c35-77b0-aaf4-336bd00f0401"),
                      dispatchUserId,
                      tokenId,
                      "dispatch-user@email.com",
                      "EMAIL_VERIFICATION",
                      "REGISTER",
                      "PENDING"));

      // 3. Assert
      throwableAssert
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasRootCauseInstanceOf(PSQLException.class)
          .hasStackTraceContaining("fk_email_dispatches_verification_token");
    }

    @Test
    @DisplayName("Deve rejeitar valores fora dos enums persistidos como string")
    void shouldRejectInvalidEmailDispatchEnumValues() {
      // 1. Arrange
      var userId = UUID.fromString("019a1b4c-0c35-77b0-aaf4-336bd00f0113");
      var tokenId = UUID.fromString("019a1b4c-0c35-77b0-aaf4-336bd00f0222");
      insertUser(userId, "invalid-dispatch-enum@email.com");
      insertToken(tokenId, userId, "invalid-dispatch-enum@email.com", null, null);

      // 2. Act
      var throwableAssert =
          assertThatThrownBy(
              () ->
                  insertDispatch(
                      UUID.fromString("019a1b4c-0c35-77b0-aaf4-336bd00f0302"),
                      UUID.fromString("019a1b4c-0c35-77b0-aaf4-336bd00f0402"),
                      userId,
                      tokenId,
                      "invalid-dispatch-enum@email.com",
                      "EMAIL_VERIFICATION",
                      "REGISTER",
                      "INVALID"));

      // 3. Assert
      throwableAssert
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasRootCauseInstanceOf(PSQLException.class)
          .hasStackTraceContaining("chk_email_dispatches_status");
    }
  }

  private Map<String, ColumnMetadata> columnsByName(String tableName) {
    return jdbcTemplate
        .query(
            """
            SELECT column_name, data_type, character_maximum_length, is_nullable
              FROM information_schema.columns
             WHERE table_schema = 'public'
               AND table_name = ?
            """,
            (rs, rowNum) ->
                new ColumnMetadata(
                    rs.getString("column_name"),
                    rs.getString("data_type"),
                    (Integer) rs.getObject("character_maximum_length"),
                    "YES".equals(rs.getString("is_nullable"))),
            tableName)
        .stream()
        .collect(Collectors.toMap(ColumnMetadata::name, Function.identity()));
  }

  private void assertColumn(
      Map<String, ColumnMetadata> columns,
      String columnName,
      String dataType,
      Integer maxLength,
      boolean nullable) {
    assertThat(columns).containsKey(columnName);
    assertThat(columns.get(columnName).dataType()).isEqualTo(dataType);
    assertThat(columns.get(columnName).maxLength()).isEqualTo(maxLength);
    assertThat(columns.get(columnName).nullable()).isEqualTo(nullable);
  }

  private boolean hasConstraint(String tableName, String constraintName, String constraintType) {
    var count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
              FROM pg_constraint constraint_metadata
              JOIN pg_class table_metadata
                ON table_metadata.oid = constraint_metadata.conrelid
             WHERE table_metadata.relname = ?
               AND constraint_metadata.conname = ?
               AND constraint_metadata.contype = ?
            """,
            Integer.class,
            tableName,
            constraintName,
            constraintType);

    return count != null && count == 1;
  }

  private boolean hasIndex(String tableName, String indexName) {
    var count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
              FROM pg_indexes
             WHERE schemaname = 'public'
               AND tablename = ?
               AND indexname = ?
            """,
            Integer.class,
            tableName,
            indexName);

    return count != null && count == 1;
  }

  private boolean hasPartialUniqueOpenTokenIndex() {
    var count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
              FROM pg_indexes
             WHERE schemaname = 'public'
               AND tablename = 'email_verification_tokens'
               AND indexname = 'uk_email_verification_tokens_open_user_email'
               AND lower(indexdef) LIKE '%unique index%'
               AND lower(indexdef) LIKE '%where%'
               AND lower(indexdef) LIKE '%consumed_at is null%'
               AND lower(indexdef) LIKE '%revoked_at is null%'
            """,
            Integer.class);

    return count != null && count == 1;
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
      UUID tokenId, UUID userId, String email, Instant consumedAt, Instant revokedAt) {
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
        timestamp(CREATED_AT.plusSeconds(600)),
        timestamp(consumedAt),
        timestamp(revokedAt),
        0,
        timestamp(CREATED_AT),
        timestamp(CREATED_AT));
  }

  private void insertDispatch(
      UUID dispatchId,
      UUID eventId,
      UUID userId,
      UUID tokenId,
      String email,
      String purpose,
      String reason,
      String status) {
    jdbcTemplate.update(
        """
        INSERT INTO email_dispatches (
            id, event_id, user_id, verification_token_id, email, purpose, reason, status,
            send_attempts, created_at, updated_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        dispatchId,
        eventId,
        userId,
        tokenId,
        email,
        purpose,
        reason,
        status,
        0,
        timestamp(CREATED_AT),
        timestamp(CREATED_AT));
  }

  private static Timestamp timestamp(Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }

  private record ColumnMetadata(
      String name, String dataType, Integer maxLength, boolean nullable) {}
}
