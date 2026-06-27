package com.app.url_shortener.shared.idempotency.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@Tag("unit")
@DisplayName("Testes de Unidade - Binding das propriedades de idempotência")
class IdempotencyPropertiesBindingTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfig.class)
          .withPropertyValues(
              "app.idempotency.protected-uris[0]=/api/v1/auth/register",
              "app.idempotency.protected-uris[1]=/api/v1/urls");

  @Test
  @DisplayName("Deve vincular protected-uris diretamente sob app.idempotency")
  void shouldBindProtectedUrisDirectlyUnderIdempotencyPrefix() {
    // 1. Arrange

    // 2. Act
    contextRunner.run(
        context -> {
          // 3. Assert
          assertThat(context).hasNotFailed();
          assertThat(context.getBean(IdempotencyProperties.class).protectedUris())
              .containsExactly("/api/v1/auth/register", "/api/v1/urls");
        });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(IdempotencyProperties.class)
  static class TestConfig {}
}
