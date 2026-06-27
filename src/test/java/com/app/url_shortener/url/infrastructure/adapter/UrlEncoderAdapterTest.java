package com.app.url_shortener.url.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.url_shortener.url.infrastructure.config.HashidsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - Adaptador de Codificação de URL")
class UrlEncoderAdapterTest {

  private static final String SALT = "test-salt";
  private static final int MIN_LENGTH = 5;

  private UrlEncoderAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new UrlEncoderAdapter(new HashidsProperties(SALT, MIN_LENGTH));
    adapter.initialize();
  }

  @Nested
  @DisplayName("Codificação")
  class EncodeTests {

    @Test
    @DisplayName("Deve retornar hashid previsível para ID válido")
    void shouldReturnPredictableHashidForValidId() {
      // 1. Arrange
      var id = 100L;

      // 2. Act
      var result = adapter.encode(id);

      // 3. Assert
      assertThat(result).isEqualTo("VmAaV");
    }

    @Test
    @DisplayName("Deve retornar hashids diferentes para IDs diferentes")
    void shouldReturnDifferentHashidsForDifferentIds() {
      // 1. Arrange
      var firstId = 100L;
      var secondId = 101L;

      // 2. Act
      var firstHashid = adapter.encode(firstId);
      var secondHashid = adapter.encode(secondId);

      // 3. Assert
      assertThat(firstHashid).isEqualTo("VmAaV");
      assertThat(secondHashid).isEqualTo("O6BeO");
      assertThat(firstHashid).isNotEqualTo(secondHashid);
    }

    @Test
    @DisplayName("Deve respeitar o tamanho mínimo configurado")
    void shouldRespectConfiguredMinimumLength() {
      // 1. Arrange
      var id = 1L;

      // 2. Act
      var result = adapter.encode(id);

      // 3. Assert
      assertThat(result).isEqualTo("5O6zO").hasSizeGreaterThanOrEqualTo(MIN_LENGTH);
    }
  }
}
