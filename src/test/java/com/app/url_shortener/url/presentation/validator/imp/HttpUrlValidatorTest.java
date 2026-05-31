package com.app.url_shortener.url.presentation.validator.imp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("Testes de Unidade - Validador de URL HTTP")
class HttpUrlValidatorTest {

  private HttpUrlValidator validator;

  @BeforeEach
  void setUp() {
    validator = new HttpUrlValidator();
  }

  @Nested
  @DisplayName("Validação")
  class ValidationTests {

    @ParameterizedTest
    @ValueSource(strings = {
        "http://google.com",
        "https://google.com",
        "https://www.google.com/search?q=url-shortener",
        "HTTP://example.com",
        "HTTPS://example.com/path",
        "  https://google.com  ",
        "https://example.com:8080",
        "https://example.com:1",
        "https://example.com:65535",
        "https://[2001:db8::1]",
        "https://[2001:db8::1]:8080"
    })
    @DisplayName("Deve retornar verdadeiro para URLs HTTP e HTTPS válidas")
    void shouldReturnTrueForValidHttpAndHttpsUrls(String value) {
      // 1. Arrange

      // 2. Act
      var result = validator.isValid(value, null);

      // 3. Assert
      assertThat(result).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    @DisplayName("Deve retornar verdadeiro para valores nulos, vazios ou em branco")
    void shouldReturnTrueForNullEmptyOrBlankValues(String value) {
      // 1. Arrange

      // 2. Act
      var result = validator.isValid(value, null);

      // 3. Assert
      assertThat(result).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "not a uri with spaces",
        "http://",
        "https:///path-only",
        "/relative/path",
        "google.com",
        "ftp://google.com",
        "file:///tmp/file.txt",
        "mailto:user@example.com",
        "https://user@example.com",
        "https://user:pass@example.com",
        "https://google.com@evil.com",
        "https://example.com:0",
        "https://example.com:65536",
        "https://example.com:",
        "https://example.com:abc"
    })
    @DisplayName("Deve retornar falso para URIs inválidas, sem host, com userinfo, porta ou esquema inválido")
    void shouldReturnFalseForInvalidUrisMissingHostUserInfoInvalidPortOrUnsupportedSchemes(String value) {
      // 1. Arrange

      // 2. Act
      var result = validator.isValid(value, null);

      // 3. Assert
      assertThat(result).isFalse();
    }
  }
}
