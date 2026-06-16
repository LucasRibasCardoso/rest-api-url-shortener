package com.app.url_shortener.iam.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.time.Duration;
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
            Duration.ofMinutes(10), Duration.ofDays(4), Duration.ofMinutes(2));

    // 2. Act
    var violations = validate(properties);

    // 3. Assert
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("Deve rejeitar durações ausentes ou não positivas")
  void shouldRejectMissingOrNonPositiveDurations() {
    // 1. Arrange
    var properties = new EmailVerificationProperties(null, Duration.ZERO, Duration.ofSeconds(-1));

    // 2. Act
    var violations = validate(properties);

    // 3. Assert
    assertThat(violations)
        .extracting(violation -> violation.getPropertyPath().toString())
        .containsExactlyInAnyOrder("codeTtl", "idempotencyTtl", "processingLeaseTtl");
  }

  private static java.util.Set<jakarta.validation.ConstraintViolation<EmailVerificationProperties>>
      validate(EmailVerificationProperties properties) {
    try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
      return validatorFactory.getValidator().validate(properties);
    }
  }
}
