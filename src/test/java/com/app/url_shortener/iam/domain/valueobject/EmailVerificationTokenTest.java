package com.app.url_shortener.iam.domain.valueobject;

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
      // 1. Arrange
      var userId = UUID.randomUUID();
      var unformattedEmail = "   usuario@email.com   ";
      var code = VerificationCode.of("123456");
      var expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);

      // 2. Act
      var token = EmailVerificationToken.create(userId, unformattedEmail, code, expiresAt);

      // 3. Assert
      assertThat(token.userId()).isEqualTo(userId);
      assertThat(token.email()).isEqualTo("usuario@email.com");
      assertThat(token.code()).isEqualTo(code);
      assertThat(token.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("Deve lançar NullPointerException se atributos obrigatórios forem nulos")
    void shouldThrowExceptionIfAttributesAreNull() {
      // 1. Arrange
      var code = VerificationCode.of("123456");
      var expiresAt = Instant.now();

      // 2. Act
      var throwableAssert =
          assertThatThrownBy(() -> EmailVerificationToken.create(null, "email@mail.com", code, expiresAt));

      // 3. Assert
      throwableAssert
              .isInstanceOf(NullPointerException.class)
              .hasMessageContaining("userId is required");
    }

    @Test
    @DisplayName("Deve rejeitar e-mail em branco")
    void shouldRejectBlankEmail() {
      // 1. Arrange
      var userId = UUID.randomUUID();
      var code = VerificationCode.of("123456");
      var expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);

      // 2. Act
      var throwableAssert =
          assertThatThrownBy(() -> EmailVerificationToken.create(userId, "   ", code, expiresAt));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("email must not be blank");
    }
  }

  @Nested
  @DisplayName("Regras de Expiração (isExpired)")
  class ExpirationTests {

    @Test
    @DisplayName("Deve retornar true se a data de expiração for no passado")
    void shouldReturnTrueWhenExpired() {
      // 1. Arrange
      var pastExpiration = Instant.now().minus(1, ChronoUnit.MINUTES);
      var token = EmailVerificationToken.create(UUID.randomUUID(), "a@b.com", VerificationCode.of("123456"), pastExpiration);

      // 2. Act
      var isExpired = token.isExpired();

      // 3. Assert
      assertThat(isExpired).isTrue();
    }

    @Test
    @DisplayName("Deve retornar false se a data de expiração for no futuro")
    void shouldReturnFalseWhenNotExpired() {
      // 1. Arrange
      var futureExpiration = Instant.now().plus(1, ChronoUnit.MINUTES);
      var token = EmailVerificationToken.create(UUID.randomUUID(), "a@b.com", VerificationCode.of("123456"), futureExpiration);

      // 2. Act
      var isExpired = token.isExpired();

      // 3. Assert
      assertThat(isExpired).isFalse();
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
