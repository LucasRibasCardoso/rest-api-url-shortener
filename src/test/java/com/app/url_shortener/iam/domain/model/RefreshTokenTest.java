package com.app.url_shortener.iam.domain.model;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
      var tokenHash = "hash-seguro-123";
      var now = Instant.now();

      // Act
      var token = RefreshToken.create(userId, tokenHash);

      // Assert
      assertThat(token).isNotNull();
      assertThat(token.getId()).isNotNull();
      assertThat(token.getUserId()).isEqualTo(userId);
      assertThat(token.getTokenHash()).isEqualTo(tokenHash);
      assertThat(token.isRevoked()).isFalse();

      var expectedExpiration = now.plus(7, ChronoUnit.DAYS);
      assertThat(token.getExpiresAt()).isCloseTo(expectedExpiration, within(1, ChronoUnit.SECONDS));
    }
  }

}