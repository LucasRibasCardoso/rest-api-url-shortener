package com.app.url_shortener.iam.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - EmailVerificationConfig")
class EmailVerificationConfigTest {

  private final EmailVerificationConfig config = new EmailVerificationConfig();

  @Nested
  @DisplayName("Chave HMAC")
  class HmacSecretKeySpecTests {

    @Test
    @DisplayName("Deve criar SecretKeySpec para segredo HMAC válido")
    void shouldCreateSecretKeySpecForValidHmacSecret() {
      // 1. Arrange
      var properties = properties(base64SecretWith32Bytes());

      // 2. Act
      var secretKeySpec = config.verificationCodeHmacSecretKeySpec(properties);

      // 3. Assert
      assertThat(secretKeySpec.getAlgorithm()).isEqualTo("HmacSHA256");
      assertThat(secretKeySpec.getEncoded()).hasSize(32);
    }

    @Test
    @DisplayName("Deve rejeitar segredo HMAC que não esteja em Base64")
    void shouldRejectHmacSecretThatIsNotBase64Encoded() {
      // 1. Arrange
      var properties = properties("not-base64-secret");

      // 2. Act / 3. Assert
      assertThatThrownBy(() -> config.verificationCodeHmacSecretKeySpec(properties))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Email verification HMAC secret must be Base64 encoded");
    }

    @Test
    @DisplayName("Deve rejeitar segredo HMAC com menos de 32 bytes")
    void shouldRejectHmacSecretWithLessThan32Bytes() {
      // 1. Arrange
      var shortSecret = Base64.getEncoder().encodeToString("short-secret".getBytes());
      var properties = properties(shortSecret);

      // 2. Act / 3. Assert
      assertThatThrownBy(() -> config.verificationCodeHmacSecretKeySpec(properties))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Email verification HMAC secret must decode to at least 32 bytes");
    }
  }

  private static EmailVerificationProperties properties(String hmacSecret) {
    return new EmailVerificationProperties(
        Duration.ofMinutes(10),
        Duration.ofMinutes(1),
        Duration.ofSeconds(30),
        new EmailVerificationProperties.CodeProtection(
            "strong-encryption-password-32-bytes", "0123456789abcdef", hmacSecret));
  }

  private static String base64SecretWith32Bytes() {
    return Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes());
  }
}
