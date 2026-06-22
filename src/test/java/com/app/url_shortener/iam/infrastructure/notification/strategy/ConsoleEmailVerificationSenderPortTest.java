package com.app.url_shortener.iam.infrastructure.notification.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.springframework.context.annotation.Profile;

@Tag("unit")
@DisplayName("Testes de Unidade - Estratégia de email em console")
class ConsoleEmailVerificationSenderPortTest {

  @Nested
  @DisplayName("Profiles permitidos")
  class ProfileTests {

    @Test
    @DisplayName("Deve estar habilitada somente para o profile de teste")
    void shouldBeEnabledOnlyForTestProfile() {
      // 1. Arrange
      var profile = ConsoleEmailVerificationSenderStrategy.class.getAnnotation(Profile.class);

      // 2. Act
      var profiles = profile.value();

      // 3. Assert
      assertThat(profiles).containsExactly("test");
    }

    @Test
    @DisplayName("Deve exigir a seleção da strategy de console")
    void shouldRequireConsoleSenderSelection() {
      // 1. Arrange
      var condition =
          ConsoleEmailVerificationSenderStrategy.class.getAnnotation(ConditionalOnProperty.class);

      // 2. Act
      var propertyName = condition.name();

      // 3. Assert
      assertThat(propertyName).containsExactly("app.iam.email-verification.sender");
      assertThat(condition.havingValue()).isEqualTo("console");
    }
  }
}
