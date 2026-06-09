package com.app.url_shortener.iam.domain.model;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - Entidade RefreshToken")
class RefreshTokenTest {

  @Nested
  @DisplayName("Criação de Token")
  class CreationTests {

    @Test
    @DisplayName("Deve criar um novo token válido com expiração de 7 dias")
    void shouldCreateValidTokenWith7DaysExpiration() {
      // Arrange
      var userId = UUID.randomUUID();
      var tokenHash = "  hash-seguro-123  ";
      // Act
      var token = RefreshToken.create(userId, tokenHash);

      // Assert
      assertThat(token).isNotNull();
      assertThat(token.getId()).isNotNull();
      assertThat(token.getUserId()).isEqualTo(userId);
      assertThat(token.getTokenHash()).isEqualTo("hash-seguro-123");
      assertThat(token.isRevoked()).isFalse();

      assertThat(Duration.between(token.getCreatedAt(), token.getExpiresAt()))
          .isEqualTo(Duration.ofDays(7));
    }

    @Test
    @DisplayName("Deve rejeitar hash do token em branco")
    void shouldRejectBlankTokenHash() {
      // 1. Arrange
      var userId = UUID.randomUUID();

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> RefreshToken.create(userId, "   "));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("tokenHash must not be blank");
    }
  }

  @Nested
  @DisplayName("Representação textual segura")
  class SafeToStringTests {

    @Test
    @DisplayName("Não deve expor tokenHash no toString")
    void shouldNotExposeTokenHashInToString() {
      // 1. Arrange
      var tokenHash = "sensitive-token-hash";
      var token = RefreshToken.create(UUID.randomUUID(), tokenHash);

      // 2. Act
      var text = token.toString();

      // 3. Assert
      assertThat(text).doesNotContain(tokenHash).doesNotContain("tokenHash");
    }
  }
}
