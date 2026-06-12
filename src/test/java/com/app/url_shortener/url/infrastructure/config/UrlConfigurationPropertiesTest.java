package com.app.url_shortener.url.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - Propriedades de infraestrutura URL")
class UrlConfigurationPropertiesTest {

  @Test
  @DisplayName("Deve rejeitar propriedades URL inválidas")
  void shouldRejectInvalidUrlProperties() {
    // 1. Arrange
    var hashids = new HashidsProperties(" ", 0);
    var idGenerator = new IdGeneratorProperties(0, " ");
    var redirectCache =
        new RedirectCacheProperties(
            new RedirectCacheProperties.Ttl(null, Duration.ZERO, Duration.ofSeconds(-1)));

    // 2. Act
    var hashidsViolations = validate(hashids);
    var idGeneratorViolations = validate(idGenerator);
    var redirectCacheViolations = validate(redirectCache);

    // 3. Assert
    assertThat(hashidsViolations).hasSize(2);
    assertThat(idGeneratorViolations).hasSize(2);
    assertThat(redirectCacheViolations).hasSize(3);
  }

  @Test
  @DisplayName("Não deve expor salt do Hashids")
  void shouldNotExposeHashidsSalt() {
    // 1. Arrange
    var properties = new HashidsProperties("sensitive-salt", 7);

    // 2. Act
    var text = properties.toString();

    // 3. Assert
    assertThat(text).doesNotContain("sensitive-salt").contains("[REDACTED]");
  }

  private static <T> java.util.Set<jakarta.validation.ConstraintViolation<T>> validate(T value) {
    try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
      return validatorFactory.getValidator().validate(value);
    }
  }
}
