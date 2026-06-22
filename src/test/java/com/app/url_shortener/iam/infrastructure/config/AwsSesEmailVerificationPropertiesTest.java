package com.app.url_shortener.iam.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - Propriedades AWS SES")
class AwsSesEmailVerificationPropertiesTest {

  @Nested
  @DisplayName("Validação")
  class ValidationTests {

    @Test
    @DisplayName("Deve aceitar configuração válida sem endpoint customizado")
    void shouldAcceptValidConfigurationWithoutCustomEndpoint() {
      // 1. Arrange
      var properties =
          new AwsSesEmailVerificationProperties(
              null,
              "no-reply@example.com",
              "Confirme seu e-mail",
              Duration.ofSeconds(10));

      // 2. Act
      var violations = validate(properties);

      // 3. Assert
      assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Deve rejeitar remetente, assunto e timeout inválidos")
    void shouldRejectInvalidSenderSubjectAndTimeout() {
      // 1. Arrange
      var properties =
          new AwsSesEmailVerificationProperties(
              "http://localhost:4566", "invalid-email", " ", Duration.ZERO);

      // 2. Act
      var violations = validate(properties);

      // 3. Assert
      assertThat(violations)
          .extracting(violation -> violation.getPropertyPath().toString())
          .containsExactlyInAnyOrder("fromEmail", "subject", "apiCallTimeout");
    }
  }

  private static java.util.Set<
          jakarta.validation.ConstraintViolation<AwsSesEmailVerificationProperties>>
      validate(AwsSesEmailVerificationProperties properties) {
    try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
      return validatorFactory.getValidator().validate(properties);
    }
  }
}
