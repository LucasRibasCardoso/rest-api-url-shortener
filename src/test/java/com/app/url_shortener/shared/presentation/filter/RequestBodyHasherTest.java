package com.app.url_shortener.shared.presentation.filter;

import com.app.url_shortener.shared.idempotency.impl.RequestBodyHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("Testes de Unidade - RequestBodyHasher")
class RequestBodyHasherTest {

  private final RequestBodyHasher hasher = new RequestBodyHasher();

  @Nested
  @DisplayName("sha256Hex")
  class Sha256HexTests {

    @Test
    @DisplayName("Deve gerar SHA-256 para body vazio")
    void shouldGenerateSha256ForEmptyBody() {
      // 1. Arrange
      var body = new byte[0];

      // 2. Act
      var result = hasher.sha256Hex(body);

      // 3. Assert
      assertThat(result).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    @DisplayName("Deve gerar SHA-256 para body JSON")
    void shouldGenerateSha256ForJsonBody() {
      // 1. Arrange
      var body = "{\"url\":\"https://example.com\"}".getBytes(StandardCharsets.UTF_8);

      // 2. Act
      var result = hasher.sha256Hex(body);

      // 3. Assert
      assertThat(result).isEqualTo("5dc5c505a79bfc2eb22d0e45eff415c6ecf0c965c3d53d6e3e02c1bda74b0927");
    }
  }
}
