package com.app.url_shortener.url.application.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.url_shortener.url.domain.exception.UnsafeUrlException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
@DisplayName("Testes de Unidade - Política de Segurança de URL")
class UrlSafetyValidatorTest {

  private UrlSafetyValidator validator;

  @BeforeEach
  void setUp() {
    validator = new UrlSafetyValidator();
  }

  @Nested
  @DisplayName("Validação")
  class ValidationTests {

    @ParameterizedTest
    @ValueSource(
        strings = {
          "https://example.com",
          "http://example.com/path?q=1",
          "https://sub.example.com",
          "https://8.8.8.8",
          "https://[2001:4860:4860::8888]"
        })
    @DisplayName("Deve permitir URLs com hosts públicos")
    void shouldAllowUrlsWithPublicHosts(String originalUrl) {
      // 1. Arrange

      // 2. Act
      var throwableAssert = assertThatCode(() -> validator.validate(originalUrl));

      // 3. Assert
      throwableAssert.doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "https://localhost",
          "https://sub.localhost",
          "https://localhost.",
          "https://localhost..",
          "https://intranet",
          "https://admin",
          "https://service",
          "https://example",
          "https://printer.local",
          "https://api.internal",
          "https://api.internal..",
          "https://router.lan",
          "https://server.home",
          "https://portal.corp",
          "https://xn--example.com",
          "https://www.xn--example.com",
          "https://exemplo.corp.",
          "https://exâmple.com",
          "https://10.0.0.1",
          "https://172.16.0.1",
          "https://172.31.255.255",
          "https://192.168.1.1",
          "https://127.0.0.1",
          "https://169.254.1.1",
          "https://169.254.169.254",
          "https://0.0.0.0",
          "https://010.000.000.001",
          "https://008.008.008.008",
          "https://224.0.0.1",
          "https://[::1]",
          "https://[fe80::1]",
          "https://[::]",
          "https://[ff00::1]",
          "https://[fc00::1]",
          "https://[fd12:3456:789a::1]"
        })
    @DisplayName("Deve bloquear URLs com hosts não permitidos")
    void shouldBlockUrlsWithDisallowedHosts(String originalUrl) {
      // 1. Arrange

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> validator.validate(originalUrl));

      // 3. Assert
      throwableAssert.isInstanceOf(UnsafeUrlException.class);
    }
  }
}
