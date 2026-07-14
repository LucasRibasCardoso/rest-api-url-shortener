package com.app.url_shortener.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@Tag("unit")
@DisplayName("Testes de Unidade - Binding das propriedades OpenAPI")
class OpenApiPropertiesBindingTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfig.class)
          .withPropertyValues("app.openapi.enabled=true");

  @Test
  @DisplayName("Deve vincular enabled diretamente sob app.openapi")
  void shouldBindEnabledDirectlyUnderOpenApiPrefix() {
    // 1. Arrange

    // 2. Act
    contextRunner.run(
        context -> {
          // 3. Assert
          assertThat(context).hasNotFailed();
          assertThat(context.getBean(OpenApiProperties.class).enabled()).isTrue();
        });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(OpenApiProperties.class)
  static class TestConfig {}
}
