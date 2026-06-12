package com.app.url_shortener.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - Propriedades compartilhadas")
class SharedConfigurationPropertiesTest {

  @Nested
  @DisplayName("Validação")
  class ValidationTests {

    @Test
    @DisplayName("Deve rejeitar propriedades obrigatórias ausentes")
    void shouldRejectMissingRequiredProperties() {
      // 1. Arrange
      var application = new ApplicationProperties(" ");
      var springApplication = new SpringApplicationProperties(null);
      var dynamoDb = new DynamoDbProperties(null, " ", null, " ", null);
      var sqs = new AwsSqsProperties(null, " ", null, " ");

      // 2. Act
      var applicationViolations = validate(application);
      var springApplicationViolations = validate(springApplication);
      var dynamoDbViolations = validate(dynamoDb);
      var sqsViolations = validate(sqs);

      // 3. Assert
      assertThat(applicationViolations).hasSize(1);
      assertThat(springApplicationViolations).hasSize(1);
      assertThat(dynamoDbViolations).hasSize(5);
      assertThat(sqsViolations).hasSize(4);
    }
  }

  @Nested
  @DisplayName("Representação textual segura")
  class SafeToStringTests {

    @Test
    @DisplayName("Não deve expor credenciais do DynamoDB")
    void shouldNotExposeDynamoDbCredentials() {
      // 1. Arrange
      var properties =
          new DynamoDbProperties(
              "http://localhost:4566",
              "us-east-1",
              "access-key-value",
              "secret-key-value",
              new DynamoDbProperties.Tables("url", "url-counter"));

      // 2. Act
      var text = properties.toString();

      // 3. Assert
      assertThat(text)
          .doesNotContain("access-key-value", "secret-key-value")
          .contains("[REDACTED]");
    }
  }

  private static <T> java.util.Set<jakarta.validation.ConstraintViolation<T>> validate(T value) {
    try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
      return validatorFactory.getValidator().validate(value);
    }
  }
}
