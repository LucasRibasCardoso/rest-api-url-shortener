package com.app.url_shortener.iam.domain.valueobject;

import com.app.url_shortener.iam.domain.valueobject.EmailVerificationToken;
import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@DisplayName("Testes de Unidade - Value Object EmailVerificationToken")
class EmailVerificationTokenTest {

  @Nested
  @DisplayName("Validações de Criação")
  class CreationTests {

    @Test
    @DisplayName("Deve aplicar trim no email e inicializar corretamente")
    void shouldCreateTokenAndTrimEmail() {
      // Arrange
      var userId = UUID.randomUUID();
      var unformattedEmail = "   usuario@email.com   ";
      var code = VerificationCode.of("123456");
      var expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);

      // Act
      var token = EmailVerificationToken.create(userId, unformattedEmail, code, expiresAt);

      // Assert
      assertThat(token.userId()).isEqualTo(userId);
      assertThat(token.email()).isEqualTo("usuario@email.com"); // Garante que aplicou o trim()
      assertThat(token.code()).isEqualTo(code);
      assertThat(token.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("Deve lançar NullPointerException se atributos obrigatórios forem nulos")
    void shouldThrowExceptionIfAttributesAreNull() {
      // Arrange, Act & Assert
      assertThatThrownBy(() -> EmailVerificationToken.create(null, "email@mail.com", VerificationCode.of("123456"), Instant.now()))
              .isInstanceOf(NullPointerException.class)
              .hasMessageContaining("userId is required");
    }
  }

  @Nested
  @DisplayName("Regras de Expiração (isExpired)")
  class ExpirationTests {

    @Test
    @DisplayName("Deve retornar true se a data de expiração for no passado")
    void shouldReturnTrueWhenExpired() {
      // Arrange
      var pastExpiration = Instant.now().minus(1, ChronoUnit.MINUTES);
      var token = EmailVerificationToken.create(UUID.randomUUID(), "a@b.com", VerificationCode.of("123456"), pastExpiration);

      // Act
      var isExpired = token.isExpired();

      // Assert
      assertThat(isExpired).isTrue();
    }

    @Test
    @DisplayName("Deve retornar false se a data de expiração for no futuro")
    void shouldReturnFalseWhenNotExpired() {
      // Arrange
      var futureExpiration = Instant.now().plus(1, ChronoUnit.MINUTES);
      var token = EmailVerificationToken.create(UUID.randomUUID(), "a@b.com", VerificationCode.of("123456"), futureExpiration);

      // Act
      var isExpired = token.isExpired();

      // Assert
      assertThat(isExpired).isFalse();
    }
  }

  @Nested
  @DisplayName("Regras de Comparação (matches)")
  class MatchingTests {

    @Test
    @DisplayName("Deve retornar true se o código fornecido for igual ao do token")
    void shouldReturnTrueWhenCodeMatches() {
      // Arrange
      var targetCode = VerificationCode.of("123456");
      var token = EmailVerificationToken.create(UUID.randomUUID(), "a@b.com", targetCode, Instant.now());
      var codeToTest = VerificationCode.of("123456");

      // Act
      var matches = token.matches(codeToTest);

      // Assert
      assertThat(matches).isTrue();
    }

    @Test
    @DisplayName("Deve retornar false se o código fornecido for diferente do token")
    void shouldReturnFalseWhenCodeDoesNotMatch() {
      // Arrange
      var token = EmailVerificationToken.create(UUID.randomUUID(), "a@b.com", VerificationCode.of("123456"), Instant.now());
      var incorrectCode = VerificationCode.of("999999");

      // Act
      var matches = token.matches(incorrectCode);

      // Assert
      assertThat(matches).isFalse();
    }
  }

  @Nested
  @DisplayName("Representação textual segura")
  class SafeToStringTests {

    @Test
    @DisplayName("Não deve expor o código de verificação no toString")
    void shouldNotExposeVerificationCodeInToString() {
      // 1. Arrange
      var rawCode = "123456";
      var token =
          EmailVerificationToken.create(
              UUID.randomUUID(),
              "usuario@email.com",
              VerificationCode.of(rawCode),
              Instant.now().plus(15, ChronoUnit.MINUTES));

      // 2. Act
      var text = token.toString();

      // 3. Assert
      assertThat(text)
          .doesNotContain(rawCode)
          .contains("[REDACTED]");
    }
  }
}
