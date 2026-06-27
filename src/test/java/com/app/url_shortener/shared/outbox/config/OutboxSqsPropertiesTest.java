package com.app.url_shortener.shared.outbox.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - Propriedades SQS do Outbox")
class OutboxSqsPropertiesTest {

  @Nested
  @DisplayName("Validação")
  class ValidationTests {

    @Test
    @DisplayName("Deve aceitar mapa de filas válido")
    void shouldAcceptValidQueueMap() {
      // 1. Arrange
      var properties =
          new OutboxSqsProperties(
              Map.of("EMAIL_VERIFICATION_REQUESTED", "email-verification-events-queue"));

      // 2. Act
      var violations = validate(properties);

      // 3. Assert
      assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Deve rejeitar mapa de filas nulo ou vazio")
    void shouldRejectNullOrEmptyQueueMap() {
      // 1. Arrange
      var nullQueues = new OutboxSqsProperties(null);
      var emptyQueues = new OutboxSqsProperties(Map.of());

      // 2. Act
      var nullViolations = validate(nullQueues);
      var emptyViolations = validate(emptyQueues);

      // 3. Assert
      assertThat(nullViolations)
          .extracting(violation -> violation.getPropertyPath().toString())
          .containsExactly("queues");
      assertThat(emptyViolations)
          .extracting(violation -> violation.getPropertyPath().toString())
          .containsExactly("queues");
    }

    @Test
    @DisplayName("Deve rejeitar tipo de evento ou fila em branco")
    void shouldRejectBlankEventTypeOrQueueName() {
      // 1. Arrange
      var properties = new OutboxSqsProperties(Map.of(" ", " "));

      // 2. Act
      var violations = validate(properties);

      // 3. Assert
      assertThat(violations).hasSize(2);
    }
  }

  private static java.util.Set<jakarta.validation.ConstraintViolation<OutboxSqsProperties>>
      validate(OutboxSqsProperties properties) {
    try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
      return validatorFactory.getValidator().validate(properties);
    }
  }
}
