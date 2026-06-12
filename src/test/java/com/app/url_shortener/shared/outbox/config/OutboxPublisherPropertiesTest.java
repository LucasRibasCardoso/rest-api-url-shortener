package com.app.url_shortener.shared.outbox.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - Propriedades do Publisher Outbox")
class OutboxPublisherPropertiesTest {

  @Nested
  @DisplayName("Validação")
  class ValidationTests {

    @Test
    @DisplayName("Deve aceitar configuração válida")
    void shouldAcceptValidConfiguration() {
      // 1. Arrange
      var properties =
          new OutboxPublisherProperties(
              true, 20, Duration.ofSeconds(5), Duration.ofSeconds(5), 5, Duration.ofSeconds(30));

      // 2. Act
      var violations = validate(properties);

      // 3. Assert
      assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Deve rejeitar configuração inválida")
    void shouldRejectInvalidConfiguration() {
      // 1. Arrange
      var properties =
          new OutboxPublisherProperties(true, 0, null, Duration.ZERO, -1, Duration.ofSeconds(-1));

      // 2. Act
      var violations = validate(properties);

      // 3. Assert
      assertThat(violations)
          .extracting(violation -> violation.getPropertyPath().toString())
          .containsExactlyInAnyOrder(
              "batchSize", "fixedDelay", "initialDelay", "maxAttempts", "retryDelay");
    }
  }

  private static java.util.Set<jakarta.validation.ConstraintViolation<OutboxPublisherProperties>>
      validate(OutboxPublisherProperties properties) {
    try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
      return validatorFactory.getValidator().validate(properties);
    }
  }
}
