package com.app.url_shortener.iam.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - Propriedades de Verificação de Email")
class EmailVerificationPropertiesTest {

  @Test
  @DisplayName("Deve aceitar configuração válida")
  void shouldAcceptValidConfiguration() {
    // 1. Arrange
    var properties =
        new EmailVerificationProperties(
            Duration.ofMinutes(10),
            Duration.ofMinutes(1),
            Duration.ofSeconds(30),
            new EmailVerificationProperties.CodeProtection(
                "strong-encryption-password-32-bytes",
                "0123456789abcdef",
                base64SecretWith32Bytes()));

    // 2. Act
    var violations = validate(properties);

    // 3. Assert
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("Deve rejeitar durações ausentes ou não positivas")
  void shouldRejectMissingOrNonPositiveDurations() {
    // 1. Arrange
    var properties =
        new EmailVerificationProperties(
            null,
            Duration.ZERO,
            Duration.ofSeconds(-1),
            new EmailVerificationProperties.CodeProtection("", "", ""));

    // 2. Act
    var violations = validate(properties);

    // 3. Assert
    assertThat(violations)
        .extracting(violation -> violation.getPropertyPath().toString())
        .contains(
            "codeTtl",
            "sendingTimeout",
            "resendCooldown",
            "codeProtection.encryptionPassword",
            "codeProtection.encryptionSalt",
            "codeProtection.hmacSecret");
  }

  @Test
  @DisplayName("Deve rejeitar senha curta e salt fora do formato hexadecimal")
  void shouldRejectWeakEncryptionPasswordAndInvalidSaltFormat() {
    // 1. Arrange
    var properties =
        new EmailVerificationProperties(
            Duration.ofMinutes(10),
            Duration.ofMinutes(1),
            Duration.ofSeconds(30),
            new EmailVerificationProperties.CodeProtection(
                "short-password", "invalid-salt", base64SecretWith32Bytes()));

    // 2. Act
    var violations = validate(properties);

    // 3. Assert
    assertThat(violations)
        .extracting(violation -> violation.getPropertyPath().toString())
        .containsExactlyInAnyOrder("codeProtection.encryptionPassword", "codeProtection.encryptionSalt");
  }

  private static String base64SecretWith32Bytes() {
    return Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes());
  }

  private static java.util.Set<jakarta.validation.ConstraintViolation<EmailVerificationProperties>>
      validate(EmailVerificationProperties properties) {
    try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
      return validatorFactory.getValidator().validate(properties);
    }
  }
}
