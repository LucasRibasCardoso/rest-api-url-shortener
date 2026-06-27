package com.app.url_shortener.iam.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.url_shortener.iam.presentation.dto.request.LoginRequestDto;
import com.app.url_shortener.iam.presentation.dto.request.RegisterRequestDto;
import com.app.url_shortener.iam.presentation.dto.request.VerifyEmailRequestDto;
import com.app.url_shortener.iam.presentation.dto.response.AuthenticatedUserResponseDto;
import com.app.url_shortener.iam.presentation.dto.response.LoginResponseDto;
import com.app.url_shortener.iam.presentation.dto.response.RefreshTokenResponseDto;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - Representação textual segura dos DTOs IAM")
class IamDtoToStringTest {

  @Nested
  @DisplayName("Dados sensíveis")
  class SensitiveDataTests {

    @Test
    @DisplayName("Não deve expor senha no LoginRequestDto")
    void shouldNotExposePasswordInLoginRequestDto() {
      // 1. Arrange
      var password = "plain-secret-password";
      var dto = new LoginRequestDto("user@email.com", password);

      // 2. Act
      var text = dto.toString();

      // 3. Assert
      assertThat(text).doesNotContain(password).contains("[REDACTED]");
    }

    @Test
    @DisplayName("Não deve expor senha no RegisterRequestDto")
    void shouldNotExposePasswordInRegisterRequestDto() {
      // 1. Arrange
      var password = "plain-secret-password";
      var dto = new RegisterRequestDto("User Name", "user@email.com", password);

      // 2. Act
      var text = dto.toString();

      // 3. Assert
      assertThat(text).doesNotContain(password).contains("[REDACTED]");
    }

    @Test
    @DisplayName("Não deve expor código no VerifyEmailRequestDto")
    void shouldNotExposeCodeInVerifyEmailRequestDto() {
      // 1. Arrange
      var code = "123456";
      var dto = new VerifyEmailRequestDto("user@email.com", code);

      // 2. Act
      var text = dto.toString();

      // 3. Assert
      assertThat(text).doesNotContain(code).contains("[REDACTED]");
    }

    @Test
    @DisplayName("Não deve expor access token no LoginResponseDto")
    void shouldNotExposeAccessTokenInLoginResponseDto() {
      // 1. Arrange
      var accessToken = "jwt-access-token";
      var user =
          new AuthenticatedUserResponseDto(
              UUID.randomUUID(), "User Name", "user@email.com", "FREE", List.of("USER"));
      var dto = new LoginResponseDto(accessToken, "Bearer", 900L, user);

      // 2. Act
      var text = dto.toString();

      // 3. Assert
      assertThat(text).doesNotContain(accessToken).contains("[REDACTED]");
    }

    @Test
    @DisplayName("Não deve expor novo access token no RefreshTokenResponseDto")
    void shouldNotExposeAccessTokenInRefreshTokenResponseDto() {
      // 1. Arrange
      var accessToken = "new-jwt-access-token";
      var dto = new RefreshTokenResponseDto(accessToken);

      // 2. Act
      var text = dto.toString();

      // 3. Assert
      assertThat(text).doesNotContain(accessToken).contains("[REDACTED]");
    }
  }
}
