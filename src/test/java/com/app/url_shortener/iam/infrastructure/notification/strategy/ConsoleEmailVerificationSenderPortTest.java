package com.app.url_shortener.iam.infrastructure.notification.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

@Tag("unit")
@DisplayName("Testes de Unidade - Estratégia de email em console")
class ConsoleEmailVerificationSenderPortTest {

  @Nested
  @DisplayName("Profiles permitidos")
  class ProfileTests {

    @Test
    @DisplayName("Deve estar habilitada somente para dev, local e test")
    void shouldBeEnabledOnlyForDevLocalAndTestProfiles() {
      // 1. Arrange
      var profile = ConsoleEmailVerificationSenderStrategy.class.getAnnotation(Profile.class);

      // 2. Act
      var profiles = profile.value();

      // 3. Assert
      assertThat(profiles).containsExactlyInAnyOrder("dev", "local", "test");
    }
  }
}
