package com.app.url_shortener.iam.application.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.url_shortener.iam.application.port.output.model.IssuedAccessToken;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - Representação textual segura dos resultados IAM")
class IamResultToStringTest {

  @Nested
  @DisplayName("Dados sensíveis")
  class SensitiveDataTests {

    @Test
    @DisplayName("Não deve expor tokens no LoginResult")
    void shouldNotExposeTokensInLoginResult() {
      // 1. Arrange
      var refreshToken = "raw-refresh-token";
      var accessToken = "jwt-access-token";
      var user =
          new AuthenticatedUserResult(
              UUID.randomUUID(), "User Name", "user@email.com", List.of("USER"), List.of(), "FREE");
      var result = new LoginResult(refreshToken, accessToken, "Bearer", 900L, user);

      // 2. Act
      var text = result.toString();

      // 3. Assert
      assertThat(text)
          .doesNotContain(refreshToken)
          .doesNotContain(accessToken)
          .contains("[REDACTED]");
    }

    @Test
    @DisplayName("Não deve expor tokens no RefreshTokenResult")
    void shouldNotExposeTokensInRefreshTokenResult() {
      // 1. Arrange
      var refreshToken = "new-raw-refresh-token";
      var accessToken = "new-jwt-access-token";
      var result = new RefreshTokenResult(refreshToken, accessToken);

      // 2. Act
      var text = result.toString();

      // 3. Assert
      assertThat(text)
          .doesNotContain(refreshToken)
          .doesNotContain(accessToken)
          .contains("[REDACTED]");
    }

    @Test
    @DisplayName("Não deve expor token no IssuedAccessToken")
    void shouldNotExposeTokenInIssuedAccessToken() {
      // 1. Arrange
      var accessToken = "jwt-access-token";
      var issuedAccessToken = new IssuedAccessToken(accessToken, 900L);

      // 2. Act
      var text = issuedAccessToken.toString();

      // 3. Assert
      assertThat(text)
          .doesNotContain(accessToken)
          .contains("[REDACTED]");
    }
  }
}
