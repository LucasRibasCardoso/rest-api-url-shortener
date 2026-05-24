package com.app.url_shortener.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - Propriedades JWT")
class JwtPropertiesTest {

  @Nested
  @DisplayName("Representação textual segura")
  class SafeToStringTests {

    @Test
    @DisplayName("Não deve expor secret no toString")
    void shouldNotExposeSecretInToString() {
      // 1. Arrange
      var secret = "jwt-secret-with-at-least-256-bits";
      var properties = new JwtProperties("url-shortener", secret, 900L);

      // 2. Act
      var text = properties.toString();

      // 3. Assert
      assertThat(text)
          .doesNotContain(secret)
          .contains("[REDACTED]");
    }
  }
}
