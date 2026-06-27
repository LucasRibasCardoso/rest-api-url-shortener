package com.app.url_shortener.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

@Tag("unit")
@DisplayName("Testes de Unidade - JwtConfig")
class JwtConfigTest {

  private final JwtConfig jwtConfig = new JwtConfig();

  @Nested
  @DisplayName("JwtDecoder")
  class JwtDecoderTests {

    @Test
    @DisplayName("Deve criar decoder quando secret tiver pelo menos 256 bits")
    void shouldCreateDecoderWhenSecretHasAtLeast256Bits() {
      // 1. Arrange
      var properties = jwtProperties("12345678901234567890123456789012");

      // 2. Act
      var decoder = jwtConfig.jwtDecoder(properties);

      // 3. Assert
      assertThat(decoder).isNotNull().isInstanceOf(JwtDecoder.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    @DisplayName("Deve rejeitar secret nulo, vazio ou em branco")
    void shouldRejectNullEmptyOrBlankSecret(String secret) {
      // 1. Arrange
      var properties = jwtProperties(secret);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> jwtConfig.jwtDecoder(properties));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("JWT secret must not be blank.");
    }
  }

  @Nested
  @DisplayName("JwtEncoder")
  class JwtEncoderTests {

    @Test
    @DisplayName("Deve criar encoder quando secret tiver pelo menos 256 bits")
    void shouldCreateEncoderWhenSecretHasAtLeast256Bits() {
      // 1. Arrange
      var properties = jwtProperties("abcdefghijklmnopqrstuvwxyz123456");

      // 2. Act
      var encoder = jwtConfig.jwtEncoder(properties);

      // 3. Assert
      assertThat(encoder).isNotNull().isInstanceOf(JwtEncoder.class);
    }

    @Test
    @DisplayName("Deve rejeitar secret com menos de 256 bits")
    void shouldRejectSecretShorterThan256Bits() {
      // 1. Arrange
      var properties = jwtProperties("short-secret");

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> jwtConfig.jwtEncoder(properties));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("JWT secret must have at least 256 bits.");
    }
  }

  private static JwtProperties jwtProperties(String secret) {
    return new JwtProperties("app-url-shortener", secret, 900);
  }
}
