package com.app.url_shortener.iam.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - Entidade EmailVerificationToken")
class EmailVerificationTokenTest {

  private static final UUID TOKEN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final Instant CREATED_AT = Instant.parse("2026-06-16T10:00:00Z");
  private static final Instant EXPIRES_AT = CREATED_AT.plus(Duration.ofMinutes(10));

  @Nested
  @DisplayName("Criação e restauração")
  class CreationAndRestoreTests {

    @Test
    @DisplayName("Deve criar token aberto com dados obrigatórios normalizados")
    void shouldCreateOpenTokenWithNormalizedRequiredData() {
      // 1. Arrange

      // 2. Act
      var token =
          EmailVerificationToken.create(
              USER_ID, " user@example.com ", " verification-code-hash ", " encrypted-code ", EXPIRES_AT, CREATED_AT);

      // 3. Assert
      assertThat(token.getId()).isNotNull();
      assertThat(token.getUserId()).isEqualTo(USER_ID);
      assertThat(token.getEmail()).isEqualTo("user@example.com");
      assertThat(token.getHashedCode()).isEqualTo("verification-code-hash");
      assertThat(token.getEncryptedCode()).isEqualTo("encrypted-code");
      assertThat(token.getExpiresAt()).isEqualTo(EXPIRES_AT);
      assertThat(token.getConsumedAt()).isNull();
      assertThat(token.getRevokedAt()).isNull();
      assertThat(token.getFailedAttempts()).isZero();
      assertThat(token.getLastAttemptAt()).isNull();
      assertThat(token.getCreatedAt()).isEqualTo(CREATED_AT);
      assertThat(token.getUpdatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    @DisplayName("Deve restaurar token consumido com dados persistidos")
    void shouldRestoreConsumedTokenWithPersistedData() {
      // 1. Arrange
      var consumedAt = CREATED_AT.plus(Duration.ofMinutes(2));
      var lastAttemptAt = CREATED_AT.plus(Duration.ofMinutes(1));
      var updatedAt = consumedAt;

      // 2. Act
      var token =
          restore(
              consumedAt,
              null,
              1,
              lastAttemptAt,
              updatedAt);

      // 3. Assert
      assertThat(token.getId()).isEqualTo(TOKEN_ID);
      assertThat(token.getUserId()).isEqualTo(USER_ID);
      assertThat(token.getConsumedAt()).isEqualTo(consumedAt);
      assertThat(token.getRevokedAt()).isNull();
      assertThat(token.getFailedAttempts()).isEqualTo(1);
      assertThat(token.getLastAttemptAt()).isEqualTo(lastAttemptAt);
      assertThat(token.isConsumed()).isTrue();
      assertThat(token.isRevoked()).isFalse();
    }
  }

  @Nested
  @DisplayName("Decisões de leitura")
  class ReadDecisionTests {

    @Test
    @DisplayName("Deve identificar token ativo")
    void shouldIdentifyActiveToken() {
      // 1. Arrange
      var token = restore(null, null, 0, null, CREATED_AT);

      // 2. Act
      var active = token.isActive(CREATED_AT.plus(Duration.ofMinutes(1)));

      // 3. Assert
      assertThat(active).isTrue();
      assertThat(token.isExpired(CREATED_AT.plus(Duration.ofMinutes(1)))).isFalse();
    }

    @Test
    @DisplayName("Deve identificar token expirado no instante de expiração")
    void shouldIdentifyExpiredTokenAtExpirationInstant() {
      // 1. Arrange
      var token = restore(null, null, 0, null, CREATED_AT);

      // 2. Act
      var expired = token.isExpired(EXPIRES_AT);

      // 3. Assert
      assertThat(expired).isTrue();
      assertThat(token.isActive(EXPIRES_AT)).isFalse();
    }

    @Test
    @DisplayName("Deve identificar token revogado como inativo")
    void shouldIdentifyRevokedTokenAsInactive() {
      // 1. Arrange
      var token = restore(null, CREATED_AT.plus(Duration.ofMinutes(1)), 0, null, CREATED_AT.plus(Duration.ofMinutes(1)));

      // 2. Act
      var active = token.isActive(CREATED_AT.plus(Duration.ofMinutes(2)));

      // 3. Assert
      assertThat(active).isFalse();
      assertThat(token.isRevoked()).isTrue();
    }
  }

  @Nested
  @DisplayName("Validação")
  class ValidationTests {

    @Test
    @DisplayName("Deve rejeitar tentativas negativas")
    void shouldRejectNegativeFailedAttempts() {
      // 1. Arrange

      // 2. Act / 3. Assert
      assertThatThrownBy(() -> restore(null, null, -1, null, CREATED_AT))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("failedAttempts must not be negative");
    }

    @Test
    @DisplayName("Deve rejeitar expiração não posterior à criação")
    void shouldRejectExpirationThatIsNotAfterCreation() {
      // 1. Arrange

      // 2. Act / 3. Assert
      assertThatThrownBy(
              () ->
                  EmailVerificationToken.restore(
                      TOKEN_ID,
                      USER_ID,
                      "user@example.com",
                      "verification-code-hash",
                      "encrypted-code",
                      CREATED_AT,
                      null,
                      null,
                      0,
                      null,
                      CREATED_AT,
                      CREATED_AT))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("expiresAt must be after createdAt");
    }

    @Test
    @DisplayName("Deve rejeitar token consumido e revogado ao mesmo tempo")
    void shouldRejectConsumedAndRevokedToken() {
      // 1. Arrange
      var consumedAt = CREATED_AT.plus(Duration.ofMinutes(1));
      var revokedAt = CREATED_AT.plus(Duration.ofMinutes(2));

      // 2. Act / 3. Assert
      assertThatThrownBy(() -> restore(consumedAt, revokedAt, 0, null, revokedAt))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Email verification token cannot be both consumed and revoked");
    }

    @Test
    @DisplayName("Deve rejeitar tentativas falhas sem lastAttemptAt")
    void shouldRejectFailedAttemptsWithoutLastAttemptAt() {
      // 1. Arrange

      // 2. Act / 3. Assert
      assertThatThrownBy(() -> restore(null, null, 1, null, CREATED_AT))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Email verification token with failed attempts requires lastAttemptAt");
    }

    @Test
    @DisplayName("Não deve expor hash ou código criptografado no toString")
    void shouldNotExposeProtectedCodeDataInToString() {
      // 1. Arrange
      var token = restore(null, null, 0, null, CREATED_AT);

      // 2. Act
      var result = token.toString();

      // 3. Assert
      assertThat(result)
          .doesNotContain("verification-code-hash")
          .doesNotContain("encrypted-code");
    }
  }

  private static EmailVerificationToken restore(
      Instant consumedAt,
      Instant revokedAt,
      int failedAttempts,
      Instant lastAttemptAt,
      Instant updatedAt) {
    return EmailVerificationToken.restore(
        TOKEN_ID,
        USER_ID,
        "user@example.com",
        "verification-code-hash",
        "encrypted-code",
        EXPIRES_AT,
        consumedAt,
        revokedAt,
        failedAttempts,
        lastAttemptAt,
        CREATED_AT,
        updatedAt);
  }
}
