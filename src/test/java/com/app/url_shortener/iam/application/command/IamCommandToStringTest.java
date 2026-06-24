package com.app.url_shortener.iam.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - Representação textual segura dos comandos IAM")
class IamCommandToStringTest {

  @Nested
  @DisplayName("Dados sensíveis")
  class SensitiveDataTests {

    @Test
    @DisplayName("Não deve expor senha no LoginCommand")
    void shouldNotExposePasswordInLoginCommand() {
      // 1. Arrange
      var password = "plain-secret-password";
      var clientIp = "203.0.113.10";
      var command = new LoginCommand("user@email.com", password, clientIp);

      // 2. Act
      var text = command.toString();

      // 3. Assert
      assertThat(text)
          .doesNotContain(password)
          .doesNotContain(clientIp)
          .contains("[REDACTED]");
    }

    @Test
    @DisplayName("Não deve expor senha no RegisterUserCommand")
    void shouldNotExposePasswordInRegisterUserCommand() {
      // 1. Arrange
      var password = "plain-secret-password";
      var clientIp = "203.0.113.10";
      var command =
          new RegisterUserCommand(clientIp, "User Name", "user@email.com", password);

      // 2. Act
      var text = command.toString();

      // 3. Assert
      assertThat(text)
          .doesNotContain(password)
          .doesNotContain(clientIp)
          .contains("[REDACTED]");
    }

    @Test
    @DisplayName("Não deve expor refresh token no RefreshTokenCommand")
    void shouldNotExposeRefreshTokenInRefreshTokenCommand() {
      // 1. Arrange
      var refreshToken = "raw-refresh-token";
      var command = new RefreshTokenCommand(refreshToken);

      // 2. Act
      var text = command.toString();

      // 3. Assert
      assertThat(text).doesNotContain(refreshToken).contains("[REDACTED]");
    }

    @Test
    @DisplayName("Não deve expor refresh token no LogoutCommand")
    void shouldNotExposeRefreshTokenInLogoutCommand() {
      // 1. Arrange
      var refreshToken = "raw-refresh-token";
      var command = new LogoutCommand(refreshToken);

      // 2. Act
      var text = command.toString();

      // 3. Assert
      assertThat(text).doesNotContain(refreshToken).contains("[REDACTED]");
    }

    @Test
    @DisplayName("Não deve expor código de verificação no VerifyEmailCommand")
    void shouldNotExposeVerificationCodeInVerifyEmailCommand() {
      // 1. Arrange
      var rawCode = "123456";
      var command = new VerifyEmailCommand("user@email.com", VerificationCode.of(rawCode));

      // 2. Act
      var text = command.toString();

      // 3. Assert
      assertThat(text).doesNotContain(rawCode).contains("[REDACTED]");
    }
  }
}
